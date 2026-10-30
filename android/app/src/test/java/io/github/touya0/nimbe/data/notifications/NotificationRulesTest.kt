package io.github.touya0.nimbe.data.notifications

import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRulesTest {

    private val adult: Profile? = null
    private val noon = 12 * 60
    private val night = 2 * 60
    private val prefs = NotificationPrefs()

    private fun show(type: NotificationType, level: NotificationLevel, presence: Presence, minute: Int = noon, current: NotificationPrefs = prefs) =
        NotificationRules.showsBanner(type, level, current, adult, presence, minute)

    @Test
    fun `une alerte critique passe partout`() {
        assertTrue(show(NotificationType.Server, NotificationLevel.Critical, Presence.Playing))
        assertTrue(show(NotificationType.Server, NotificationLevel.Critical, Presence.OtherApp, night))
    }

    @Test
    fun `pendant un film, pas de normal ni de discret par défaut`() {
        assertFalse(show(NotificationType.Disk, NotificationLevel.Normal, Presence.Playing))
        assertTrue(show(NotificationType.Disk, NotificationLevel.Normal, Presence.Browsing))
        val allowing = prefs.copy(duringPlayback = DuringPlayback.CriticalAndNormal)
        assertTrue(show(NotificationType.Disk, NotificationLevel.Normal, Presence.Playing, current = allowing))
        assertFalse(show(NotificationType.Home, NotificationLevel.Discreet, Presence.Playing, current = allowing))
    }

    @Test
    fun `heures calmes, seulement la cloche`() {
        assertFalse(show(NotificationType.Disk, NotificationLevel.Normal, Presence.Browsing, night))
        assertTrue(show(NotificationType.Disk, NotificationLevel.Normal, Presence.Browsing, night, prefs.copy(quietHours = null)))
    }

    @Test
    fun `le mode choisi est respecté, et une panne ne se coupe pas tout à fait`() {
        assertFalse(show(NotificationType.Episode, NotificationLevel.Discreet, Presence.Browsing))
        val never = prefs.copy(modes = mapOf(NotificationType.Server to DeliveryMode.Never))
        assertEquals(DeliveryMode.Banner, never.mode(NotificationType.Server))
    }

    @Test
    fun `réglages relus à l'identique, illisibles remplacés par défaut`() {
        val changed = NotificationPrefs(mapOf(NotificationType.Disk to DeliveryMode.Never), DuringPlayback.CriticalAndNormal, null, 15)
        assertEquals(changed, NotificationPrefsCodec.decode(NotificationPrefsCodec.encode(changed)))
        assertEquals(NotificationPrefs(), NotificationPrefsCodec.decode("pas du json"))
    }

    @Test
    fun `invité et enfant ne voient rien de la maison`() {
        val child = Profile(id = "e", name = "Léo", color = 0, kind = ProfileKind.Child)
        assertFalse(NotificationRules.concerns(NotificationType.Server, child))
        assertTrue(NotificationRules.concerns(NotificationType.ScreenTime, child))
        assertFalse(NotificationRules.concerns(NotificationType.ScreenTime, adult))
    }
}
