package io.github.touya0.nimbe.feature.settings

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.notifications.DeliveryMode
import io.github.touya0.nimbe.data.notifications.DuringPlayback
import io.github.touya0.nimbe.data.notifications.NotificationCenter
import io.github.touya0.nimbe.data.notifications.NotificationPrefs
import io.github.touya0.nimbe.data.notifications.NotificationType
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import kotlinx.coroutines.launch

/** Réglages → Notifications : pour chaque type, bandeau, cloche seule ou jamais, et les règles du profil. */
@Composable
internal fun NotificationsPage(center: NotificationCenter, profileId: String, profileName: String) {
    PageTitle(
        "Notifications",
        "Pour chaque type d'alerte : un bandeau à l'écran, seulement dans le centre de notifications (la cloche), ou rien. Pour le profil $profileName.",
    )
    val scope = rememberCoroutineScope()
    val saved by remember(profileId) { center.prefs.prefs(profileId) }.collectAsStateWithLifecycle(initialValue = null)
    val prefs = saved ?: run {
        Text("Chargement…", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
        return
    }
    fun save(changed: NotificationPrefs) {
        scope.launch { center.prefs.save(profileId, changed) }
    }
    SettingsGroupTitle("Que veux-tu recevoir ?")
    NotificationType.Settable.forEach { type ->
        ModeRow(type, prefs.mode(type)) { save(prefs.copy(modes = prefs.modes + (type to it))) }
    }
    SettingsGroupTitle("Règles")
    LinkRow(
        "Pendant un film ou une série",
        "Dans le lecteur de Nimbe et dans les autres applis",
        prefs.duringPlayback.label,
        onClick = {
            val all = DuringPlayback.entries
            save(prefs.copy(duringPlayback = all[(all.indexOf(prefs.duringPlayback) + 1) % all.size]))
        },
    )
    LinkRow(
        "Heures calmes",
        "La nuit, seules les alertes critiques s'affichent ; le reste attend dans la cloche",
        if (prefs.quietHours == null) "Désactivées" else "23:00 → 8:00",
        onClick = { save(prefs.copy(quietHours = if (prefs.quietHours == null) NotificationPrefs.DefaultQuiet else null)) },
    )
    LinkRow(
        "Durée d'un bandeau",
        "Une alerte critique reste jusqu'à ce que tu la fermes avec Retour",
        "${prefs.bannerSeconds} secondes",
        onClick = {
            val all = NotificationPrefs.DurationChoices
            save(prefs.copy(bannerSeconds = all[(all.indexOf(prefs.bannerSeconds) + 1) % all.size]))
        },
    )
    SettingsGroupTitle("Par-dessus les autres applis")
    val allowed = Settings.canDrawOverlays(LocalContext.current)
    Text(
        if (allowed) {
            "Autorisé : les alertes critiques s'affichent aussi pendant une autre appli."
        } else {
            "Pas encore autorisé : pendant une autre appli, les alertes attendent ton retour dans Nimbe. " +
                "Google TV n'a pas ce réglage, il se donne une fois depuis le PC (voir le README de Nimbe)."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = LuneAttenuee,
        modifier = Modifier.width(640.dp),
    )
}

/** Gauche et droite changent le mode, OK passe au suivant. */
@Composable
private fun ModeRow(type: NotificationType, mode: DeliveryMode, onChange: (DeliveryMode) -> Unit) {
    val choices = type.choices
    fun shift(delta: Int) = onChange(choices[(choices.indexOf(mode) + delta).coerceIn(choices.indices)])
    SettingRow(
        title = type.label,
        detail = type.detail,
        onClick = { onChange(choices[(choices.indexOf(mode) + 1) % choices.size]) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { shift(-1); true }
                Key.DirectionRight -> { shift(1); true }
                else -> false
            }
        },
        trailing = {
            Row(Modifier.background(Nuit, NimbeFocus.PillShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                choices.forEach { choice ->
                    val selected = choice == mode
                    Box(Modifier.background(if (selected) Lune else Nuit, NimbeFocus.PillShape).padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Text(choice.label, style = MaterialTheme.typography.labelMedium, color = if (selected) Nuit else LuneAttenuee)
                    }
                }
            }
        },
    )
}
