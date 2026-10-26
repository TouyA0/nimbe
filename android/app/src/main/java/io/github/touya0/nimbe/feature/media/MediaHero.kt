package io.github.touya0.nimbe.feature.media

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.ImageRef
import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.ui.theme.FilmTitleStyle
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.delay

/**
 * Image de fond de l'élément focalisé, fondue vers la gauche et le bas pour garder le texte lisible.
 * Elle ne change qu'après un court arrêt sur une carte : parcourir une rangée ne télécharge pas tous les fonds.
 */
@Composable
fun Backdrop(ref: ImageRef?, modifier: Modifier = Modifier) {
    val shown by produceState(ref, ref) {
        delay(BACKDROP_DELAY_MS)
        value = ref
    }
    Box(modifier.fillMaxSize()) {
        // 640 dp : 1280 pixels sur un écran 1080p, assez pour un fond fondu.
        JellyfinImage(
            shown,
            width = 640.dp,
            height = null,
            modifier = Modifier.fillMaxHeight(0.78f).fillMaxWidth(0.72f).align(Alignment.TopEnd),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Nuit, 0.35f to Nuit.copy(alpha = 0.9f), 0.65f to Nuit.copy(alpha = 0.4f), 1f to Color.Transparent),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Nuit, 0.12f to Nuit.copy(alpha = 0.3f), 0.25f to Color.Transparent, 0.45f to Color.Transparent, 0.78f to Nuit),
            ),
        )
    }
}

private const val BACKDROP_DELAY_MS = 350L

/**
 * Haut d'écran de la maquette : source et accroche, grand titre, détails, résumé.
 * Les films et séries ont leur titre en Bebas Neue ; un épisode affiche sa série puis sa ligne d'épisode.
 */
@Composable
fun Hero(item: MediaItem, kicker: String?, modifier: Modifier = Modifier, overviewLines: Int = 3) {
    Column(modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceBadge("JELLYFIN")
            kicker?.let { Text(it, style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.5.sp), color = Focus) }
        }
        if (item.kind == ItemKind.Episode) {
            Text(MediaText.title(item), style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp), maxLines = 1)
            Text(MediaText.episodeLine(item), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(item.name.uppercase(), style = titleStyle(item.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
            MetaLine(item)
        }
        if (item.resumable) {
            ProgressLine(item)
        } else {
            item.overview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee, maxLines = overviewLines, overflow = TextOverflow.Ellipsis)
            }
        }
    }
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
        Text(MediaText.meta(item), style = MaterialTheme.typography.bodyMedium)
        MediaText.rating(item.officialRating)?.let { Chip(it) }
    }
}

@Composable
fun Chip(text: String) {
    Box(Modifier.border(1.dp, LuneAttenuee, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** Barre de progression et temps restant. */
@Composable
fun ProgressLine(item: MediaItem, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(240.dp).height(4.dp).background(PanneauClair, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(item.progress).height(4.dp).background(Focus, RoundedCornerShape(2.dp)))
        }
        MediaText.progressLine(item)?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
    }
}
