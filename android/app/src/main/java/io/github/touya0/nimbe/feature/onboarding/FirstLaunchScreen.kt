package io.github.touya0.nimbe.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantAccount
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.data.profiles.ProfileKind
import io.github.touya0.nimbe.data.profiles.ProfileRules
import io.github.touya0.nimbe.data.profiles.ProfilesState
import io.github.touya0.nimbe.data.profiles.ProfilesStore
import io.github.touya0.nimbe.feature.homeassistant.HomeAssistantColor
import io.github.touya0.nimbe.feature.homeassistant.HomeAssistantConnectScreen
import io.github.touya0.nimbe.feature.profiles.ProfilesManager
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Framboise de l'initiale « P » du Raspberry Pi. */
private val PiColor = Color(0xFFC51A4A)

/** Les étapes du premier lancement, dans l'ordre du bandeau. */
enum class SetupStep(val label: String, val summary: String) {
    Server("Serveur", "Trouver ton Raspberry Pi"),
    HomeAssistant("Home Assistant", "Connecter Home Assistant pour surveiller le serveur"),
    Jellyfin("Jellyfin", "Relier Jellyfin pour les films et les séries"),
    Profiles("Profils", "Créer les profils de la maison"),
    Apps("Applis", "Choisir tes applis et vérifier que tout est en place"),
}

/**
 * Ce que l'assistant ajoute à un écran de connexion (Home Assistant, Jellyfin) : le bandeau des étapes,
 * Retour vers l'étape précédente et « Passer ».
 */
class SetupChrome(val stepBar: @Composable () -> Unit, val onBack: () -> Unit, val onSkip: () -> Unit)

/** Les étapes déjà construites ; après la dernière, le premier lancement se termine. */
private val BuiltSteps = listOf(SetupStep.Server, SetupStep.HomeAssistant, SetupStep.Jellyfin, SetupStep.Profiles)

/** Enveloppe pour distinguer « pas encore lu » (null) de « pas relié ». */
private class Loaded<T>(val value: T?)

/**
 * Premier lancement : Nimbe s'ouvre ici tant qu'il n'est pas terminé, même si des services sont déjà reliés
 * (leur étape propose alors de continuer ou de relier à nouveau). Chaque étape peut être passée, sauf les profils :
 * Nimbe doit marcher Pi coupé, mais il lui faut quelqu'un devant la TV.
 * [startAt] : Profils quand l'assistant est fini mais qu'il n'y a plus aucun profil.
 */
