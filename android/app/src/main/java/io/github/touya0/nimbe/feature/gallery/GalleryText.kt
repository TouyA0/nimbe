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

    /** Espace insécable fine entre les milliers, comme en français : « 4 812 ». */
    fun count(n: Int, one: String, many: String): String {
        val digits = n.toString().reversed().chunked(3).joinToString(" ").reversed()
        return "$digits ${if (n <= 1) one else many}"
    }
}
