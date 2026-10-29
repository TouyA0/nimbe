package io.github.touya0.nimbe.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantAccount
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.feature.homeassistant.HomeAssistantColor
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair

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
 * Premier lancement : Nimbe s'ouvre ici tant qu'il n'est pas terminé, même si des services sont déjà reliés
 * (ils s'affichent alors « Connecté »). Chaque étape peut être passée : Nimbe doit marcher Pi coupé.
 */
@Composable
fun FirstLaunchScreen(jellyfin: JellyfinAccount, homeAssistant: HomeAssistantAccount, onFinish: () -> Unit) {
    val step by rememberSaveable { mutableStateOf(SetupStep.Server) }
    Column(Modifier.fillMaxSize().background(Nuit).padding(horizontal = 56.dp, vertical = 32.dp)) {
        StepBar(step)
        Spacer(Modifier.height(28.dp))
        when (step) {
            // Les étapes suivantes arrivent une à une ; d'ici là, le premier lancement s'arrête au serveur.
            SetupStep.Server -> ServerStepContent(jellyfin, homeAssistant, onContinue = onFinish)
            else -> Unit
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
private fun ServerStepContent(jellyfin: JellyfinAccount, homeAssistant: HomeAssistantAccount, onContinue: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember { ServerStepController(PiScanner(jellyfin, homeAssistant), scope) }
    LaunchedEffect(Unit) { controller.scan() }
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

        Column(
            Modifier
                .weight(1f)
                .background(Panneau, RoundedCornerShape(20.dp))
                .border(1.dp, PanneauClair, RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
private fun ServiceRow(letter: String, color: Color, title: String, spot: ServiceSpot) {
    val address = spot.url?.removePrefix("http://")?.removePrefix("https://")
    when (spot) {
        is ServiceSpot.Linked -> SpotRow(
            letter, color, title,
            detail = "$address · déjà relié à cette TV",
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
