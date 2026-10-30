package io.github.touya0.nimbe.data.nextcloud

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId

/** Jusqu'où une date est connue : à la seconde, au jour, au mois, ou seulement l'année. */
enum class DatePrecision { Exact, Day, Month, Year }

/** Une date trouvée dans un nom, avec sa précision. */
data class NameDate(val millis: Long, val precision: DatePrecision)

/**
 * Date d'une photo lue dans les noms, quand Nextcloud ne connaît pas sa date de prise de vue :
 * d'abord le nom du fichier (« IMG_20220730_190614.jpg », « 2017-05_video_3.VOB »), puis les dossiers
 * qui la contiennent, du plus proche au plus lointain (« 2015-10-02 Jeux enfants », « Noël 2012 »).
 * Une plage (« Enfance (2006 - 2018) ») ne donne pas de date : on regarde le dossier au-dessus.
 * L'heure est celle de la maison ([zone]). Sans heure, midi ; mois seul, le 1er ; année seule, le 1er janvier.
 * Une date improbable (avant 1900, dans le futur) est ignorée : ce n'était pas une date.
 */
object FileNameDate {

    private val fullDate = Regex(
        """(?<!\d)((?:19|20)\d{2})[-_.]?(0[1-9]|1[0-2])[-_.]?(0[1-9]|[12]\d|3[01])""" +
            """(?:[-_ T.]?([01]\d|2[0-3])[-_.:h]?([0-5]\d)[-_.:m]?([0-5]\d))?""",
    )
    private val yearMonth = Regex("""(?<!\d)((?:19|20)\d{2})[-_.](0[1-9]|1[0-2])(?!\d)""")
    private val year = Regex("""(?<!\d)(?:19|20)\d{2}(?!\d)""")

    /** Date dans le nom d'un fichier, en millisecondes (null : aucune). */
    fun parse(fileName: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? =
        fromName(fileName, allowYear = false, now, zone)?.millis

    /**
     * Date d'un élément d'après son chemin : le nom du fichier, puis chaque dossier en remontant.
     * Un nombre de quatre chiffres seul dans un nom de fichier n'est pas une année (compteur « IMG_2015 ») ;
     * dans un nom de dossier, si (« Noël 2012 »), à condition d'y être seul.
     */
    fun fromPath(path: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): NameDate? {
        fromName(WebDav.displayName(path), allowYear = false, now, zone)?.let { return it }
        val folders = path.split('/').dropLast(1).filter { it.isNotEmpty() && !it.startsWith("@") }
        for (folder in folders.asReversed()) {
            fromName(folder, allowYear = true, now, zone)?.let { return it }
        }
        return null
    }

    /**
     * Précision de la date de [item] : celle du nom quand c'est de là qu'elle vient, sinon exacte
     * (date de prise de vue lue par le serveur, ou date d'envoi).
     */
    fun precisionOf(item: GalleryItem, zone: ZoneId = ZoneId.systemDefault()): DatePrecision {
        val found = fromPath(item.path, Long.MAX_VALUE / 2, zone) ?: return DatePrecision.Exact
        return if (found.millis == item.takenAt) found.precision else DatePrecision.Exact
    }

    fun fromName(name: String, allowYear: Boolean, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): NameDate? {
        fullDate.find(name)?.groupValues?.let { g ->
            val exact = g[4].isNotEmpty()
            date(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toIntOrNull() ?: 12, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0, zone)
                ?.let { return valid(it, if (exact) DatePrecision.Exact else DatePrecision.Day, now) }
        }
        yearMonth.find(name)?.groupValues?.let { g ->
            date(g[1].toInt(), g[2].toInt(), 1, 0, 0, 0, zone)?.let { return valid(it, DatePrecision.Month, now) }
        }
        if (allowYear) {
            val years = year.findAll(name).toList()
            if (years.size == 1) date(years.single().value.toInt(), 1, 1, 0, 0, 0, zone)?.let { return valid(it, DatePrecision.Year, now) }
        }
        return null
    }

    private fun date(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, zone: ZoneId): Long? = try {
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()
    } catch (_: DateTimeException) {
        // 31 février et autres dates qui n'existent pas.
        null
    }

    private fun valid(millis: Long, precision: DatePrecision, now: Long): NameDate? =
        NameDate(millis, precision).takeIf { millis in MIN until now + DAY_MS }

    private const val DAY_MS = 86_400_000L

    /** 1er janvier 1900. */
    private const val MIN = -2_208_988_800_000L
}
