package io.github.touya0.nimbe.feature.player

import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class PlayerRulesTest {

    private val minute = MediaItem.TICKS_PER_MINUTE
    private fun film(positionMinutes: Long) =
        MediaItem(id = "m", kind = ItemKind.Movie, name = "Sable rouge", runTimeTicks = 100 * minute, positionTicks = positionMinutes * minute)

    @Test
    fun `reprendre ou recommencer seulement entre 2 min et 95 pourcents`() {
        assertFalse(ResumeRule.shouldAsk(film(0)))
        assertFalse(ResumeRule.shouldAsk(film(1)))
        assertTrue(ResumeRule.shouldAsk(film(2)))
        assertTrue(ResumeRule.shouldAsk(film(95)))
        assertFalse(ResumeRule.shouldAsk(film(96)))
        assertEquals(0L, ResumeRule.direct(film(1)).startTicks)
        assertEquals(50 * minute, ResumeRule.resume(film(50)).startTicks)
    }

    @Test
    fun temps() {
        assertEquals("1:21:04", PlayerText.time(((1 * 60 + 21) * 60 + 4) * 1000L))
        assertEquals("47:12", PlayerText.time((47 * 60 + 12) * 1000L))
        assertEquals("0:00", PlayerText.time(-5))
    }

    @Test
    fun `moment de la dernière lecture`() {
        val today = LocalDate.of(2026, 10, 26)
        assertEquals("hier soir", PlayerText.whenPlayed(Instant.parse("2026-10-25T21:10:00Z"), today, ZoneOffset.UTC))
        assertEquals("aujourd'hui", PlayerText.whenPlayed(Instant.parse("2026-10-26T09:00:00Z"), today, ZoneOffset.UTC))
        assertEquals("le 3 octobre", PlayerText.whenPlayed(Instant.parse("2026-10-03T21:00:00Z"), today, ZoneOffset.UTC))
    }
}
