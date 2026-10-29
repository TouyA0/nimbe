package io.github.touya0.nimbe.feature.gallery

import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Un mois de la chronologie : « Octobre 2025 », ses photos et vidéos, des plus récentes aux plus anciennes. */
data class MonthSection(val month: YearMonth, val items: List<GalleryItem>) {
    val photos: Int get() = items.count { !it.isVideo }
    val videos: Int get() = items.count { it.isVideo }
}

/** Rangement de la galerie par mois, sans Android (testé sur le PC). */
object GalleryTimeline {

    /** [items] déjà triés du plus récent au plus ancien ; les mois suivent le même ordre, dans le fuseau de la maison. */
    fun group(items: List<GalleryItem>, zone: ZoneId = ZoneId.systemDefault()): List<MonthSection> {
        val sections = mutableListOf<MonthSection>()
        var month: YearMonth? = null
        var current = mutableListOf<GalleryItem>()
        for (item in items) {
            val itemMonth = YearMonth.from(Instant.ofEpochMilli(item.takenAt).atZone(zone))
            if (itemMonth != month) {
                month?.let { sections += MonthSection(it, current) }
                month = itemMonth
                current = mutableListOf()
            }
            current += item
        }
        month?.let { sections += MonthSection(it, current) }
        return sections
    }

    /**
     * Position d'une photo dans la grille, en-têtes compris : le titre de la galerie (0), puis pour
     * chaque mois son en-tête et ses photos. null si elle n'y est plus (supprimée entre-temps).
     */
    fun gridIndex(sections: List<MonthSection>, fileId: Long): Int? {
        var position = 1
        for (section in sections) {
            position++
            val inside = section.items.indexOfFirst { it.fileId == fileId }
            if (inside >= 0) return position + inside
            position += section.items.size
        }
        return null
    }
}
