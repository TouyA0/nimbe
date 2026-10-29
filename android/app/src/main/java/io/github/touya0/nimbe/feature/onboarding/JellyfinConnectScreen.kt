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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.data.jellyfin.JellyfinDiscovery
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.Spinner
import io.github.touya0.nimbe.ui.components.TextInput
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.delay

/** Violet Jellyfin de la maquette, pour l'initiale du service. */
val JellyfinColor = Color(0xFF5B3FB5)

/**
 * Écran « Connecter Jellyfin », ouvert depuis Réglages → Serveurs et comptes ou par le premier lancement.
 * Plein écran : le focus ne peut pas s'échapper vers l'écran de derrière.
 * [currentUrl] pré-remplit l'adresse quand la TV est déjà reliée à un serveur ; [knownUrl] : serveur déjà
 * trouvé, on passe directement au code. [onClose] : une fois relié, et sur Retour sauf dans l'assistant ([setup]).
 */
@Composable
fun JellyfinConnectScreen(
    account: JellyfinAccount,
    currentUrl: String?,
    onClose: () -> Unit,
    knownUrl: String? = null,
    setup: SetupChrome? = null,
) {
    val scope = rememberCoroutineScope()
    val controller = remember { JellyfinConnectController(account, JellyfinDiscovery(), scope) }
    LaunchedEffect(Unit) { if (knownUrl != null) controller.start(knownUrl) else controller.search() }
    BackHandler(onBack = setup?.onBack ?: onClose)

    Column(Modifier.fillMaxSize().background(Nuit).padding(horizontal = 56.dp, vertical = if (setup != null) 32.dp else 40.dp)) {
        setup?.let {
            it.stepBar()
            Spacer(Modifier.height(24.dp))
        }
        JellyfinConnectContent(controller, currentUrl, onClose, setup)
    }
}

@Composable
private fun JellyfinConnectContent(controller: JellyfinConnectController, currentUrl: String?, onClose: () -> Unit, setup: SetupChrome?) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.width(320.dp).padding(top = if (setup != null) 0.dp else 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Connecter Jellyfin", style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp))
            Text(
                "Films et séries de ton serveur, avec la reprise de lecture. Aucun mot de passe ne passe par la TV : " +
                    "tu autorises la connexion depuis ton téléphone.",
                style = MaterialTheme.typography.bodyLarge,
                color = LuneAttenuee,
            )
            Spacer(Modifier.height(8.dp))
            Stage(1, "Trouver le serveur", done = controller.step.pastServer)
            Stage(2, "Autoriser avec Quick Connect", done = controller.step is ConnectStep.Done)
            if (setup != null && controller.step !is ConnectStep.Done) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Retour", onClick = setup.onBack)
                    PillButton("Passer", onClick = setup.onSkip)
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .background(Panneau, RoundedCornerShape(20.dp))
                .border(1.dp, PanneauClair, RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val step = controller.step) {
                ConnectStep.Searching -> SearchingContent(onManual = controller::enterAddress)
                is ConnectStep.Found -> FoundContent(step, onChoose = controller::choose, onManual = controller::enterAddress)
                is ConnectStep.Manual -> ManualContent(step, controller, currentUrl, onSearch = controller::search)
                is ConnectStep.Code -> CodeContent(step, onChangeServer = controller::enterAddress)
                is ConnectStep.Failed -> FailedContent(
                    step,
                    onRetry = { controller.choose(step.server) },
                    onChangeServer = controller::enterAddress,
                )
                is ConnectStep.Done -> DoneContent(step, if (setup != null) "Continuer" else "Terminer", onClose)
            }
        }
    }
}

private val ConnectStep.pastServer: Boolean
    get() = this is ConnectStep.Code || this is ConnectStep.Failed || this is ConnectStep.Done

@Composable
private fun SearchingContent(onManual: () -> Unit) {
    val focus = rememberStepFocus()
    PanelHeader("Recherche sur ton réseau", "Ton serveur Jellyfin répond en général en quelques secondes.")
    StatusLine("Recherche en cours…", spinning = true)
    PillButton("Saisir l'adresse", onClick = onManual, modifier = Modifier.focusRequester(focus))
}

