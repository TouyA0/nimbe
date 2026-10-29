package io.github.touya0.nimbe.data.profiles

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Couvre-feu : plus d'accès de [start] à [end], en minutes depuis minuit ; il peut passer minuit (20:00 → 7:30). */
data class Curfew(val start: Int, val end: Int) {
    fun contains(minuteOfDay: Int): Boolean =
        if (start <= end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end

    val label: String get() = "${hour(start)} → ${hour(end)}"

    companion object {
        fun hour(minute: Int): String = "%d:%02d".format(minute / 60, minute % 60)
    }
}

/** Limites d'un profil Enfant. null : pas de limite. */
data class ChildLimits(val dailyMinutes: Int? = null, val curfew: Curfew? = null) {
    val any: Boolean get() = dailyMinutes != null || curfew != null
}

/**
 * Temps d'écran d'un profil, compté sur le stick. [day] : le jour compté (AAAA-MM-JJ) ; [usedMs] : temps déjà
 * passé ce jour-là ; [activeSince] : début de la séance en cours (profil actif et écran allumé), sinon null ;
 * [bonusUntil] : rab accordé par le PIN parent jusqu'à cette heure.
 */
data class Usage(val day: String = "", val usedMs: Long = 0, val activeSince: Long? = null, val bonusUntil: Long = 0)

/** Ce que le temps d'écran permet maintenant. */
sealed interface ScreenTimeVerdict {
    /** [remainingMs] : null sans limite du jour. */
    data class Allowed(val remainingMs: Long?) : ScreenTimeVerdict
    data class TimeUp(val usedMs: Long) : ScreenTimeVerdict
    data class Curfewed(val curfew: Curfew) : ScreenTimeVerdict

    val blocked: Boolean get() = this !is Allowed
}

/**
 * Règles du temps d'écran : compté localement, remis à zéro chaque jour à minuit. Une séance commencée
 * la veille ne compte que depuis minuit. Le rab du PIN parent lève le blocage, couvre-feu compris.
 */
object ScreenTime {

    /** Rab accordé par le PIN parent sur l'écran « Temps écoulé ». */
    const val BONUS_MS = 15 * 60_000L

    /** Choix proposés dans les réglages, en minutes ; null : sans limite. */
    val DailyChoices: List<Int?> = listOf(null, 30, 60, 90, 120, 180)

    /** Couvre-feux proposés dans les réglages ; null : aucun. */
    val CurfewChoices: List<Curfew?> = listOf(
        null,
        Curfew(19 * 60 + 30, 7 * 60 + 30),
        Curfew(20 * 60, 7 * 60 + 30),
        Curfew(20 * 60 + 30, 7 * 60 + 30),
        Curfew(21 * 60, 8 * 60),
    )

    private fun today(now: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    private fun startOfDay(now: Long, zone: ZoneId): Long = today(now, zone).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Le compte du jour : remis à zéro si [usage] date d'un autre jour. */
    fun rollOver(usage: Usage, now: Long, zone: ZoneId): Usage {
        val day = today(now, zone).toString()
        if (usage.day == day) return usage
        // Une séance en cours depuis hier repart de minuit.
        val since = usage.activeSince?.let { maxOf(it, startOfDay(now, zone)) }
        return Usage(day = day, usedMs = 0, activeSince = since, bonusUntil = usage.bonusUntil)
    }

    /** Temps passé aujourd'hui, séance en cours comprise. */
    fun usedToday(usage: Usage, now: Long, zone: ZoneId): Long {
        val current = rollOver(usage, now, zone)
        return current.usedMs + (current.activeSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
    }

    /** Début d'une séance : profil Enfant actif et écran allumé. */
    fun start(usage: Usage, now: Long, zone: ZoneId): Usage {
        val current = rollOver(usage, now, zone)
        return if (current.activeSince != null) current else current.copy(activeSince = now)
    }

    /** Fin de séance : le temps écoulé s'ajoute au compte du jour. */
    fun stop(usage: Usage, now: Long, zone: ZoneId): Usage {
        val current = rollOver(usage, now, zone)
        val since = current.activeSince ?: return current
        return current.copy(usedMs = current.usedMs + (now - since).coerceAtLeast(0), activeSince = null)
    }

    fun grantBonus(usage: Usage, now: Long): Usage = usage.copy(bonusUntil = now + BONUS_MS)

    fun verdict(limits: ChildLimits, usage: Usage, now: Long, zone: ZoneId): ScreenTimeVerdict {
        val bonusLeft = usage.bonusUntil - now
        if (bonusLeft > 0) return ScreenTimeVerdict.Allowed(bonusLeft)
        val time = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
        limits.curfew?.let { curfew ->
            if (curfew.contains(time.hour * 60 + time.minute)) return ScreenTimeVerdict.Curfewed(curfew)
        }
        val limitMs = limits.dailyMinutes?.let { it * 60_000L } ?: return ScreenTimeVerdict.Allowed(null)
        val used = usedToday(usage, now, zone)
        return if (used >= limitMs) ScreenTimeVerdict.TimeUp(used) else ScreenTimeVerdict.Allowed(limitMs - used)
    }

    /** « 1 h 10 », « 45 min », « moins d'une minute ». */
    fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes < 1 -> "moins d'une minute"
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0L -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${"%02d".format(minutes % 60)}"
        }
    }

    fun dailyLabel(minutes: Int?): String = minutes?.let { formatDuration(it * 60_000L) + " par jour" } ?: "Sans limite"

    /** L'heure de fin d'un couvre-feu, pour l'écran « C'est l'heure de dormir ». */
    fun curfewEnd(curfew: Curfew): LocalTime = LocalTime.of(curfew.end / 60, curfew.end % 60)
}