@Composable
fun FirstLaunchScreen(
    jellyfin: JellyfinAccount,
    homeAssistant: HomeAssistantAccount,
    profiles: ProfilesStore,
    profilesState: ProfilesState,
    onFinish: () -> Unit,
    startAt: SetupStep = SetupStep.Server,
) {
    var step by rememberSaveable { mutableStateOf(startAt) }
    val scope = rememberCoroutineScope()
    // Gardé d'une étape à l'autre : en revenant au serveur, on retrouve le résultat sans tout rechercher.
    val server = remember { ServerStepController(PiScanner(jellyfin, homeAssistant), scope) }
    LaunchedEffect(Unit) { server.scan() }
    val scan = (server.step as? ServerStep.Result)?.scan
    val jellyfinLink by remember(jellyfin) { jellyfin.link.map { Loaded(it) } }.collectAsStateWithLifecycle(initialValue = null)
    val homeAssistantLink by remember(homeAssistant) { homeAssistant.link.map { Loaded(it) } }
        .collectAsStateWithLifecycle(initialValue = null)
    // « Relier à nouveau » sur un service déjà relié ; oublié en changeant d'étape.
    var relink by remember(step) { mutableStateOf(false) }

    fun next() {
        val index = BuiltSteps.indexOf(step)
        if (index == BuiltSteps.lastIndex) onFinish() else step = BuiltSteps[index + 1]
    }
    fun back() {
        BuiltSteps.getOrNull(BuiltSteps.indexOf(step) - 1)?.let { step = it }
    }
    // Retour depuis « Relier à nouveau » ramène au résumé du service, pas à l'étape d'avant.
    val chrome = SetupChrome(stepBar = { StepBar(step) }, onBack = { if (relink) relink = false else back() }, onSkip = ::next)

    when (step) {
        SetupStep.Server -> SetupFrame(step) { ServerStepContent(server, onContinue = ::next) }
        SetupStep.HomeAssistant -> {
            val link = homeAssistantLink ?: return
            val linked = link.value
            if (linked != null && !relink) {
                SetupFrame(step) {
                    LinkedStepContent(
                        title = "Home Assistant",
                        intro = "Il surveille le Pi et ses services : voyant de l'en-tête, écran « État du serveur » et écran de veille.",
                        onContinue = ::next,
                        onRelink = { relink = true },
                        onBack = ::back,
                    ) {
                        ServiceRow(
                            "H", HomeAssistantColor, "Home Assistant",
                            spot = scan?.homeAssistant as? ServiceSpot.Linked ?: ServiceSpot.Linked(linked.serverUrl, reachable = true),
                            detail = "Compte « ${linked.userName} » · ${sensorCount(linked.sensors.size)}",
                        )
                    }
                }
            } else {
                HomeAssistantConnectScreen(
                    homeAssistant,
                    fallbackAddress = scan?.homeAssistant?.url ?: scan?.piHost?.let(PiScanner::homeAssistantUrl).orEmpty(),
                    onClose = ::next,
                    setup = chrome,
                )
            }
        }
        SetupStep.Jellyfin -> {
            val link = jellyfinLink ?: return
            val linked = link.value
            if (linked != null && !relink) {
                SetupFrame(step) {
                    LinkedStepContent(
                        title = "Jellyfin",
                        intro = "Un seul compte pour toute la maison : films, séries et reprise de lecture sont partagés par les profils.",
                        onContinue = ::next,
                        onRelink = { relink = true },
                        onBack = ::back,
                    ) {
                        ServiceRow(
                            "J", JellyfinColor, "Jellyfin",
                            spot = scan?.jellyfin as? ServiceSpot.Linked ?: ServiceSpot.Linked(linked.serverUrl, reachable = true),
                            detail = "Compte « ${linked.userName} » sur ${linked.serverName}",
                        )
                    }
                }
            } else {
                JellyfinConnectScreen(
                    jellyfin,
                    currentUrl = linked?.serverUrl,
                    onClose = ::next,
                    knownUrl = (scan?.jellyfin as? ServiceSpot.Found)?.url,
                    setup = chrome,
                )
            }
        }
        SetupStep.Profiles -> SetupFrame(step) {
            ProfilesStepContent(profiles, profilesState, onContinue = ::next, onBack = ::back)
        }
        SetupStep.Apps -> Unit
    }
}

@Composable
private fun ProfilesStepContent(store: ProfilesStore, state: ProfilesState, onContinue: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.width(340.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Profils", style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp))
            Text(
                "Chaque membre de la maison a son profil, avec son accueil et ses réglages. Jellyfin et " +
                    "Home Assistant restent communs à tout le monde.",
                style = MaterialTheme.typography.bodyLarge,
                color = LuneAttenuee,
            )
            ProfileKind.entries.forEach { Instruction(it.ordinal + 1, "${it.label} : ${it.summary.replaceFirstChar(Char::lowercase)}") }
        }
        SetupPanel {
            Text(
                if (state.profiles.isEmpty()) "Aucun profil pour l'instant" else "Profils de cette TV",
                style = MaterialTheme.typography.labelMedium,
                color = LuneAttenuee,
            )
            ProfilesManager(
                profiles = state.profiles,
                onSave = { scope.launch { store.save(it) } },
                onDelete = { scope.launch { store.delete(it.id) } },
                focusOnShow = true,
            ) {
                Spacer(Modifier.height(4.dp))
                if (!ProfileRules.hasAdult(state.profiles)) {
                    Text(
                        "Crée au moins un profil adulte pour continuer : c'est lui qui règle la TV.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LuneAttenuee,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (ProfileRules.hasAdult(state.profiles)) PillButton("Continuer", onClick = onContinue)
                    PillButton("Retour", onClick = onBack)
                }
            }
        }
    }
}

