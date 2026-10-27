package io.github.touya0.nimbe.feature.homeassistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.homeassistant.HaEntity
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantAccount
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.Spinner
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
import kotlinx.coroutines.launch

/** Bleu Home Assistant, pour l'initiale du service. */
val HomeAssistantColor = Color(0xFF18BCF2)

/**
 * Écran « Connecter Home Assistant », ouvert depuis Réglages → Serveurs et comptes.
 * La page de configuration n'est ouverte sur le réseau que tant que cet écran est affiché.
 * [fallbackAddress] : adresse proposée sur la page quand aucune n'est enregistrée.
 */
@Composable
fun HomeAssistantConnectScreen(account: HomeAssistantAccount, fallbackAddress: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { HomeAssistantConnectController(account, scope, fallbackAddress) }
    DisposableEffect(Unit) {
        controller.start(PairingServer.localAddress(context))
        onDispose { controller.stop() }
    }
    BackHandler(onBack = onClose)

    val back = remember { FocusRequester() }
    val confirm = remember { FocusRequester() }
    val canSave = (controller.sensors as? SensorsState.Loaded)?.selected?.isNotEmpty() == true
    LaunchedEffect(Unit) { runCatching { back.requestFocus() } }
    // Dès que les capteurs arrivent, « Continuer » prend le focus, comme sur la maquette.
    LaunchedEffect(controller.sensors is SensorsState.Loaded) {
        if (controller.sensors is SensorsState.Loaded) runCatching { confirm.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Nuit).padding(horizontal = 56.dp, vertical = 36.dp)) {
        Text("Connecter Home Assistant", style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp))
        Text(
            "Pour afficher l'état du Pi et des services. Utilise le token d'un compte non-admin dédié à la TV.",
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
        )
        Row(
            Modifier.weight(1f).padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Panel(Modifier.weight(1.25f)) { PairingPanel(controller) }
            Panel(Modifier.weight(1f)) { SensorsPanel(controller) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            PillButton("Retour", onClick = onClose, modifier = Modifier.focusRequester(back))
            if (canSave) {
                PillButton(
                    "Continuer",
                    onClick = { scope.launch { if (controller.save()) onClose() } },
                    modifier = Modifier.focusRequester(confirm),
                )
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .background(Panneau, RoundedCornerShape(20.dp))
            .border(1.dp, PanneauClair, RoundedCornerShape(20.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun PairingPanel(controller: HomeAssistantConnectController) {
    val endpoint = controller.endpoint ?: return
    val url = endpoint.url
    if (url == null) {
        Text(
            if (endpoint.address == null) "Pas de réseau" else "Page de configuration indisponible",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            if (endpoint.address == null) {
                "Le stick doit être sur le même réseau que ton téléphone pour recevoir le token."
            } else {
                "Une autre appli occupe les ports 8765 à 8769. Redémarre le stick, puis réessaie."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QrCode(url, size = 180.dp)
            Text("ou ouvre ${url.removePrefix("http://")}", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Instruction(1, "Scanne le QR code avec ton téléphone")
            Instruction(2, "Saisis ce code sur la page")
            Instruction(3, "Colle le token Home Assistant")
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Nuit.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .border(1.dp, PanneauClair, RoundedCornerShape(14.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    controller.code.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = 3.sp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            TokenStatus(controller)
        }
    }
}

@Composable
private fun TokenStatus(controller: HomeAssistantConnectController) {
    val received = controller.received
    val rejection = controller.lastRejection
    when {
        received != null && rejection == null -> StatusCard(
            title = "Token reçu",
            detail = if (received.user.isAdmin) {
                "Compte « ${received.user.name} » · administrateur : préfère un compte dédié sans droits admin"
            } else {
                "Compte « ${received.user.name} » · sans droits admin"
            },
            color = if (received.user.isAdmin) Focus else EnLigne,
        )
        rejection != null -> StatusCard(title = "Envoi refusé", detail = rejection, color = Alerte)
        else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Spinner()
            Text("En attente du téléphone…", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun StatusCard(title: String, detail: String, color: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("● $title", style = MaterialTheme.typography.titleSmall, color = color)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
    }
}

@Composable
private fun SensorsPanel(controller: HomeAssistantConnectController) {
    val state = controller.sensors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Capteurs à afficher", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (state is SensorsState.Loaded) {
            Text("${state.candidates.size} détectés", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
        }
    }
    when (state) {
        SensorsState.NoToken -> Text(
            "Les capteurs de ton Home Assistant apparaissent ici dès que le token est reçu.",
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
        )
        SensorsState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Spinner()
            Text("Lecture des capteurs…", style = MaterialTheme.typography.titleSmall)
        }
        is SensorsState.Failed -> {
            Text(state.message, style = MaterialTheme.typography.bodyLarge, color = Alerte)
            Text(
                "Les réglages enregistrés restent en place. Vérifie que Home Assistant tourne sur le Pi.",
                style = MaterialTheme.typography.bodyMedium,
                color = LuneAttenuee,
            )
            PillButton("Réessayer", onClick = controller::retry)
        }
        is SensorsState.Loaded -> if (state.candidates.isEmpty()) {
            Text(
                "Aucun capteur System Monitor ni Ping trouvé. Ajoute ces intégrations dans Home Assistant, " +
                    "et donne au compte de la TV l'accès à leurs entités.",
                style = MaterialTheme.typography.bodyLarge,
                color = LuneAttenuee,
            )
        } else {
            // Marge intérieure : l'anneau de focus déborde de la carte, il ne doit pas être rogné.
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.candidates.forEach { entity ->
                    SensorRow(entity, checked = entity.id in state.selected, onToggle = { controller.toggle(entity.id) })
                }
            }
        }
    }
}

@Composable
private fun SensorRow(entity: HaEntity, checked: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onToggle,
        modifier = Modifier
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
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(if (checked) EnLigne else Color.Transparent, RoundedCornerShape(5.dp))
                    .border(2.dp, if (checked) EnLigne else LuneAttenuee, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Text("✓", style = MaterialTheme.typography.titleSmall, color = Nuit)
            }
            Column(Modifier.weight(1f)) {
                Text(entity.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    entity.id,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = LuneAttenuee,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(entity.displayValue, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun Instruction(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$number", style = MaterialTheme.typography.titleSmall, color = Focus, modifier = Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
