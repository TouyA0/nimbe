package io.github.touya0.nimbe.feature.gallery

import io.github.touya0.nimbe.data.nextcloud.DatePrecision
import io.github.touya0.nimbe.data.nextcloud.FileNameDate
import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import io.github.touya0.nimbe.data.nextcloud.WebDav
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Un ensemble de photos à ouvrir : un souvenir, un dossier, les ajouts récents.
 * [kicker] : la petite ligne en capitales au-dessus du titre (« IL Y A UN AN »).
 */
data class GalleryCollection(val kicker: String, val title: String, val items: List<GalleryItem>) {
    /** Couverture : la première photo qui a un aperçu, sinon le premier élément. */
    val cover: GalleryItem get() = items.firstOrNull { !it.isVideo && it.hasPreview } ?: items.first()

    val photos: Int get() = items.count { !it.isVideo }
    val videos: Int get() = items.count { it.isVideo }
}

/** Souvenirs et regroupements de la page « Pour toi », calculés sur la TV à partir de l'index (testés sur le PC). */
object GalleryMemories {

    /**
     * « Il y a un an », « Il y a 3 ans » : les photos prises autour de la même date ([windowDays] jours
     * avant ou après) les années précédentes. Un souvenir par année, de la plus récente à la plus ancienne ;
     * une année avec trop peu de photos ([minItems]) est ignorée.
     */
    fun onThisDay(
        items: List<GalleryItem>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        windowDays: Int = 3,
        minItems: Int = 3,
    ): List<GalleryCollection> = items
        // Un jour précis est nécessaire : une photo datée par un dossier « Mai 2017 » ou « 2012 » n'est pas un souvenir du jour.
        .filter { FileNameDate.precisionOf(it, zone).let { p -> p == DatePrecision.Exact || p == DatePrecision.Day } }
        .groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
        .flatMap { (date, photos) ->
            val years = today.year - date.year
            if (years < 1) return@flatMap emptyList()
            // Même jour, l'année de la photo : la distance ne dépend pas des années bissextiles.
            val sameDay = runCatching { today.withYear(date.year) }.getOrElse { today.minusDays(1).withYear(date.year) }
            if (abs(ChronoUnit.DAYS.between(sameDay, date)) > windowDays) emptyList() else photos.map { years to it }
        }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size >= minItems }
        .toSortedMap()
        .map { (years, photos) ->
            val month = YearMonth.from(Instant.ofEpochMilli(photos.first().takenAt).atZone(zone))
            GalleryCollection(
                kicker = if (years == 1) "IL Y A UN AN" else "IL Y A $years ANS",
                title = GalleryText.month(month),
                items = photos.sortedByDescending { it.takenAt },
            )
        }

    /** Les photos du mois en cours, null s'il n'y en a pas. */
    fun thisMonth(items: List<GalleryItem>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): GalleryCollection? {
        val month = YearMonth.from(today)
        val photos = items.filter {
            YearMonth.from(Instant.ofEpochMilli(it.takenAt).atZone(zone)) == month && FileNameDate.precisionOf(it, zone) != DatePrecision.Year
        }
        return if (photos.isEmpty()) null else GalleryCollection("CE MOIS-CI", GalleryText.month(month), photos)
    }

    /**
     * Les derniers fichiers arrivés sur le serveur. Nextcloud numérote les fichiers dans l'ordre où il
     * les découvre : le plus grand identifiant est le plus récent, quelle que soit la date de la photo.
     */
    fun recentlyAdded(items: List<GalleryItem>, count: Int = 40): GalleryCollection? {
        val recent = items.sortedByDescending { it.fileId }.take(count)
        return if (recent.isEmpty()) null else GalleryCollection("AJOUTÉES RÉCEMMENT", "Dernières arrivées", recent)
    }

    /**
     * Les dossiers comme des albums, pour qui n'utilise pas les albums de Nextcloud Photos :
     * un par dossier qui contient directement des photos, du plus récemment rempli au plus ancien.
     */
    fun folders(items: List<GalleryItem>): List<GalleryCollection> = items
        .filter { !it.path.startsWith(WebDav.DAV_PREFIX) }
        .groupBy { it.path.substringBeforeLast('/', "") }
        .map { (folder, photos) ->
            GalleryCollection(
                kicker = folder.substringBeforeLast('/', "").substringAfterLast('/').uppercase(),
                title = folder.substringAfterLast('/').ifEmpty { "Racine du compte" },
                items = photos.sortedByDescending { it.takenAt },
            )
        }
        .sortedByDescending { it.items.first().takenAt }
}
