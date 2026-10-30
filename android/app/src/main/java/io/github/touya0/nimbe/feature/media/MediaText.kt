package io.github.touya0.nimbe.feature.media

import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Textes affichés pour un contenu Jellyfin, en français. */
object MediaText {

    /** « 2 h 08 », « 52 min ». */
    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h %02d".format(minutes % 60)
    }

    /** « 2026 · 2 h 08 · Aventure, Drame » ; pour une série : « 3 saisons · Thriller · 2024 – 2026 ». */
    fun meta(item: MediaItem): String = listOfNotNull(
        item.childCount?.takeIf { item.kind == ItemKind.Series }?.let { if (it > 1) "$it saisons" else "1 saison" },
        years(item),
        item.runtimeMinutes?.takeIf { item.kind != ItemKind.Series && it > 0 }?.let(::duration),
        item.genres.take(2).joinToString(", ").ifEmpty { null },
    ).joinToString(" · ")

    private fun years(item: MediaItem): String? {
        val start = item.year ?: return null
        val end = item.endYear
        return if (item.kind == ItemKind.Series && end != null && end != start) "$start – $end" else "$start"
    }

    /** « FR-12 » devient « 12+ », « TP » devient « Tous publics », « PG-13 » reste tel quel. */
    fun rating(raw: String?): String? {
        val value = raw?.substringAfter("FR-")?.trim()?.ifEmpty { null } ?: return null
        return when {
            value == "TP" || value == "U" -> "Tous publics"
            value.all(Char::isDigit) -> "$value+"
            else -> value
        }
    }

    /** « 7,8 », à la française ; rien sans note. */
    fun score(value: Float?): String? = value?.takeIf { it > 0f }?.let { String.format(Locale.FRENCH, "%.1f", it) }

    /** « Fin à 22:14 » si on lance (ou reprend) le film maintenant. */
    fun endsAt(item: MediaItem, now: LocalTime = LocalTime.now()): String? {
        val minutes = item.remainingMinutes?.takeIf { it > 0 } ?: return null
        return "Fin à " + now.plusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    /** « AJOUTÉ HIER », « AJOUTÉ IL Y A 3 JOURS », « AJOUTÉ LE 2 OCTOBRE ». */
    fun added(at: Instant?, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String? {
        val day = at?.atZone(zone)?.toLocalDate() ?: return null
        val days = ChronoUnit.DAYS.between(day, today)
        val text = when {
            days <= 0 -> "ajouté aujourd'hui"
            days == 1L -> "ajouté hier"
            days < 7 -> "ajouté il y a $days jours"
            else -> "ajouté le " + day.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))
        }
        return text.uppercase(Locale.FRENCH)
    }

    /** Titre principal : la série pour un épisode. */
    fun title(item: MediaItem): String = if (item.kind == ItemKind.Episode) item.seriesName ?: item.name else item.name

    /** Sous la carte « Continuer à regarder » : « Brumes · S02 É04 » ou le titre du film. */
    fun cardLabel(item: MediaItem): String = when (item.kind) {
        ItemKind.Episode -> listOfNotNull(item.seriesName, item.episodeCode?.replace(" · ", " ")).joinToString(" · ")
        else -> item.name
    }

    /** « Saison 2 · Épisode 4 · Ce que cache la forêt ». */
    fun episodeLine(item: MediaItem): String = listOfNotNull(
        item.seasonNumber?.let { "Saison $it" },
        item.episodeNumber?.let { "Épisode $it" },
        item.name,
    ).joinToString(" · ")

    /** « 18 min restantes » pendant la lecture, sinon la durée, « vu » une fois terminé. */
    fun progressLine(item: MediaItem): String? = when {
        item.resumable -> item.remainingMinutes?.let { "${duration(it.coerceAtLeast(1))} restantes" }
        item.played -> listOfNotNull(item.runtimeMinutes?.let(::duration), "vu").joinToString(" · ")
        else -> item.runtimeMinutes?.takeIf { it > 0 }?.let(::duration)
    }

    /** « Arrêté à 1 h 21 » sur la fiche. */
    fun stoppedAt(item: MediaItem): String = "Arrêté à " + duration((item.positionTicks / MediaItem.TICKS_PER_MINUTE).toInt())
}
