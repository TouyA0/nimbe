package io.github.touya0.nimbe.feature.streaming

import io.github.touya0.nimbe.data.streaming.StreamingService
import io.github.touya0.nimbe.data.streaming.StreamingTitle
import io.github.touya0.nimbe.data.streaming.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreamingTextTest {

    @Test
    fun classification() {
        assertEquals("Tous publics", StreamingText.rating("TP"))
        assertEquals("12+", StreamingText.rating("12"))
        assertNull(StreamingText.rating(""))
    }

    @Test
    fun `ligne de détails d'un film et d'une série`() {
        val film = StreamingTitle("tm1", TitleKind.Movie, "Zootopie 2", year = 2025, runtimeMinutes = 108, genres = listOf("ani", "cmy", "drm"))
        assertEquals("2025 · 1 h 48 · Animation, Comédie", StreamingText.meta(film))
        val show = StreamingTitle("ts1", TitleKind.Show, "The Last of Us", year = 2023, seasons = 2, runtimeMinutes = 55)
        assertEquals("2 saisons · 2023", StreamingText.meta(show))
    }

    @Test
    fun `texte lu par TalkBack`() {
        val film = StreamingTitle("tm1", TitleKind.Movie, "Vice-versa 2")
        assertEquals("Vice-versa 2, film, sur Disney+ et HBO Max", StreamingText.spoken(film, listOf(StreamingService.Disney, StreamingService.HboMax)))
    }

    @Test
    fun `ouvert il y a combien de jours, en jours du calendrier`() {
        val zone = ZoneId.of("Europe/Paris")
        val today = LocalDate.of(2026, 10, 29)
        val lateYesterday = today.minusDays(1).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Ouvert hier", openedLabel(lateYesterday, today, zone))
        assertEquals("Ouvert aujourd'hui", openedLabel(today.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), today, zone))
        assertEquals("Ouvert il y a 4 jours", openedLabel(today.minusDays(4).atStartOfDay(zone).toInstant().toEpochMilli(), today, zone))
    }
}
