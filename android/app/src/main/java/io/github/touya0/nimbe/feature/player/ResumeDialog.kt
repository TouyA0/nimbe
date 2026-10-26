package io.github.touya0.nimbe.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.feature.media.Backdrop
import io.github.touya0.nimbe.feature.media.JellyfinImage
import io.github.touya0.nimbe.feature.media.MediaText
import io.github.touya0.nimbe.feature.media.SourceBadge
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * « Reprendre à 1:21:04 » ou « Recommencer », avant de lancer un contenu entamé.
 * Proposé seulement entre 2 min et 95 % (voir [ResumeRule]). Retour revient d'où l'on venait.
 */
@Composable
fun ResumeDialog(item: MediaItem, onPlay: (PlayRequest) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val resume = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { resume.requestFocus() } }
    val position = PlayerText.time(item.positionTicks / PlayerController.TICKS_PER_MS)
    val total = item.runTimeTicks?.let { PlayerText.time(it / PlayerController.TICKS_PER_MS) }

    Box(Modifier.fillMaxSize().background(Nuit), contentAlignment = Alignment.Center) {
        Backdrop(item.backdrop)
        Column(
            Modifier
                .width(620.dp)
                .background(Panneau.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .border(1.dp, PanneauClair, RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(PanneauClair)) {
                    JellyfinImage(item.primary ?: item.seriesPrimary, width = 110.dp, height = null, modifier = Modifier.fillMaxSize())
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceBadge("JELLYFIN")
                    Text(MediaText.title(item), style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.episodeCode != null) Text(MediaText.episodeLine(item), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        listOfNotNull("Tu t'es arrêté à $position", total?.let { "sur $it" }, PlayerText.whenPlayed(item.lastPlayed)).joinToString(" "),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Box(Modifier.fillMaxWidth().height(4.dp).background(PanneauClair, RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxWidth(item.progress).height(4.dp).background(Focus, RoundedCornerShape(2.dp)))
                    }
                    Text("Position synchronisée avec Jellyfin", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("▶  Reprendre à $position", onClick = { onPlay(ResumeRule.resume(item)) }, modifier = Modifier.focusRequester(resume))
                PillButton("↺  Recommencer", onClick = { onPlay(PlayRequest(item, 0)) })
            }
            Text(
                "Retour : revenir en arrière · Ce choix n'apparaît pas avant 2 min ni après 95 %",
                style = MaterialTheme.typography.labelMedium,
                color = LuneAttenuee,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** « hier soir », « aujourd'hui », « le 12 octobre ». */
fun PlayerText.whenPlayed(at: Instant?, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String? {
    val moment = at?.atZone(zone) ?: return null
    val days = ChronoUnit.DAYS.between(moment.toLocalDate(), today)
    val evening = moment.hour >= 18
    return when {
        days <= 0 -> if (evening) "ce soir" else "aujourd'hui"
        days == 1L -> if (evening) "hier soir" else "hier"
        else -> "le " + moment.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))
    }
}
