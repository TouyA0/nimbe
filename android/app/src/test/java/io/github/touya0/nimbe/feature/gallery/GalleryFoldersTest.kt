package io.github.touya0.nimbe.feature.gallery

import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class GalleryFoldersTest {

    private val paris = ZoneId.of("Europe/Paris")
    private fun item(id: Long, path: String, year: Int = 2025, mime: String = "image/jpeg") =
        GalleryItem("p", id, path, mime, LocalDateTime.of(year, 6, 1, 12, 0).atZone(paris).toInstant().toEpochMilli(), 1, 0, 0, true)

    private val items = listOf(
        item(1, "F/Gallerie/Souvenirs/02 - Enfance/a.jpg", 2010),
        item(2, "F/Gallerie/Souvenirs/02 - Enfance/Noël/b.jpg", 2012),
        item(3, "F/Gallerie/Souvenirs/c.jpg", 2019),
        item(4, "F/Gallerie/synchro_tel/d.mp4", 2025, "video/mp4"),
        item(5, "F/Gallerie/synchro_tel/e.jpg", 2025),
    )

    @Test
    fun `le dossier de départ saute les couloirs`() {
        assertEquals("F/Gallerie", GalleryFolders.root(items))
        assertEquals("F/Gallerie/synchro_tel", GalleryFolders.root(items.filter { it.path.contains("synchro_tel") }))
        assertEquals("", GalleryFolders.root(listOf(item(1, "a.jpg"), item(2, "B/b.jpg"))))
    }

    @Test
    fun `sous-dossiers, photos directes et contenu complet`() {
        val children = GalleryFolders.children(items, "F/Gallerie")
        assertEquals(listOf("synchro_tel", "Souvenirs"), children.map { it.name })
        assertEquals(3, children[1].items.size)
        assertEquals(listOf("02 - Enfance"), GalleryFolders.children(items, "F/Gallerie/Souvenirs").map { it.name })
        assertEquals(listOf(3L), GalleryFolders.direct(items, "F/Gallerie/Souvenirs").map { it.fileId })
        assertEquals(setOf(1L, 2L), GalleryFolders.inside(items, "F/Gallerie/Souvenirs/02 - Enfance").map { it.fileId }.toSet())
        // « Souvenirs 2 » ne fait pas partie de « Souvenirs ».
        assertEquals(0, GalleryFolders.inside(listOf(item(9, "F/Souvenirs 2/x.jpg")), "F/Souvenirs").size)
    }

    @Test
    fun `années et vidéos récentes`() {
        assertEquals(listOf("2025", "2019", "2012", "2010"), GalleryFolders.byYear(items, paris).map { it.title })
        assertEquals(listOf(4L), GalleryFolders.recentVideos(items)?.items?.map { it.fileId })
        val timeline = GalleryTimeline.group(items.sortedByDescending { it.takenAt }, paris)
        assertEquals(listOf(2025, 2019, 2012, 2010), GalleryFolders.yearStarts(timeline).map { it.first })
    }

    @Test
    fun `fil d'Ariane et durées`() {
        assertEquals("DOSSIERS › SOUVENIRS", GalleryText.breadcrumb("F/Gallerie", "F/Gallerie/Souvenirs/02 - Enfance"))
        assertEquals("DOSSIERS", GalleryText.breadcrumb("F/Gallerie", "F/Gallerie/Souvenirs"))
        assertEquals("1:15", GalleryText.duration(75_000))
        assertEquals("1:02:03", GalleryText.duration(3_723_000))
    }
}
