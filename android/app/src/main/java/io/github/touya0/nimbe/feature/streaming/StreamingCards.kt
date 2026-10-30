package io.github.touya0.nimbe.feature.streaming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.touya0.nimbe.data.streaming.JustWatchImages
import io.github.touya0.nimbe.data.streaming.StreamingCatalog
import io.github.touya0.nimbe.data.streaming.StreamingService
import io.github.touya0.nimbe.data.streaming.StreamingTitle
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.feature.media.Chip
import io.github.touya0.nimbe.feature.media.MediaSurface
import io.github.touya0.nimbe.feature.media.MediaText
import io.github.touya0.nimbe.feature.media.ScoreLabel
import io.github.touya0.nimbe.feature.media.SourceBadge
import io.github.touya0.nimbe.feature.media.titleStyle
import io.github.touya0.nimbe.feature.media.FadingBackdrop
import io.github.touya0.nimbe.feature.media.HeroWidth
import io.github.touya0.nimbe.feature.media.Kicker
import io.github.touya0.nimbe.feature.media.Overview
import io.github.touya0.nimbe.ui.theme.FilmTitleStyle
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Badge d'une plateforme, à sa couleur. */
@Composable
fun ServiceBadge(service: StreamingService, modifier: Modifier = Modifier) {
    SourceBadge(service.badge, modifier, color = Color(service.color))
}

/** Image d'une plateforme ou de JustWatch ; rien n'est dessiné sans adresse. */
@Composable
fun StreamingImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (url == null) return
    AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = modifier)
}

/** Textes d'un titre des plateformes, en français. */
object StreamingText {

    /** « TP » devient « Tous publics », « 12 » devient « 12+ ». */
    fun rating(raw: String?): String? = when {
        raw.isNullOrBlank() -> null
        raw == "TP" || raw == "U" -> "Tous publics"
        raw.all(Char::isDigit) -> "$raw+"
        else -> raw
    }

    /** « 2025 · 1 h 48 · Animation, Comédie » ; pour une série : « 2 saisons · 2023 · Drame ». */
    fun meta(title: StreamingTitle): String = listOfNotNull(
        title.seasons?.takeIf { title.kind == TitleKind.Show }?.let { if (it > 1) "$it saisons" else "1 saison" },
        title.year?.toString(),
        title.runtimeMinutes?.takeIf { title.kind == TitleKind.Movie }?.let(MediaText::duration),
        title.categoryLabels.take(2).joinToString(", ").ifEmpty { null },
    ).joinToString(" · ")

    /** Pour les lecteurs d'écran : « Vice-versa 2, film, sur Disney+ ». */
    fun spoken(title: StreamingTitle, services: List<StreamingService> = title.services): String = listOfNotNull(
        title.title,
        if (title.kind == TitleKind.Movie) "film" else "série",
        services.takeIf { it.isNotEmpty() }?.joinToString(" et ", prefix = "sur ") { it.label },
    ).joinToString(", ")
}

/**
 * Affiche 2:3 d'un titre des plateformes, comme celles de Jellyfin. En haut à gauche, [service] quand la liste
 * mêle plusieurs plateformes ; « J » violet quand le titre est aussi sur Jellyfin.
 */
@Composable
fun StreamingPosterCard(
    title: StreamingTitle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 104.dp,
    service: StreamingService? = title.services.firstOrNull(),
    inJellyfin: Boolean = false,
    onFocus: () -> Unit = {},
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
) {
    MediaSurface(
        onClick = onClick,
        onFocus = onFocus,
        restoreFocus = restoreFocus,
        onRestored = onRestored,
        label = StreamingText.spoken(title) + if (inJellyfin) ", aussi sur Jellyfin" else "",
        modifier = modifier.width(width).aspectRatio(2f / 3f),
        dimmed = false,
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                title.title.uppercase(),
                style = FilmTitleStyle.copy(fontSize = 22.sp, lineHeight = 22.sp),
                textAlign = TextAlign.Center,
                maxLines = 4,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
            StreamingImage(JustWatchImages.poster(title.poster, large = width > 120.dp), Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.TopStart).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                service?.let { ServiceBadge(it) }
                if (inJellyfin) SourceBadge("J")
            }
        }
    }
}