private fun sensorCount(count: Int): String = when (count) {
    0 -> "aucun capteur suivi"
    1 -> "1 capteur suivi"
    else -> "$count capteurs suivis"
}

/** Cadre commun des étapes dessinées ici : bandeau en haut, contenu en dessous. */
@Composable
private fun SetupFrame(step: SetupStep, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Nuit).padding(horizontal = 56.dp, vertical = 32.dp)) {
        StepBar(step)
        Spacer(Modifier.height(28.dp))
        content()
    }
}

/** Grand panneau de droite, comme sur la maquette. */
@Composable
private fun RowScope.SetupPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .background(Panneau, RoundedCornerShape(20.dp))
            .border(1.dp, PanneauClair, RoundedCornerShape(20.dp))
            // Le formulaire d'un profil peut dépasser : le focus fait défiler jusqu'à l'élément choisi.
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Étape d'un service déjà relié : on continue sans rien refaire, ou on le relie à nouveau. */
@Composable
private fun LinkedStepContent(
    title: String,
    intro: String,
    onContinue: () -> Unit,
    onRelink: () -> Unit,
    onBack: () -> Unit,
    row: @Composable () -> Unit,
) {
    BackHandler(onBack = onBack)
    val focus = rememberStepFocus()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.width(340.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp))
            Text(intro, style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
        }
        SetupPanel {
            Text("● Déjà relié à cette TV", style = MaterialTheme.typography.labelMedium, color = EnLigne)
            row()
            Text(
                "Rien à refaire. Pour changer de compte ou de serveur, relie-le à nouveau.",
                style = MaterialTheme.typography.bodyMedium,
                color = LuneAttenuee,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Continuer", onClick = onContinue, modifier = Modifier.focusRequester(focus))
                PillButton("Relier à nouveau", onClick = onRelink)
                PillButton("Retour", onClick = onBack)
            }
        }
    }
}

/** Bandeau du haut : étapes faites cochées, étape en cours en clair, les suivantes atténuées. */
@Composable
private fun StepBar(current: SetupStep) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        SetupStep.entries.forEach { step ->
            val done = step.ordinal < current.ordinal
            val active = step == current
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).background(
                        when {
                            done -> EnLigne.copy(alpha = 0.2f)
                            active -> Focus
                            else -> PanneauClair
                        },
                        RoundedCornerShape(8.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (done) "✓" else "${step.ordinal + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            done -> EnLigne
                            active -> Nuit
                            else -> LuneAttenuee
                        },
                    )
                }
                Text(step.label, style = MaterialTheme.typography.titleSmall, color = if (active) Lune else LuneAttenuee)
            }
        }
    }
}

@Composable
private fun ServerStepContent(controller: ServerStepController, onContinue: () -> Unit) {
    // Retour ramène de la saisie au résultat ; ailleurs il ne fait rien : Nimbe est l'écran d'accueil du stick.
    BackHandler(enabled = controller.step is ServerStep.Manual, onBack = controller::leaveAddress)

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.width(340.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Bienvenue", style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp))
            Text(
                "Nimbe relie ta TV à ton Raspberry Pi. Ça prend environ cinq minutes, et presque tout se fait " +
                    "depuis ton téléphone.",
                style = MaterialTheme.typography.bodyLarge,
                color = LuneAttenuee,
            )
            Spacer(Modifier.height(8.dp))
            SetupStep.entries.forEach { Instruction(it.ordinal + 1, it.summary) }
        }

        SetupPanel {
            when (val step = controller.step) {
                ServerStep.Scanning -> ScanningContent(onManual = controller::enterAddress)
                is ServerStep.Result -> ResultContent(
                    step.scan,
                    onContinue = onContinue,
                    onManual = controller::enterAddress,
                    onRescan = controller::scan,
                )
                is ServerStep.Manual -> ManualContent(step, controller)
            }
        }
    }
}

@Composable
private fun ScanningContent(onManual: () -> Unit) {
    val focus = rememberStepFocus()
    PanelTitle("Recherche sur ton réseau", "Nimbe cherche Jellyfin et Home Assistant sur ton Raspberry Pi.")
    StatusLine("Recherche en cours…", spinning = true)
    PillButton("Saisir l'adresse du Pi", onClick = onManual, modifier = Modifier.focusRequester(focus))
}

