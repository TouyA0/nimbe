package io.github.touya0.nimbe.feature.gallery

import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class GalleryMemoriesTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 10, 29)
    private fun item(id: Long, at: LocalDate, path: String = "Photos/$id.jpg") =
        GalleryItem("p", id, path, "image/jpeg", LocalDateTime.of(at, java.time.LocalTime.NOON).atZone(paris).toInstant().toEpochMilli(), 1, 0, 0, true)

    @Test
    fun `souvenirs des années passées autour de la même date`() {
        val items = listOf(
            item(1, LocalDate.of(2025, 10, 27)), item(2, LocalDate.of(2025, 10, 29)), item(3, LocalDate.of(2025, 11, 1)),
            item(4, LocalDate.of(2023, 10, 30)), item(5, LocalDate.of(2023, 10, 30)), item(6, LocalDate.of(2023, 10, 31)),
            // Trop loin de la date (8 jours) : pas un souvenir.
            item(7, LocalDate.of(2025, 11, 6)),
            // Cette année : pas un souvenir non plus.
            item(8, LocalDate.of(2026, 10, 29)),
            // Une seule photo cette année-là : ignorée.
            item(9, LocalDate.of(2020, 10, 29)),
        )
        val memories = GalleryMemories.onThisDay(items, today, paris)
        assertEquals(listOf("IL Y A UN AN", "IL Y A 3 ANS"), memories.map { it.kicker })
        assertEquals(listOf(3L, 2L, 1L), memories[0].items.map { it.fileId })
        assertEquals("Octobre 2025", memories[0].title)
    }

    @Test
    fun `ce mois-ci, ajouts récents et dossiers`() {
        val items = listOf(
            item(10, LocalDate.of(2026, 10, 2), "A/B/x.jpg"),
            item(30, LocalDate.of(2019, 5, 1), "A/C/y.jpg"),
            item(20, LocalDate.of(2026, 9, 2), "A/B/z.jpg"),
        )
        assertEquals(listOf(10L), GalleryMemories.thisMonth(items, today, paris)?.items?.map { it.fileId })
        assertNull(GalleryMemories.thisMonth(items, LocalDate.of(2026, 8, 1), paris))
        assertEquals(listOf(30L, 20L, 10L), GalleryMemories.recentlyAdded(items)?.items?.map { it.fileId })
        val folders = GalleryMemories.folders(items)
        assertEquals(listOf("B", "C"), folders.map { it.title })
        assertEquals("A", folders[0].kicker)
    }
}
