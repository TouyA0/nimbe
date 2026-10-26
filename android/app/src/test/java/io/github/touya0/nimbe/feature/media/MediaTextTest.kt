package io.github.touya0.nimbe.feature.media

import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MediaTextTest {

    private val minute = MediaItem.TICKS_PER_MINUTE

    @Test
    fun durées() {
        assertEquals("52 min", MediaText.duration(52))
        assertEquals("2 h 08", MediaText.duration(128))
    }

    @Test
    fun `ligne de détails d'un film et d'une série`() {
        val film = MediaItem(id = "m", kind = ItemKind.Movie, name = "Sable rouge", year = 2026, runTimeTicks = 128 * minute, genres = listOf("Aventure", "Drame", "Western"))
        assertEquals("2026 · 2 h 08 · Aventure, Drame", MediaText.meta(film))
        val series = MediaItem(id = "s", kind = ItemKind.Series, name = "Brumes", year = 2024, endYear = 2026, childCount = 3, genres = listOf("Thriller"))
        assertEquals("3 saisons · 2024 – 2026 · Thriller", MediaText.meta(series))
    }

    @Test
    fun classifications() {
        assertEquals("12+", MediaText.rating("FR-12"))
        assertEquals("PG-13", MediaText.rating("PG-13"))
        assertNull(MediaText.rating(null))
    }

    @Test
    fun `date d'ajout relative`() {
        val today = LocalDate.of(2026, 10, 26)
        fun at(day: String) = Instant.parse("${day}T12:00:00Z")
        assertEquals("AJOUTÉ AUJOURD'HUI", MediaText.added(at("2026-10-26"), today, ZoneOffset.UTC))
        assertEquals("AJOUTÉ HIER", MediaText.added(at("2026-10-25"), today, ZoneOffset.UTC))
        assertEquals("AJOUTÉ IL Y A 3 JOURS", MediaText.added(at("2026-10-23"), today, ZoneOffset.UTC))
        assertEquals("AJOUTÉ LE 2 OCTOBRE", MediaText.added(at("2026-10-02"), today, ZoneOffset.UTC))
    }

    @Test
    fun `progression et libellés d'épisode`() {
        val episode = MediaItem(
            id = "e", kind = ItemKind.Episode, name = "Ce que cache la forêt", seriesName = "Brumes",
            seasonNumber = 2, episodeNumber = 4, runTimeTicks = 52 * minute, positionTicks = 34 * minute,
        )
        assertEquals("18 min restantes", MediaText.progressLine(episode))
        assertEquals("Brumes · S02 É04", MediaText.cardLabel(episode))
        assertEquals("Saison 2 · Épisode 4 · Ce que cache la forêt", MediaText.episodeLine(episode))
        assertEquals("Arrêté à 34 min", MediaText.stoppedAt(episode))
        assertEquals("52 min · vu", MediaText.progressLine(episode.copy(played = true, positionTicks = 0)))
    }
}
