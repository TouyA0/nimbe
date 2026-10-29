package io.github.touya0.nimbe.data.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScreenTimeTest {

    private val zone = ZoneId.of("Europe/Paris")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val min = 60_000L

    @Test
    fun `une séance s'ajoute au compte du jour`() {
        var usage = ScreenTime.start(Usage(), at(29, 16), zone)
        assertEquals(30 * min, ScreenTime.usedToday(usage, at(29, 16, 30), zone))
        usage = ScreenTime.stop(usage, at(29, 16, 45), zone)
        assertEquals(45 * min, usage.usedMs)
        assertEquals(null, usage.activeSince)
        // Une nouvelle séance repart du compte du jour.
        usage = ScreenTime.start(usage, at(29, 18), zone)
        assertEquals(55 * min, ScreenTime.usedToday(usage, at(29, 18, 10), zone))
    }

    @Test
    fun `le compte repart de zéro à minuit, même en pleine séance`() {
        val usage = Usage(day = "2026-10-29", usedMs = 90 * min, activeSince = at(29, 23, 30))
        assertEquals(20 * min, ScreenTime.usedToday(usage, at(30, 0, 20), zone))
        assertEquals(0, ScreenTime.usedToday(usage.copy(activeSince = null), at(30, 9), zone))
    }

    @Test
    fun `limite atteinte, puis rab du PIN parent`() {
        val limits = ChildLimits(dailyMinutes = 60)
        val usage = Usage(day = "2026-10-29", usedMs = 50 * min)
        assertEquals(ScreenTimeVerdict.Allowed(10 * min), ScreenTime.verdict(limits, usage, at(29, 15), zone))
        val over = usage.copy(usedMs = 60 * min)
        assertTrue(ScreenTime.verdict(limits, over, at(29, 15), zone).blocked)
        val bonus = ScreenTime.grantBonus(over, at(29, 15))
        assertEquals(ScreenTimeVerdict.Allowed(ScreenTime.BONUS_MS), ScreenTime.verdict(limits, bonus, at(29, 15), zone))
        assertTrue(ScreenTime.verdict(limits, bonus, at(29, 15, 16), zone).blocked)
    }

    @Test
    fun `couvre-feu qui passe minuit`() {
        val curfew = Curfew(20 * 60, 7 * 60 + 30)
        assertTrue(curfew.contains(21 * 60))
        assertTrue(curfew.contains(2 * 60))
        assertFalse(curfew.contains(7 * 60 + 30))
        assertFalse(curfew.contains(12 * 60))
        val limits = ChildLimits(curfew = curfew)
        assertEquals(ScreenTimeVerdict.Curfewed(curfew), ScreenTime.verdict(limits, Usage(), at(29, 20, 5), zone))
        assertEquals(ScreenTimeVerdict.Allowed(null), ScreenTime.verdict(limits, Usage(), at(29, 19, 59), zone))
        assertEquals("20:00 → 7:30", curfew.label)
    }

    @Test
    fun `sans limite, tout est permis`() {
        assertFalse(ScreenTime.verdict(ChildLimits(), Usage(usedMs = 10 * 60 * min), at(29, 23), zone).blocked)
    }

    @Test
    fun `durées lisibles`() {
        assertEquals("1 h 10", ScreenTime.formatDuration(70 * min))
        assertEquals("2 h", ScreenTime.formatDuration(120 * min))
        assertEquals("45 min", ScreenTime.formatDuration(45 * min))
        assertEquals("moins d'une minute", ScreenTime.formatDuration(20_000))
        assertEquals("Sans limite", ScreenTime.dailyLabel(null))
        assertEquals("1 h 30 par jour", ScreenTime.dailyLabel(90))
    }
}