@Composable
private fun ResultContent(scan: PiScan, onContinue: () -> Unit, onManual: () -> Unit, onRescan: () -> Unit) {
    val focus = rememberStepFocus()
    if (scan.foundAnything) {
        Text("● Trouvé sur ton réseau", style = MaterialTheme.typography.labelMedium, color = EnLigne)
    } else {
        Text("Rien trouvé sur ton réseau", style = MaterialTheme.typography.labelMedium, color = Focus)
    }
    SpotRow(
        letter = "P",
        color = PiColor,
        title = "Raspberry Pi",
        detail = scan.piHost ?: "Adresse inconnue",
        status = if (scan.piHost != null) "Trouvé" else "Introuvable",
        statusColor = if (scan.piHost != null) EnLigne else LuneAttenuee,
    )
    ServiceRow("J", JellyfinColor, "Jellyfin", scan.jellyfin)
    ServiceRow("H", HomeAssistantColor, "Home Assistant", scan.homeAssistant)
    if (!scan.foundAnything) {
        Text(
            "Vérifie que le Pi est allumé et sur le même réseau que la TV. Tu peux aussi continuer sans lui : " +
                "chaque service se relie plus tard, dans Réglages → Serveurs et comptes.",
            style = MaterialTheme.typography.bodyMedium,
            color = LuneAttenuee,
        )
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Continuer", onClick = onContinue, modifier = Modifier.focusRequester(focus))
        PillButton("Autre adresse", onClick = onManual)
        PillButton("Rechercher", onClick = onRescan)
    }
}

@Composable
private fun ManualContent(step: ServerStep.Manual, controller: ServerStepController) {
    val focus = rememberStepFocus()
    var text by rememberSaveable { mutableStateOf(step.previous?.piHost.orEmpty()) }
    PanelTitle(
        "Adresse du Raspberry Pi",
        "Son adresse IP ou son nom sur ton réseau. Ajoute le port de Jellyfin s'il n'utilise pas 8096.",
    )
    AddressField(
        value = text,
        onValueChange = { text = it },
        onSubmit = { controller.tryAddress(text) },
        modifier = Modifier.focusRequester(focus),
        placeholder = "192.168.1.20",
    )
    when {
        controller.checking -> StatusLine("Recherche à cette adresse…", spinning = true)
        controller.addressError != null -> Text(controller.addressError.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Alerte)
        else -> Text(
            "Par exemple 192.168.1.20, pi5.lan ou 192.168.1.20:8097.",
            style = MaterialTheme.typography.bodyMedium,
            color = LuneAttenuee,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Chercher", onClick = { controller.tryAddress(text) })
        PillButton("Retour", onClick = controller::leaveAddress)
    }
}

@Composable
private fun PanelTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
    }
}

@Composable
private fun ServiceRow(letter: String, color: Color, title: String, spot: ServiceSpot, detail: String? = null) {
    val address = spot.url?.removePrefix("http://")?.removePrefix("https://")
    when (spot) {
        is ServiceSpot.Linked -> SpotRow(
            letter, color, title,
            detail = "$address · ${detail ?: "déjà relié à cette TV"}",
            status = if (spot.reachable) "Connecté" else "Ne répond pas",
            statusColor = if (spot.reachable) EnLigne else Alerte,
        )
        is ServiceSpot.Found -> SpotRow(letter, color, title, "$address · détecté automatiquement", "Trouvé", EnLigne)
        ServiceSpot.Missing -> SpotRow(letter, color, title, "Son adresse pourra être saisie à son étape", "Non trouvé", LuneAttenuee)
    }
}

/** Ligne d'information, pas focalisable : seuls les boutons du bas prennent le focus. */
@Composable
private fun SpotRow(letter: String, color: Color, title: String, detail: String, status: String, statusColor: Color) {
    Row(
        Modifier.fillMaxWidth().background(PanneauClair, RoundedCornerShape(14.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServiceInitial(letter, color)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
        }
        Text(status, style = MaterialTheme.typography.titleSmall, color = statusColor)
    }
}
