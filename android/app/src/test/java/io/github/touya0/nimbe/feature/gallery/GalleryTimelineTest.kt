package io.github.touya0.nimbe.feature.gallery

import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class GalleryTimelineTest {

    private val paris = ZoneId.of("Europe/Paris")
    private fun item(id: Long, at: LocalDateTime, mime: String = "image/jpeg") =
        GalleryItem("p", id, "$id.jpg", mime, at.atZone(paris).toInstant().toEpochMilli(), 1, 0, 0, true)

    @Test
    fun `un mois par section, dans l'ordre reçu`() {
        val sections = GalleryTimeline.group(
            listOf(
                item(1, LocalDateTime.of(2025, 10, 18, 19, 42)),
                item(2, LocalDateTime.of(2025, 10, 2, 8, 0), "video/mp4"),
                // 0 h 30 à Paris le 1er octobre, encore septembre en UTC : on range à l'heure de la maison.
                item(3, LocalDateTime.of(2025, 10, 1, 0, 30)),
                item(4, LocalDateTime.of(2024, 9, 1, 12, 0)),
            ),
            paris,
        )
        assertEquals(listOf(YearMonth.of(2025, 10), YearMonth.of(2024, 9)), sections.map { it.month })
        assertEquals(2 to 1, sections[0].photos to sections[0].videos)
        assertEquals(emptyList<MonthSection>(), GalleryTimeline.group(emptyList(), paris))
    }

    @Test
    fun `titres des mois`() {
        assertEquals("Octobre 2025", GalleryText.month(YearMonth.of(2025, 10)))
        assertEquals("58 photos · 2 vidéos", GalleryText.monthCounts(58, 2))
        assertEquals("3 vidéos", GalleryText.monthCounts(0, 3))
    }

    @Test
    fun `position dans la grille, en-têtes compris`() {
        val sections = GalleryTimeline.group(
            listOf(
                item(1, LocalDateTime.of(2025, 10, 18, 19, 42)),
                item(2, LocalDateTime.of(2025, 10, 2, 8, 0)),
                item(3, LocalDateTime.of(2024, 9, 1, 12, 0)),
            ),
            paris,
        )
        // 0 titre, 1 « Octobre 2025 », 2 et 3 ses photos, 4 « Septembre 2024 », 5 sa photo.
        assertEquals(2, GalleryTimeline.gridIndex(sections, 1))
        assertEquals(3, GalleryTimeline.gridIndex(sections, 2))
        assertEquals(5, GalleryTimeline.gridIndex(sections, 3))
        assertEquals(null, GalleryTimeline.gridIndex(sections, 99))
    }

    @Test
    fun `date complète en français`() {
        val at = LocalDateTime.of(2025, 10, 18, 19, 42).atZone(paris).toInstant().toEpochMilli()
        assertEquals("Samedi 18 octobre 2025 · 19:42", GalleryText.fullDate(at, paris))
    }

    @Test
    fun `photos à l'année seule regroupées à part, dates aussi précises que connues`() {
        fun dated(id: Long, path: String, at: LocalDateTime) = GalleryItem("p", id, path, "image/jpeg", at.atZone(paris).toInstant().toEpochMilli(), 1, 0, 0, true)
        val items = listOf(
            dated(1, "S/2012-01-15 Ski/a.jpg", LocalDateTime.of(2012, 1, 15, 12, 0)),
            dated(2, "S/Noël 2012/b.jpg", LocalDateTime.of(2012, 1, 1, 0, 0)),
        )
        val sections = GalleryTimeline.group(items, paris)
        assertEquals(listOf(false, true), sections.map { it.yearOnly })
        assertEquals("2012 · mois inconnu", GalleryText.sectionTitle(sections[1]))
        assertEquals("Dimanche 15 janvier 2012", GalleryText.fullDate(items[0], paris))
        assertEquals("2012", GalleryText.fullDate(items[1], paris))
    }
}
