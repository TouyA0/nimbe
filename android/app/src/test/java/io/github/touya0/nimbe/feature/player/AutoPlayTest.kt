package io.github.touya0.nimbe.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPlayTest {

    @Test
    fun `la suite apparaît au générique, sinon 30 s avant la fin`() {
        assertEquals(2_800_000L, NextUpRule.showAtMs(durationMs = 3_000_000, outroStartMs = 2_800_000))
        assertEquals(2_970_000L, NextUpRule.showAtMs(durationMs = 3_000_000, outroStartMs = null))
        // Un générique annoncé après la fin est ignoré.
        assertEquals(2_970_000L, NextUpRule.showAtMs(durationMs = 3_000_000, outroStartMs = 3_100_000))
        assertEquals(0L, NextUpRule.showAtMs(durationMs = 20_000, outroStartMs = null))
        assertNull(NextUpRule.showAtMs(durationMs = 0, outroStartMs = null))
    }

    @Test
    fun `tu regardes toujours, au troisième épisode sans toucher la télécommande`() {
        var untouched = 0
        // Premier épisode lancé à la main, puis deux enchaînés tout seuls.
        repeat(2) {
            untouched = NextUpRule.untouched(untouched, touchedThisEpisode = false)
            assertFalse(NextUpRule.askStillWatching(untouched))
        }
        untouched = NextUpRule.untouched(untouched, touchedThisEpisode = false)
        assertTrue(NextUpRule.askStillWatching(untouched))
        // Une seule touche remet le compte à zéro.
        assertEquals(0, NextUpRule.untouched(untouched, touchedThisEpisode = true))
    }

    @Test
    fun `minuteur de veille`() {
        val timer = SleepTimer.inMinutes(15, now = 1_000)
        assertEquals(15 * 60_000L + 1_000, timer.epochMs)
        assertEquals(60_000L, SleepTimer.remainingMs(timer, now = timer.epochMs - 60_000))
        assertEquals(0L, SleepTimer.remainingMs(timer, now = timer.epochMs + 5_000))
        assertNull(SleepTimer.remainingMs(SleepTimer.EndOfItem))
    }
}
