package io.github.touya0.nimbe.feature.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.touya0.nimbe.data.jellyfin.ImageRef
import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.feature.streaming.StreamingHero
import io.github.touya0.nimbe.ui.theme.FilmTitleStyle
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.KickerStyle
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeMotion
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.nimbeTween
import kotlinx.coroutines.delay

/** Adresse d'un fond Jellyfin, à la taille utile : 640 dp, soit 1280 pixels sur un écran 1080p. */
@Composable
fun rememberBackdropUrl(ref: ImageRef?): String? {
    val server = LocalJellyfin.current?.link?.serverUrl
    val density = LocalDensity.current
    return remember(ref, server) { if (ref == null || server == null) null else ref.url(server, maxWidth = with(density) { 640.dp.roundToPx() }) }
}

/**
 * Image de fond de l'élément focalisé, fondue vers la gauche et le bas pour garder le texte lisible.
 * Elle ne change qu'après un court arrêt sur une carte : parcourir une rangée ne télécharge pas tous les fonds.
 */
@Composable
fun Backdrop(ref: ImageRef?, modifier: Modifier = Modifier) {
    FadingBackdrop(rememberBackdropUrl(ref), modifier)
}

/**
 * Fond plein écran qui passe d'une image à l'autre en fondu enchaîné : la nouvelle image est d'abord
 * chargée en coulisse, l'ancienne reste affichée jusque-là. Jamais d'écran noir entre deux cartes.
 * [image] : une adresse, ou une [ImageRequest] quand le serveur demande un en-tête (Nextcloud).
 */
@Composable
fun FadingBackdrop(image: Any?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wanted by produceState(image, image) {
        delay(BACKDROP_DELAY_MS)
        value = image
    }
    var shown by remember { mutableStateOf(image) }
    LaunchedEffect(wanted) {
        val target = wanted
        if (target == null) {
            shown = null
            return@LaunchedEffect
        }
        val result = SingletonImageLoader.get(context).execute(target as? ImageRequest ?: ImageRequest.Builder(context).data(target).build())
        // Une image introuvable laisse la précédente en place plutôt qu'un trou.
        if (result is SuccessResult) shown = target
    }
    Box(modifier.fillMaxSize()) {
        Crossfade(
            targetState = shown,
            animationSpec = nimbeTween(NimbeMotion.SLOW_MS),
            modifier = Modifier.fillMaxHeight(0.86f).fillMaxWidth(0.74f).align(Alignment.TopEnd),
            label = "fond",
        ) { current ->
            if (current != null) {
                AsyncImage(model = current, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        BackdropScrim()
    }
}

/** Voiles du fond : le texte à gauche et les rangées du bas restent lisibles, l'en-tête aussi. */
@Composable
fun BackdropScrim(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().background(
            Brush.horizontalGradient(0f to Nuit, 0.3f to Nuit.copy(alpha = 0.92f), 0.6f to Nuit.copy(alpha = 0.35f), 1f to Color.Transparent),
        ),
    )
    Box(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Nuit.copy(alpha = 0.7f), 0.16f to Color.Transparent, 0.5f to Color.Transparent, 0.84f to Nuit),
        ),
    )
}

private const val BACKDROP_DELAY_MS = 300L

/** Change le haut d'un écran en fondu bref, quand [key] change. Avec « Réduire les animations », ces durées valent zéro. */
@Composable
fun <T> HeroSwitch(target: T, key: (T) -> Any?, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val enter = nimbeTween<Float>(NimbeMotion.MEDIUM_MS, delayMs = 60)
    val rise = nimbeTween<IntOffset>(NimbeMotion.MEDIUM_MS)
    val exit = nimbeTween<Float>(NimbeMotion.FAST_MS)
    AnimatedContent(
        targetState = target,
        contentKey = key,
        transitionSpec = { (fadeIn(enter) + slideInVertically(rise) { it / 24 }) togetherWith fadeOut(exit) },
        contentAlignment = Alignment.TopStart,
        modifier = modifier,
        label = "haut",
    ) { content(it) }
}

/**
 * Le haut d'un écran de catalogue : le titre focalisé, qui change en fondu bref quand le focus passe
 * d'une carte à l'autre. [placeholder] s'affiche avant le premier titre.
 */
@Composable
fun FocusHero(focus: CatalogFocus?, overviewLines: Int, modifier: Modifier = Modifier, placeholder: @Composable () -> Unit = {}) {
    HeroSwitch(focus, key = { it?.id }, modifier = modifier) { shown ->
        when (shown) {
            is CatalogFocus.Jellyfin -> Hero(shown.item, shown.kicker, overviewLines = overviewLines)
            is CatalogFocus.Streaming -> StreamingHero(shown.title, shown.kicker, overviewLines = overviewLines)
            is CatalogFocus.WatchNext -> WatchNextHero(shown.program)
            null -> placeholder()
        }
    }
}

/**
 * Haut d'écran de la maquette : accroche, grand titre, détails, résumé.
 * Les films et séries ont leur titre en Bebas Neue ; un épisode affiche sa série puis sa ligne d'épisode.
 */
@Composable
fun Hero(item: MediaItem, kicker: String?, modifier: Modifier = Modifier, overviewLines: Int = 3) {
    Column(modifier.width(HeroWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        kicker?.let { Kicker(it) }
        if (item.kind == ItemKind.Episode) {
            Text(MediaText.title(item).uppercase(), style = titleStyle(MediaText.title(item)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(MediaText.episodeLine(item), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(item.name.uppercase(), style = titleStyle(item.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
            MetaLine(item)
        }
        if (item.resumable) {
            ProgressLine(item, Modifier.padding(top = 4.dp))
        } else {
            item.overview?.let { Overview(it, overviewLines) }
        }
    }
}

/** Largeur du texte du haut d'écran : assez pour un titre, sans couvrir l'image de droite. */
val HeroWidth = 540.dp

/** Accroche ambre en capitales au-dessus du titre. */
@Composable
fun Kicker(text: String, color: Color = Focus) {
    Text(text, style = KickerStyle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Résumé sous le titre, en texte secondaire. */
@Composable
fun Overview(text: String, lines: Int, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
        color = LuneAttenuee,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = 2.dp),
    )
}

/** Grand titre en Bebas Neue, réduit pour les titres longs plutôt que coupé. */
fun titleStyle(name: String): TextStyle = when {
    name.length <= 22 -> FilmTitleStyle
    name.length <= 32 -> FilmTitleStyle.copy(fontSize = 44.sp, lineHeight = 48.sp)
    else -> FilmTitleStyle.copy(fontSize = 34.sp, lineHeight = 38.sp)
}

/** « 2026 · 2 h 08 · Aventure, Drame » suivi de la classification encadrée. */
@Composable
fun MetaLine(item: MediaItem) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(MediaText.meta(item), style = MaterialTheme.typography.bodyMedium, color = Lune)
        MediaText.rating(item.officialRating)?.let { Chip(it) }
    }
}

@Composable
fun Chip(text: String) {
    Box(Modifier.border(1.dp, LuneAttenuee.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Lune)
    }
}

/** Barre de progression et temps restant. */
@Composable
fun ProgressLine(item: MediaItem, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ProgressBar(item.progress, Modifier.width(220.dp))
        MediaText.progressLine(item)?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
    }
}

/** Barre fine de progression, arrondie, ambre sur fond discret. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).background(Lune.copy(alpha = 0.18f), RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(Focus, RoundedCornerShape(2.dp)))
    }
}