/**
 * Vignette 16:9 d'un titre ouvert sur une plateforme (« Continuer à regarder ») : fond, plateforme,
 * et dessous le titre et quand il a été ouvert. [onMenu] : retirer de la rangée.
 */
@Composable
fun StreamingWideCard(
    title: StreamingTitle,
    service: StreamingService,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 196.dp,
    onFocus: () -> Unit = {},
    onMenu: ((FocusRequester) -> Unit)? = null,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    dimmed: Boolean = false,
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MediaSurface(
            onClick = onClick,
            onFocus = onFocus,
            restoreFocus = restoreFocus,
            onRestored = onRestored,
            label = listOfNotNull(StreamingText.spoken(title, listOf(service)), detail).joinToString(", "),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            dimmed = dimmed,
            onLongClick = onMenu,
        ) {
            Box(Modifier.fillMaxSize().background(PanneauClair)) {
                Text(
                    title.title.uppercase(),
                    style = FilmTitleStyle.copy(fontSize = 24.sp, lineHeight = 24.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                )
                StreamingImage(JustWatchImages.backdrop(title.backdrop, large = false), Modifier.fillMaxSize())
                ServiceBadge(service, Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }
        Text(title.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 1) }
    }
}

/**
 * Haut d'écran pour un titre des plateformes, sur le modèle du bandeau Jellyfin : plateformes et accroche,
 * grand titre en Bebas Neue, détails, résumé.
 */
@Composable
fun StreamingHero(title: StreamingTitle, kicker: String?, modifier: Modifier = Modifier, overviewLines: Int = 3, compact: Boolean = false) {
    val ui = LocalStreaming.current
    // Un titre de « Continuer à regarder » n'a gardé que son nom et ses images : on complète avec sa fiche.
    val title = if (title.overview == null && ui != null) {
        rememberStreaming(StreamingCatalog.detailKey(title.id, ui.services), title.id) { detail(title.id, ui.services) }.value ?: title
    } else {
        title
    }
    val services = title.services.filter { ui == null || it in ui.services }.ifEmpty { title.services }
    Column(modifier.width(HeroWidth), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            services.take(3).forEach { ServiceBadge(it) }
            if (ui?.inJellyfin(title) != null) SourceBadge("AUSSI SUR JELLYFIN")
            kicker?.let { Kicker(it) }
        }
        Text(title.title.uppercase(), style = titleStyle(title.title, compact), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreLabel(title.score)
            Text(StreamingText.meta(title), style = MaterialTheme.typography.bodyMedium)
            StreamingText.rating(title.certification)?.let { Chip(it) }
        }
        title.overview?.let { Overview(it, overviewLines) }
    }
}

/**
 * Fond d'un titre des plateformes, fondu comme celui de Jellyfin. Il ne change qu'après un court arrêt
 * sur une carte : parcourir une rangée ne télécharge pas tous les fonds.
 */
@Composable
fun StreamingBackdrop(template: String?, modifier: Modifier = Modifier, compact: Boolean = false) {
    FadingBackdrop(JustWatchImages.backdrop(template), modifier, compact)
}

/** « Ouvert aujourd'hui », « Ouvert hier », « Ouvert il y a 3 jours », en jours du calendrier. */
fun openedLabel(openedAt: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(openedAt).atZone(zone).toLocalDate(), today).coerceAtLeast(0)
    return when (days) {
        0L -> "Ouvert aujourd'hui"
        1L -> "Ouvert hier"
        else -> "Ouvert il y a $days jours"
    }
}

/** Coin arrondi des encarts de la partie streaming. */
internal val PanelShape = RoundedCornerShape(16.dp)
