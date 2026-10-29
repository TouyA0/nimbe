package io.github.touya0.nimbe.feature.gallery

import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import io.github.touya0.nimbe.data.nextcloud.WebDav
import java.time.Instant
import java.time.ZoneId

/** Un dossier de la galerie, avec toutes les photos qu'il contient, sous-dossiers compris. */
data class GalleryFolder(val path: String, val items: List<GalleryItem>) {
    val name: String get() = path.substringAfterLast('/').ifEmpty { "Racine du compte" }
    val photos: Int get() = items.count { !it.isVideo }
    val videos: Int get() = items.count { it.isVideo }

    /** Couverture : la photo la plus récente qui a un aperçu, sinon le premier élément. */
    val cover: GalleryItem get() = items.firstOrNull { !it.isVideo && it.hasPreview } ?: items.first()
}

/** Arborescence des dossiers et regroupements par année, calculés sur la TV (testés sur le PC). */
object GalleryFolders {

    /**
     * Le dossier de départ : le plus profond commun à toutes les photos (« Fichiers Quentin/Gallerie »
     * quand seuls ses sous-dossiers sont montrés). Ainsi la première page montre tout de suite les vrais
     * dossiers, sans couloir de dossiers qui ne contiennent qu'un dossier.
     */
    fun root(items: List<GalleryItem>): String {
        val parents = items.filter { !it.path.startsWith(WebDav.DAV_PREFIX) }.map { it.path.substringBeforeLast('/', "").split('/') }
        if (parents.isEmpty()) return ""
        var common = parents.first()
        for (parts in parents) {
            val same = common.zip(parts).takeWhile { (a, b) -> a == b }.size
            common = common.take(same)
            if (common.isEmpty()) break
        }
        return common.joinToString("/")
    }

    /**
     * Sous-dossiers directs de [path], chacun avec toutes ses photos. [byName] : dans l'ordre des noms, chiffres
     * compris (« 01 - … », « 02 - … », « 10 - … »), l'ordre qu'on a choisi en les nommant ; sinon du plus
     * récemment rempli au plus ancien (rangée de « Pour toi »).
     */
    fun children(items: List<GalleryItem>, path: String, byName: Boolean = false): List<GalleryFolder> {
        val prefix = if (path.isEmpty()) "" else "$path/"
        return items
            .filter { it.path.startsWith(prefix) && it.path.removePrefix(prefix).contains('/') }
            .groupBy { prefix + it.path.removePrefix(prefix).substringBefore('/') }
            .map { (child, photos) -> GalleryFolder(child, photos.sortedByDescending { it.takenAt }) }
            .let { folders ->
                if (byName) folders.sortedWith(compareBy(NaturalOrder) { it.name }) else folders.sortedByDescending { it.items.first().takenAt }
            }
    }

    /** Comparaison « naturelle » : « 2 - x » avant « 10 - x », sans tenir compte des majuscules ni des accents. */
    val NaturalOrder: Comparator<String> = Comparator { a, b ->
        val left = CHUNKS.findAll(normalize(a)).map { it.value }.toList()
        val right = CHUNKS.findAll(normalize(b)).map { it.value }.toList()
        for (i in 0 until minOf(left.size, right.size)) {
            val x = left[i]
            val y = right[i]
            val order = if (x[0].isDigit() && y[0].isDigit()) {
                val (nx, ny) = x.trimStart('0') to y.trimStart('0')
                nx.length.compareTo(ny.length).takeIf { it != 0 } ?: nx.compareTo(ny)
            } else {
                x.compareTo(y)
            }
            if (order != 0) return@Comparator order
        }
        left.size.compareTo(right.size)
    }

    private val CHUNKS = Regex("""\d+|\D+""")
    private val MARKS = Regex("""\p{M}""")

    private fun normalize(text: String): String = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(MARKS, "")

    /** Photos rangées directement dans [path], pas dans ses sous-dossiers. */
    fun direct(items: List<GalleryItem>, path: String): List<GalleryItem> =
        items.filter { it.path.substringBeforeLast('/', "") == path }

    /** Toutes les photos sous [path], sous-dossiers compris. */
    fun inside(items: List<GalleryItem>, path: String): List<GalleryItem> {
        val prefix = if (path.isEmpty()) "" else "$path/"
        return items.filter { it.path.startsWith(prefix) }
    }

    /** Une collection par année, de la plus récente à la plus ancienne, pour sauter loin d'un coup. */
    fun byYear(items: List<GalleryItem>, zone: ZoneId = ZoneId.systemDefault()): List<GalleryCollection> = items
        .groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).year }
        .toSortedMap(compareByDescending { it })
        .map { (year, photos) -> GalleryCollection("ANNÉE", "$year", photos.sortedByDescending { it.takenAt }) }

    /** Les dernières vidéos arrivées sur le serveur (plus grand identifiant = plus récent). */
    fun recentVideos(items: List<GalleryItem>, count: Int = 30): GalleryCollection? {
        val videos = items.filter { it.isVideo }.sortedByDescending { it.fileId }.take(count)
        return if (videos.isEmpty()) null else GalleryCollection("VIDÉOS", "Vidéos récentes", videos)
    }

    /** Années présentes dans une chronologie, avec la position du premier mois de chacune. */
    fun yearStarts(sections: List<MonthSection>): List<Pair<Int, MonthSection>> =
        sections.groupBy { it.month.year }.map { (year, months) -> year to months.first() }
}