@Composable
private fun FoundContent(step: ConnectStep.Found, onChoose: (FoundServer) -> Unit, onManual: () -> Unit) {
    val focus = rememberStepFocus()
    Text("● Trouvé sur ton réseau", style = MaterialTheme.typography.labelMedium, color = EnLigne)
    step.servers.forEachIndexed { index, server ->
        ServerRow(
            title = server.info.name,
            detail = "${server.url.removePrefix("http://").removePrefix("https://")} · Jellyfin ${server.info.version}",
            onClick = { onChoose(server) },
            modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Saisir une autre adresse", onClick = onManual)
    }
}

@Composable
private fun ManualContent(step: ConnectStep.Manual, controller: JellyfinConnectController, currentUrl: String?, onSearch: () -> Unit) {
    val focus = rememberStepFocus()
    var text by rememberSaveable { mutableStateOf(currentUrl?.removePrefix("http://").orEmpty()) }
    PanelHeader("Adresse du serveur", step.notice ?: "L'adresse de Jellyfin sur ton réseau, avec son port.")
    AddressField(
        value = text,
        onValueChange = { text = it },
        onSubmit = { controller.tryAddress(text) },
        modifier = Modifier.focusRequester(focus),
    )
    when {
        controller.checking -> StatusLine("Vérification…", spinning = true)
        controller.addressError != null -> Text(controller.addressError.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Alerte)
        else -> Text(
            "Par exemple 192.168.1.20:8096 ou jellyfin.lan. Sans port, Nimbe essaie 8096.",
            style = MaterialTheme.typography.bodyMedium,
            color = LuneAttenuee,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Continuer", onClick = { controller.tryAddress(text) })
        PillButton("Chercher à nouveau", onClick = onSearch)
    }
}

@Composable
private fun CodeContent(step: ConnectStep.Code, onChangeServer: () -> Unit) {
    val focus = rememberStepFocus()
    PanelHeader("Jellyfin Quick Connect", "Connexion à ${step.server.info.name}", initial = true)
    // Le panneau est étroit sur un écran TV : le code au-dessus, les étapes en dessous.
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .background(Nuit.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .border(1.dp, PanneauClair, RoundedCornerShape(14.dp))
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            Text(
                step.code.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = 4.sp),
            )
        }
        Text("Expire dans ${remainingText(step.expiresAt)}", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Instruction(1, "Ouvre Jellyfin sur ton téléphone")
        Instruction(2, "Paramètres → Quick Connect")
        Instruction(3, "Saisis le code et autorise")
    }
    StatusLine(step.trouble ?: "En attente de l'autorisation sur ton téléphone…", spinning = true, alert = step.trouble != null)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Changer de serveur", onClick = onChangeServer, modifier = Modifier.focusRequester(focus))
    }
}

@Composable
private fun FailedContent(step: ConnectStep.Failed, onRetry: () -> Unit, onChangeServer: () -> Unit) {
    val focus = rememberStepFocus()
    PanelHeader("Connexion impossible", step.server.info.name, initial = true)
    Text(step.message, style = MaterialTheme.typography.bodyLarge, color = Alerte)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Réessayer", onClick = onRetry, modifier = Modifier.focusRequester(focus))
        PillButton("Changer de serveur", onClick = onChangeServer)
    }
}

@Composable
private fun DoneContent(step: ConnectStep.Done, label: String, onClose: () -> Unit) {
    val focus = rememberStepFocus()
    PanelHeader("Connecté", "Compte ${step.userName} sur ${step.serverName}", initial = true)
    Text(
        "Tes films et séries arrivent sur l'accueil. Pour retirer l'accès à cette TV : Réglages → Serveurs et comptes, " +
            "ou dans Jellyfin, Tableau de bord → Appareils.",
        style = MaterialTheme.typography.bodyMedium,
        color = LuneAttenuee,
    )
    PillButton(label, onClick = onClose, modifier = Modifier.focusRequester(focus))
}

/** Chaque étape pose le focus sur son action principale dès qu'elle s'affiche. */
@Composable
internal fun rememberStepFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    return requester
}

@Composable
private fun PanelHeader(title: String, subtitle: String, initial: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (initial) ServiceInitial()
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
        }
    }
}

/** Carré violet « J » de la maquette. */
@Composable
fun ServiceInitial(letter: String = "J", color: Color = JellyfinColor) {
    Box(Modifier.size(40.dp).background(color, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Text(letter, style = MaterialTheme.typography.titleMedium, color = Lune)
    }
}

@Composable
private fun Stage(number: Int, label: String, done: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).background(if (done) EnLigne.copy(alpha = 0.2f) else PanneauClair, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done) "✓" else "$number", style = MaterialTheme.typography.titleSmall, color = if (done) EnLigne else Focus)
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun Instruction(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$number", style = MaterialTheme.typography.titleSmall, color = Focus)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun StatusLine(text: String, spinning: Boolean, alert: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().background(PanneauClair.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spinning) Spinner(if (alert) Alerte else Focus)
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (alert) Alerte else Lune)
    }
}

@Composable
private fun ServerRow(title: String, detail: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PanneauClair,
            contentColor = Lune,
            focusedContainerColor = PanneauClair,
            focusedContentColor = Lune,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ServiceInitial()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
            }
            Text("Choisir", style = MaterialTheme.typography.titleSmall, color = EnLigne)
        }
    }
}

/** Champ d'adresse : OK ouvre le clavier d'Android, « Valider » sur le clavier lance la vérification. */
@Composable
internal fun AddressField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "192.168.1.20:8096",
) {
    TextInput(value, onValueChange, onSubmit, modifier, placeholder, KeyboardType.Uri)
}

/** « 9:42 », recalculé chaque seconde. */
@Composable
private fun remainingText(expiresAt: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = ((expiresAt - now) / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
