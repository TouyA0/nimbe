package io.github.touya0.nimbe.data.notifications

import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.isRestricted

/** Où en est la TV au moment de l'alerte. */
enum class Presence {
    /** Dans Nimbe, hors lecture. */
    Browsing,

    /** Dans le lecteur de Nimbe. */
    Playing,

    /** Dans une autre appli : on ne sait pas ce qui s'y joue, on la traite comme un film. */
    OtherApp,
}

/** Règles d'affichage, sans Android (testées sur le PC). */
object NotificationRules {

    /** Le bandeau s'affiche-t-il ? La notification, elle, reste de toute façon dans la cloche. */
    fun showsBanner(
        type: NotificationType,
        level: NotificationLevel,
        prefs: NotificationPrefs,
        profile: Profile?,
        presence: Presence,
        minuteOfDay: Int,
    ): Boolean {
        if (!concerns(type, profile)) return false
        if (prefs.mode(type) != DeliveryMode.Banner) return false
        if (level == NotificationLevel.Critical) return true
        if (prefs.quietHours?.contains(minuteOfDay) == true) return false
        return when (presence) {
            Presence.Browsing -> true
            Presence.Playing, Presence.OtherApp -> level == NotificationLevel.Normal && prefs.duringPlayback == DuringPlayback.CriticalAndNormal
        }
    }

    /**
     * Invité et enfant ne voient rien de ce qui concerne la maison (titres regardés, état du serveur) :
     * seulement la fin du temps d'écran, qui ne concerne qu'eux.
     */
    fun concerns(type: NotificationType, profile: Profile?): Boolean =
        if (profile.isRestricted) type == NotificationType.ScreenTime else type != NotificationType.ScreenTime

    /** Ce que la cloche montre au profil. */
    fun bell(entries: List<NotificationEntry>, prefs: NotificationPrefs, profile: Profile?): List<NotificationEntry> =
        if (profile.isRestricted) {
            emptyList()
        } else {
            entries.filter { concerns(it.notificationType, profile) && prefs.mode(it.notificationType) != DeliveryMode.Never }
        }
}
