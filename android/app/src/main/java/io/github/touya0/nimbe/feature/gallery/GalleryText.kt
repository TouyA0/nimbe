package io.github.touya0.nimbe.feature.gallery

/** Textes de la galerie, sans Android (testés sur le PC). */
object GalleryText {

    /** « Nextcloud · 4 812 photos · 236 vidéos · synchronisé il y a 3 min ». */
    fun summary(photos: Int, videos: Int, syncedAt: Long?, now: Long): String = buildList {
        add("Nextcloud")
        add(count(photos, "photo", "photos"))
        if (videos > 0) add(count(videos, "vidéo", "vidéos"))
        syncedAt?.let { add("synchronisé ${ago(it, now)}") }
    }.joinToString(" · ")

    /** « à l'instant », « il y a 3 min », « il y a 2 h », « il y a 4 jours ». */
    fun ago(at: Long, now: Long): String {
        val minutes = ((now - at) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a $minutes min"
            minutes < 24 * 60 -> "il y a ${minutes / 60} h"
            minutes < 48 * 60 -> "hier"
            else -> "il y a ${minutes / (24 * 60)} jours"
        }
    }

    /** « Samedi 18 octobre 2025 · 19:42 », à l'heure de la maison. */
    fun fullDate(at: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val text = java.time.Instant.ofEpochMilli(at).atZone(zone).format(FULL_DATE)
        return text.replaceFirstChar { it.uppercase() }
    }

    /**
     * Date d'une photo, aussi précise qu'elle est connue : « Samedi 18 octobre 2025 · 19:42 »,
     * « Vendredi 2 octobre 2015 » (date d'un dossier), « Mai 2017 », « 2012 ».
     */
    fun fullDate(item: io.github.touya0.nimbe.data.nextcloud.GalleryItem, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val at = java.time.Instant.ofEpochMilli(item.takenAt).atZone(zone)
        return when (io.github.touya0.nimbe.data.nextcloud.FileNameDate.precisionOf(item, zone)) {
            io.github.touya0.nimbe.data.nextcloud.DatePrecision.Exact -> fullDate(item.takenAt, zone)
            io.github.touya0.nimbe.data.nextcloud.DatePrecision.Day -> at.format(DAY_DATE).replaceFirstChar { it.uppercase() }
            io.github.touya0.nimbe.data.nextcloud.DatePrecision.Month -> month(java.time.YearMonth.from(at))
            io.github.touya0.nimbe.data.nextcloud.DatePrecision.Year -> "${at.year}"
        }
    }

    /** Date courte sur une case de la grille : « 18 oct. 2025 », « oct. 2015 », « 2012 ». */
    fun shortDate(item: io.github.touya0.nimbe.data.nextcloud.GalleryItem, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val at = java.time.Instant.ofEpochMilli(item.takenAt).atZone(zone)
        return when (io.github.touya0.nimbe.data.nextcloud.FileNameDate.precisionOf(item, zone)) {
            io.github.touya0.nimbe.data.nextcloud.DatePrecision.Exact, io.github.touya0.nimbe.data.nextcloud.DatePrecision.Day -> at.format(SHORT_DATE)
            io.github.touya0.nimbe.data.nextcloud.DatePrecision.Month -> at.format(SHORT_MONTH)
            io.github.touya0.nimbe.data.nextcloud.DatePrecision.Year -> "${at.year}"
        }
    }

    /** En-tête d'une section de la chronologie : « Octobre 2025 », ou « 2012 · mois inconnu ». */
    fun sectionTitle(section: io.github.touya0.nimbe.feature.gallery.MonthSection): String =
        if (section.yearOnly) "${section.month.year} · mois inconnu" else month(section.month)

    private val FULL_DATE = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy · HH:mm", java.util.Locale.FRENCH)
    private val DAY_DATE = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH)
    private val SHORT_DATE = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.FRENCH)
    private val SHORT_MONTH = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.FRENCH)

    /** Rappel des touches en bas de la visionneuse. */
    fun viewerHints(video: Boolean, slideshow: Boolean): String = listOf(
        when {
            video -> "OK : pause ou lecture"
            slideshow -> "OK : arrêter le diaporama"
            else -> "OK : lancer le diaporama"
        },
        "Gauche / droite : précédente ou suivante",
        "Retour : quitter",
    ).joinToString("   ·   ")

    /** Raison d'un échec de lecture vidéo, d'après le code Media3 (PlaybackException). */
    fun playbackError(code: Int): String = when (code) {
        2001, 2002 -> "Nextcloud ne répond pas"
        2004 -> "Nextcloud refuse l'accès à ce fichier"
        2005 -> "le fichier n'existe plus"
        3001, 3002, 3003, 3004 -> "format de fichier illisible"
        4001, 4002, 4003, 4004, 4005 -> "ce format vidéo n'est pas pris en charge par la TV"
        else -> "erreur $code"
    }

    /** « 1:15 », « 1:02:03 » : durée d'une vidéo. */
    fun duration(ms: Long): String {
        val total = ms / 1000
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val seconds = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    /** « SOUVENIRS › 02 - ENFANCE » : les dossiers parents d'un dossier ouvert, depuis le dossier de départ. */
    fun breadcrumb(root: String, path: String): String {
        val relative = if (root.isNotEmpty() && path.startsWith("$root/")) path.removePrefix("$root/") else path
        val parents = relative.split('/').dropLast(1).filter { it.isNotEmpty() }
        return (listOf("DOSSIERS") + parents.map { it.uppercase() }).joinToString(" › ")
    }

    /** « Octobre 2025 ». */
    fun month(month: java.time.YearMonth): String = "${MONTHS[month.monthValue - 1]} ${month.year}"

    /** « 58 photos · 2 vidéos », « 3 vidéos ». */
    fun monthCounts(photos: Int, videos: Int): String = listOfNotNull(
        count(photos, "photo", "photos").takeIf { photos > 0 },
        count(videos, "vidéo", "vidéos").takeIf { videos > 0 },
    ).joinToString(" · ")

    private val MONTHS = listOf(
        "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre",
    )

    /** Espace insécable fine entre les milliers, comme en français : « 4 812 ». */
    fun count(n: Int, one: String, many: String): String {
        val digits = n.toString().reversed().chunked(3).joinToString(" ").reversed()
        return "$digits ${if (n <= 1) one else many}"
    }
}
