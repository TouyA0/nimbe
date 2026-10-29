package io.github.touya0.nimbe.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.core.network.ConnectivityStatus
import io.github.touya0.nimbe.data.layout.TabKeys
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.feature.profiles.profileColor
import io.github.touya0.nimbe.feature.status.ServerLevel
import io.github.touya0.nimbe.feature.status.ServerVerdict
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.ClockStyle
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * [needsInternet] : onglet barré pendant une coupure d'internet (Films et Galerie viennent du Pi).
 * [needsJellyfin] : onglet barré quand Jellyfin ne répond plus.
 */
enum class Tab(val key: String, val label: String, val needsInternet: Boolean = false, val needsJellyfin: Boolean = false) {
    Home(TabKeys.HOME, "Accueil"),
    Tv("tv", "TV", needsInternet = true),
    Movies("movies", "Films", needsJellyfin = true),
    Series("series", "Séries", needsJellyfin = true),
    Music("music", "Musique", needsInternet = true),
    Gallery("gallery", "Galerie"),
    Apps(TabKeys.APPS, "Applis"),
    ;

    companion object {
        fun fromKey(key: String): Tab? = entries.firstOrNull { it.key == key }
    }
}

/**
 * En-tête toujours visible : onglets à gauche, horloge à droite.
 * Comme sur les autres accueils TV, poser le focus sur un onglet l'affiche.
 */
@Composable
fun Header(
    /** Onglets affichés pour le profil actif, dans son ordre. */
    tabs: List<Tab>,
    selected: Tab,
    onSelect: (Tab) -> Unit,
    requesters: Map<Tab, FocusRequester>,
    connectivity: ConnectivityStatus,
    jellyfinDown: Boolean,
    status: ServerVerdict,
    onStatus: () -> Unit,
    onSettings: () -> Unit,
    settingsRequester: FocusRequester,
    profile: Profile?,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // En remontant depuis le contenu, le focus revient sur l'onglet quitté.
        Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { tab ->
                TabPill(
                    tab = tab,
                    selected = tab == selected,
                    onSelect = { onSelect(tab) },
                    struck = (tab.needsInternet && connectivity.offline) || (tab.needsJellyfin && jellyfinDown),
                    modifier = Modifier.focusRequester(requesters.getValue(tab)),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        StatusPill(status, onStatus)
        Spacer(Modifier.width(12.dp))
        SettingsButton(onSettings, Modifier.focusRequester(settingsRequester))
        profile?.let {
            Spacer(Modifier.width(12.dp))
            ProfileButton(it, onProfile)
        }
        Spacer(Modifier.width(16.dp))
        Clock()
    }
}

@Composable
private fun TabPill(tab: Tab, selected: Boolean, onSelect: () -> Unit, struck: Boolean, modifier: Modifier) {
    val container = if (selected) Lune else Color.Transparent
    val content = if (selected) Nuit else LuneAttenuee
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onSelect,
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .focusRing(focused, NimbeFocus.PillRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = content,
            focusedContainerColor = if (selected) Lune else PanneauClair,
            focusedContentColor = if (selected) Nuit else Lune,
            pressedContainerColor = Lune,
            pressedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = tab.label,
            style = MaterialTheme.typography.titleMedium,
            textDecoration = if (struck) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Roue crantée : ouvre les réglages (OK, pas au simple passage du focus). */
@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, 20.dp, grow = false),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PanneauClair,
            contentColor = Lune,
            focusedContainerColor = Lune,
            focusedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("⚙", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Avatar du profil actif : OK ouvre « Qui regarde ? » pour changer de profil. */
@Composable
private fun ProfileButton(profile: Profile, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val color = profileColor(profile.color)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, 20.dp, grow = false),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color,
            contentColor = Lune,
            focusedContainerColor = color,
            focusedContentColor = Lune,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(profile.initial, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Voyant d'état : vert seulement quand Home Assistant confirme en direct que tout va bien,
 * gris pointillé quand on ne sait pas. OK ouvre l'écran « État du serveur ».
 */
@Composable
private fun StatusPill(status: ServerVerdict, onClick: () -> Unit) {
    val text = status.label ?: return
    val dot = when (status.level) {
        ServerLevel.NoNetwork, ServerLevel.PiUnreachable -> Alerte
        ServerLevel.ServiceDown, ServerLevel.NoInternet -> Focus
        ServerLevel.Unknown, ServerLevel.Hidden -> LuneAttenuee
        ServerLevel.Ok -> EnLigne
    }
    val tinted = status.level != ServerLevel.Ok && status.level != ServerLevel.Unknown
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.PillRadius, grow = false)
            .then(if (status.level == ServerLevel.Unknown) Modifier.dashedPill(LuneAttenuee) else Modifier),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (tinted) dot.copy(alpha = 0.22f) else PanneauClair,
            contentColor = Lune,
            focusedContainerColor = Lune,
            focusedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status.level == ServerLevel.Unknown) {
                Box(Modifier.size(8.dp).border(1.5.dp, if (focused) Nuit else dot, CircleShape))
            } else {
                Box(Modifier.size(8.dp).background(dot, CircleShape))
            }
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Bordure en pointillés de la pilule « État inconnu ». */
private fun Modifier.dashedPill(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(size.height / 2),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
    )
}

private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            // Réveil pile au changement de minute, plutôt qu'une boucle toutes les secondes.
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    // Jamais sur deux lignes, même quand l'en-tête est plein.
    Text(now.format(ClockFormat), style = ClockStyle, color = Lune, maxLines = 1, softWrap = false)
}
