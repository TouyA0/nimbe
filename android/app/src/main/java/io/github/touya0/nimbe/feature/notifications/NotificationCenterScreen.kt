package io.github.touya0.nimbe.feature.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.notifications.NotificationEntry
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Centre de notifications, en panneau à droite comme sur la maquette. [entries] : null pendant la lecture.
 * [unread] : compté à l'ouverture, car ouvrir le panneau marque tout comme lu.
 */
@Composable
fun NotificationCenterScreen(
    entries: List<NotificationEntry>?,
    unread: Int,
    onOpen: (NotificationEntry) -> Unit,
    onClearAll: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val firstRequester = remember { FocusRequester() }
    val settingsRequester = remember { FocusRequester() }
    val empty = entries.isNullOrEmpty()
    LaunchedEffect(empty) { runCatching { (if (empty) settingsRequester else firstRequester).requestFocus() } }
    Row(Modifier.fillMaxSize().background(Nuit)) {
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.width(620.dp).fillMaxHeight().background(Panneau).padding(horizontal = 32.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                if (unread > 0) {
                    Text(
                        if (unread == 1) "1 nouvelle" else "$unread nouvelles",
                        style = MaterialTheme.typography.labelMedium,
                        color = Lune,
                        modifier = Modifier.background(PanneauClair, NimbeFocus.PillShape).padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    entries == null -> Text("Chargement…", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
                    entries.isEmpty() -> Text(
                        "Rien de nouveau. Les pannes, les nouveaux épisodes et les mises à jour s'afficheront ici.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LuneAttenuee,
                    )
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                    ) {
                        items(entries, key = { it.key }) { entry ->
                            EntryRow(
                                entry,
                                onClick = { onOpen(entry) },
                                modifier = if (entry === entries.first()) Modifier.focusRequester(firstRequester) else Modifier,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!empty) PillButton("Tout effacer", onClearAll)
                PillButton("Régler les notifications", onSettings, Modifier.focusRequester(settingsRequester))
            }
        }
    }
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DayFormat = DateTimeFormatter.ofPattern("d/MM HH:mm")

/** L'heure seule aujourd'hui, avec le jour avant. */
private fun timeLabel(at: Long): String {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(at).atZone(zone)
    return moment.format(if (moment.toLocalDate() == java.time.LocalDate.now(zone)) TimeFormat else DayFormat)
}

@Composable
private fun EntryRow(entry: NotificationEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PanneauClair.copy(alpha = 0.5f),
            contentColor = Lune,
            focusedContainerColor = PanneauClair,
            focusedContentColor = Lune,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text(entry.kicker, style = MaterialTheme.typography.labelMedium, color = entry.color(), modifier = Modifier.weight(1f))
                Text(timeLabel(entry.at), style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
            }
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            entry.body?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 2) }
        }
    }
}
