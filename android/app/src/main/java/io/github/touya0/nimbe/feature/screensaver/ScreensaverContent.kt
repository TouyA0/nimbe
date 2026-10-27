package io.github.touya0.nimbe.feature.screensaver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.homeassistant.HaLive
import io.github.touya0.nimbe.data.homeassistant.SensorCatalog
import io.github.touya0.nimbe.data.homeassistant.SensorKind
import io.github.touya0.nimbe.feature.status.ServerLevel
import io.github.touya0.nimbe.feature.status.ServerVerdict
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.ClockStyle
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** Lune assombrie : l'écran de veille reste peu lumineux, pour l'écran comme pour la pièce. */
private val Dim = Lune.copy(alpha = 0.72f)
private val Dimmer = Lune.copy(alpha = 0.45f)

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/**
 * Écran de veille « Heure et serveur » : fond noir, heure en bas à gauche avec la date et la météo,
 * état du serveur en bas à droite. Tout se décale de quelques pixels chaque minute (anti-marquage).
 */
@Composable
fun ScreensaverContent(verdict: ServerVerdict, ha: HaLive) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            // Réveil pile au changement de minute.
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val minute = System.currentTimeMillis() / 60_000
    val (dx, dy) = ScreensaverText.burnInOffset(minute, radius = 12f)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .offset(dx.dp, dy.dp)
                .padding(horizontal = 72.dp, vertical = 56.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    now.format(TimeFormat),
                    style = ClockStyle.copy(fontSize = 120.sp, lineHeight = 124.sp),
                    color = Dim,
                )
                val date = now.format(DateFormat).replaceFirstChar { it.uppercase() }
                Text(
                    listOfNotNull(date, ScreensaverText.weather(ha.weather)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    color = Dimmer,
                )
            }
            ServerCorner(verdict, ha)
        }
    }
}

@Composable
private fun ServerCorner(verdict: ServerVerdict, ha: HaLive) {
    val label = verdict.label ?: return
    val color = when (verdict.level) {
        ServerLevel.Ok -> EnLigne
        ServerLevel.ServiceDown, ServerLevel.NoInternet -> Focus
        ServerLevel.PiUnreachable, ServerLevel.NoNetwork -> Alerte
        ServerLevel.Unknown, ServerLevel.Hidden -> Dimmer
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (verdict.level == ServerLevel.Unknown) {
                Box(Modifier.size(10.dp).border(1.5.dp, color, CircleShape))
            } else {
                Box(Modifier.size(10.dp).background(color.copy(alpha = 0.8f), CircleShape))
            }
            Text(label, style = MaterialTheme.typography.titleMedium, color = Dim)
        }
        // Les mesures ne s'affichent qu'en direct : une valeur périmée n'a rien à faire sur un écran de veille.
        if (verdict.level == ServerLevel.Ok || verdict.level == ServerLevel.ServiceDown) {
            val measures = ha.sensors.mapNotNull { ha.entities[it] }.filter { SensorCatalog.kindOf(it) != SensorKind.Service }
            val stats = ScreensaverText.stats(measures)
            if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodyMedium, color = Dimmer)
        }
    }
}
