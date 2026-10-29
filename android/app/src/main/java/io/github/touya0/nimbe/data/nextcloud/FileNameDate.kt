package io.github.touya0.nimbe.data.nextcloud

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Date de prise de vue lue dans le nom du fichier, quand Nextcloud n'a pas lu l'EXIF :
 * « IMG_20220730_190614.jpg », « PXL_20240102_101500123.jpg », « IMG-20220730-WA0012.jpg »,
 * « 2019-07-14_famille_12.jpg ». L'heure est celle de la maison ([zone]) ; sans heure, midi.
 * Une date improbable (avant 1990, dans le futur) est ignorée : ce n'était pas une date.
 */
object FileNameDate {

    private val pattern = Regex(
        """(?<!\d)((?:19|20)\d{2})[-_.]?(0[1-9]|1[0-2])[-_.]?(0[1-9]|[12]\d|3[01])""" +
            """(?:[-_ T.]?([01]\d|2[0-3])[-_.:h]?([0-5]\d)[-_.:m]?([0-5]\d))?""",
    )

    fun parse(fileName: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        val match = pattern.find(fileName) ?: return null
        val g = match.groupValues
        val at = try {
            LocalDateTime.of(
                g[1].toInt(), g[2].toInt(), g[3].toInt(),
                g[4].toIntOrNull() ?: 12, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0,
            ).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            // 31 février et autres dates qui n'existent pas.
            return null
        }
        return at.takeIf { it in MIN until now + DAY_MS }
    }

    private const val DAY_MS = 86_400_000L
    /** 1er janvier 1990. */
    private const val MIN = 631_152_000_000L
}
