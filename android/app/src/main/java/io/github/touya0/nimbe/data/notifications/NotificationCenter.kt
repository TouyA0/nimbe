package io.github.touya0.nimbe.data.notifications

import io.github.touya0.nimbe.data.profiles.ProfilesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.ZoneId

/** Un bandeau à l'écran ; [persistent] : il reste jusqu'à ce qu'on le ferme. */
data class Banner(val entry: NotificationEntry, val persistent: Boolean)

/**
 * Enregistre les alertes (une seule fois chacune) et décide lesquelles s'affichent en bandeau.
 * Vit avec le processus : les alertes arrivent aussi pendant un film ou une autre appli.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotificationCenter(
    private val dao: NotificationDao,
    val prefs: NotificationPrefsStore,
    private val profiles: ProfilesStore,
    scope: CoroutineScope,
) {
    /** Nimbe à l'écran ([Presence.Browsing]) ou une autre appli : tenue à jour par l'activité. */
    val presence = MutableStateFlow(Presence.OtherApp)

    /** Le lecteur de Nimbe est ouvert. */
    val playing = MutableStateFlow(false)

    private val bannerState = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = bannerState.asStateFlow()

    private val queue = Channel<Banner>(Channel.UNLIMITED)
    private val dismissals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Ce que la cloche montre au profil actif. */
    val bell: Flow<List<NotificationEntry>> = profiles.state.flatMapLatest { state ->
        val profile = state.active
        combine(dao.visible(), prefs.prefs(profile?.id.orEmpty())) { entries, current -> NotificationRules.bell(entries, current, profile) }
    }

    init {
        scope.launch { showBanners() }
    }

    suspend fun post(notification: NewNotification, now: Long = System.currentTimeMillis()) {
        val entry = NotificationEntry(
            key = notification.key,
            type = notification.type.key,
            level = notification.level.name,
            kicker = notification.kicker.take(MAX_KICKER),
            title = notification.title.take(MAX_TITLE),
            body = notification.body?.take(MAX_BODY),
            at = now,
            target = notification.target,
        )
        if (dao.insert(entry) == -1L) return
        val state = profiles.state.first()
        val profile = state.active
        val current = prefs.prefs(profile?.id.orEmpty()).first()
        val where = if (presence.value == Presence.Browsing && playing.value) Presence.Playing else presence.value
        val minute = LocalTime.now(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
        if (NotificationRules.showsBanner(notification.type, notification.level, current, profile, where, minute)) {
            queue.send(Banner(entry, persistent = notification.level == NotificationLevel.Critical))
        }
    }

    /** Ferme le bandeau affiché. */
    fun dismissBanner() {
        dismissals.tryEmit(Unit)
    }

    suspend fun markAllRead() = dao.markAllRead()

    suspend fun clearAll() = dao.clearAll()

    suspend fun cleanUp(now: Long = System.currentTimeMillis()) = dao.deleteOlderThan(now - KEEP_MS)

    private suspend fun showBanners() {
        for (banner in queue) {
            // Resté en attente derrière une alerte critique : passé, il n'a plus d'intérêt.
            if (!banner.persistent && System.currentTimeMillis() - banner.entry.at > STALE_MS) continue
            bannerState.value = banner
            val seconds = prefs.prefs(profiles.state.first().active?.id.orEmpty()).first().bannerSeconds
            if (banner.persistent) {
                dismissals.first()
            } else {
                withTimeoutOrNull(seconds * 1000L) { dismissals.first() }
            }
            bannerState.value = null
        }
    }

    private companion object {
        const val STALE_MS = 60_000L
        const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_KICKER = 40
        const val MAX_TITLE = 120
        const val MAX_BODY = 200
    }
}
