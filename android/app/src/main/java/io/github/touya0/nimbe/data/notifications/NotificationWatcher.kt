package io.github.touya0.nimbe.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import io.github.touya0.nimbe.core.network.ConnectivityMonitor
import io.github.touya0.nimbe.core.network.NetworkState
import io.github.touya0.nimbe.data.homeassistant.HaEntity
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantMonitor
import io.github.touya0.nimbe.data.jellyfin.JellyfinException
import io.github.touya0.nimbe.data.jellyfin.JellyfinLibrary
import io.github.touya0.nimbe.data.profiles.ProfilesStore
import io.github.touya0.nimbe.data.profiles.ScreenTime
import io.github.touya0.nimbe.data.profiles.ScreenTimeStore
import io.github.touya0.nimbe.data.profiles.ScreenTimeVerdict
import io.github.touya0.nimbe.data.profiles.isChild
import io.github.touya0.nimbe.update.UpdateManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Surveille ce qui mérite une alerte, tant que l'écran est allumé : le Pi et ses services (Home Assistant),
 * les nouveaux épisodes (Jellyfin), les mises à jour, la fin du temps d'écran. Écran éteint, tout s'arrête :
 * on laisse le Pi tranquille, et la connexion à Home Assistant se ferme.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationWatcher(
    private val context: Context,
    private val center: NotificationCenter,
    private val homeAssistant: HomeAssistantMonitor,
    private val library: JellyfinLibrary,
    private val network: ConnectivityMonitor,
    private val readyUpdate: Flow<UpdateManifest?>,
    private val profiles: ProfilesStore,
    private val screenTime: ScreenTimeStore,
    private val scope: CoroutineScope,
) {
    private val screenOn = MutableStateFlow(context.getSystemService(PowerManager::class.java)?.isInteractive ?: true)
    private val alerts = ServiceAlerts()

    fun start() {
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    screenOn.value = intent.action == Intent.ACTION_SCREEN_ON
                }
            },
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch { center.cleanUp() }
        scope.launch { screenOn.collectLatest { on -> if (on) watchHomeAssistant() } }
        scope.launch { screenOn.collectLatest { on -> if (on) watchEpisodes() } }
        scope.launch { screenOn.collectLatest { on -> if (on) watchScreenTime() } }
        scope.launch {
            readyUpdate.collect { manifest ->
                if (manifest != null) {
                    center.post(
                        NewNotification(
                            key = "update:${manifest.versionCode}",
                            type = NotificationType.Update,
                            level = NotificationLevel.Discreet,
                            kicker = "MISE À JOUR",
                            title = "Nimbe ${manifest.versionName} est prêt",
                            body = "OK pour voir les nouveautés et l'installer",
                            target = NotificationTarget.UPDATE,
                        ),
                    )
                }
            }
        }
    }

    /** Toutes les 30 s en plus de chaque changement : une panne se confirme aussi quand plus rien n'arrive. */
    private suspend fun watchHomeAssistant() = coroutineScope {
        val networkUp = MutableStateFlow(true)
        launch { network.states.collect { networkUp.value = it != NetworkState.NoNetwork } }
        launch { homeAssistant.live.collect { live -> live.entities[HaEntity.NOTICE_ENTITY]?.let { postNotice(it) } } }
        while (true) {
            alerts.evaluate(homeAssistant.live.value, networkUp.value, System.currentTimeMillis()).forEach { center.post(it) }
            delay(CHECK_MS)
        }
    }

    /** Événement nimbe_notify d'une automatisation, reçu par le capteur `sensor.nimbe_notification`. */
    private suspend fun postNotice(entity: HaEntity) {
        val notice = entity.notice ?: return
        val at = entity.updatedAt ?: return
        // Le capteur garde sa dernière valeur : passé un jour, c'est une vieille histoire, pas une alerte.
        if (System.currentTimeMillis() - at > NOTICE_MAX_AGE_MS || notice.title.isBlank()) return
        center.post(
            NewNotification(
                key = "ha:${entity.state}",
                type = NotificationType.Home,
                level = when (notice.level) {
                    "critique", "critical" -> NotificationLevel.Critical
                    "discret", "discreet" -> NotificationLevel.Discreet
                    else -> NotificationLevel.Normal
                },
                kicker = "HOME ASSISTANT",
                title = notice.title,
                body = notice.detail,
                target = NotificationTarget.STATUS,
            ),
        )
    }

    private suspend fun watchEpisodes() {
        while (true) {
            runCatching { checkEpisodes() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            delay(EPISODES_EVERY_MS)
        }
    }

    private suspend fun checkEpisodes() {
        val auth = library.auth() ?: return
        val now = System.currentTimeMillis()
        val seen = center.prefs.episodesSeenAt()
        // La première fois, tout ce qui existe déjà n'a rien de nouveau.
        if (seen == null) {
            center.prefs.setEpisodesSeenAt(now)
            return
        }
        val fresh = try {
            library.newEpisodes(auth, seen)
        } catch (_: JellyfinException) {
            return
        }
        fresh.take(MAX_EPISODES).forEach { episode ->
            center.post(
                NewNotification(
                    key = "episode:${episode.id}",
                    type = NotificationType.Episode,
                    level = NotificationLevel.Discreet,
                    kicker = "NOUVEL ÉPISODE",
                    title = listOfNotNull(episode.seriesName, episode.episodeCode).joinToString(" · ").ifBlank { episode.name },
                    body = "Ajouté à Jellyfin",
                    target = NotificationTarget.item(episode.id),
                ),
            )
        }
        center.prefs.setEpisodesSeenAt(now)
    }

    private suspend fun watchScreenTime() {
        while (true) {
            val child = profiles.state.first().active?.takeIf { it.isChild }
            if (child != null) {
                val zone = ZoneId.systemDefault()
                val now = System.currentTimeMillis()
                val verdict = ScreenTime.verdict(child.limits, screenTime.usage(child.id).first(), now, zone)
                val remaining = (verdict as? ScreenTimeVerdict.Allowed)?.remainingMs
                if (remaining != null && remaining in 1..SCREEN_TIME_WARNING_MS) {
                    center.post(
                        NewNotification(
                            key = "time:${child.id}:${LocalDate.now(zone)}",
                            type = NotificationType.ScreenTime,
                            level = NotificationLevel.Normal,
                            kicker = "TEMPS D'ÉCRAN",
                            title = "Plus que ${ScreenTime.formatDuration(remaining)} de télé",
                            body = "Pense à finir ce que tu regardes",
                        ),
                    )
                }
            }
            delay(CHECK_MS)
        }
    }

    private companion object {
        const val CHECK_MS = 30_000L
        const val EPISODES_EVERY_MS = 30 * 60_000L
        const val SCREEN_TIME_WARNING_MS = 5 * 60_000L
        const val NOTICE_MAX_AGE_MS = 24 * 60 * 60_000L
        const val MAX_EPISODES = 5
    }
}
