package io.github.touya0.nimbe.feature.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.touya0.nimbe.data.jellyfin.ImageRef
import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.FilmTitleStyle
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair

/** Violet Jellyfin des badges de source. */
val JellyfinPurple = Color(0xFF5B3FB5)

private val CardRadius = 10.dp
private val CardShape = RoundedCornerShape(CardRadius)

/**
 * Image Jellyfin demandée à la taille affichée : le serveur la réduit, le stick ne décode que
 * ce qu'il montre. [width] et [height] sont en dp ; l'adresse est calculée en pixels.
 */
@Composable
fun JellyfinImage(ref: ImageRef?, width: Dp?, height: Dp?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val server = LocalJellyfin.current?.link?.serverUrl
    if (ref == null || server == null) return
    val density = LocalDensity.current
    val url = remember(ref, server, width, height) {
        with(density) { ref.url(server, maxWidth = width?.roundToPx(), maxHeight = height?.roundToPx()) }
    }
    AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = modifier)
}

/**
 * Carte focalisable commune : grandit de 8 % avec l'anneau ambre, signale le focus à l'écran
 * (pour le bandeau du haut), et reprend le focus si [restoreFocus] (retour d'une fiche).
 */
@Composable
private fun MediaSurface(
    onClick: () -> Unit,
    onFocus: () -> Unit,
    restoreFocus: Boolean,
    onRestored: () -> Unit,
    label: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (restoreFocus) {
        LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
            onRestored()
        }
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusRing(focused, CardRadius)
            .semantics { contentDescription = label },
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Panneau,
            contentColor = Lune,
            focusedContainerColor = Panneau,
            focusedContentColor = Lune,
            pressedContainerColor = Panneau,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) { content() }
}

/** Affiche 2:3 d'un film ou d'une série, sans texte dessous : le bandeau du haut donne le détail. */
@Composable
fun PosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 112.dp,
    onFocus: () -> Unit = {},
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
) {
    MediaSurface(onClick, onFocus, restoreFocus, onRestored, item.name, modifier.width(width).aspectRatio(2f / 3f)) {
        Box(Modifier.fillMaxSize()) {
            // Sans affiche, le titre en grand, comme sur la maquette.
            Text(
                item.name.uppercase(),
                style = FilmTitleStyle.copy(fontSize = 22.sp, lineHeight = 22.sp),
                textAlign = TextAlign.Center,
                maxLines = 4,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
            JellyfinImage(item.primary ?: item.seriesPrimary, width = width, height = null, modifier = Modifier.fillMaxSize())
            WatchedMarks(item)
        }
    }
}

/**
 * Vignette 16:9 : épisodes et « Continuer à regarder ». [badge] en haut à gauche (source ou numéro),
 * [label] et [detail] sous l'image.
 */
@Composable
fun WideCard(
    item: MediaItem,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    badge: String? = "JELLYFIN",
    width: Dp = 208.dp,
    onFocus: () -> Unit = {},
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MediaSurface(onClick, onFocus, restoreFocus, onRestored, label, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Box(Modifier.fillMaxSize().background(PanneauClair)) {
                // Un épisode a sa propre capture ; sinon, la vignette ou le fond de la série.
                val image = if (item.kind == ItemKind.Episode) item.primary ?: item.thumb ?: item.backdrop else item.thumb ?: item.backdrop ?: item.primary
                JellyfinImage(image, width = width, height = null, modifier = Modifier.fillMaxSize())
                badge?.let { SourceBadge(it, Modifier.align(Alignment.TopStart).padding(8.dp)) }
                WatchedMarks(item)
            }
        }
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 1) }
    }
}

/**
 * Barre de progression en bas, et coche « vu » en haut à droite. Un film déjà vu qu'on revoit
 * montre sa progression plutôt que la coche.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.WatchedMarks(item: MediaItem) {
    if (item.played && !item.resumable && item.kind != ItemKind.Series) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(Nuit.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("✓", style = MaterialTheme.typography.labelMedium, color = EnLigne) }
    }
    val unplayed = item.unplayedCount
    if (item.kind == ItemKind.Series && unplayed != null && unplayed > 0) {
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).background(Focus, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text("$unplayed", style = MaterialTheme.typography.labelMedium, color = Nuit)
        }
    }
    if (item.resumable) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Nuit.copy(alpha = 0.6f))) {
            Box(Modifier.fillMaxWidth(item.progress).height(4.dp).background(Focus))
        }
    }
}

/** Petite étiquette « JELLYFIN » de la maquette. */
@Composable
fun SourceBadge(text: String, modifier: Modifier = Modifier, color: Color = JellyfinPurple) {
    Box(modifier.background(color, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = 0.5.sp), color = Lune)
    }
}
