package io.github.touya0.nimbe.crashguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashPolicyTest {

    private val now = 1_000_000_000L

    @Test
    fun `aucun plantage, démarrage normal`() {
        assertEquals(StartupMode.Normal, CrashPolicy.decide(emptyList(), safeMode = false, now = now))
    }

    @Test
    fun `deux plantages récents ne suffisent pas`() {
        val crashes = listOf(now - 10_000, now - 5_000)
        assertEquals(StartupMode.Normal, CrashPolicy.decide(crashes, safeMode = false, now = now))
    }

    @Test
    fun `trois plantages en deux minutes déclenchent le Secours`() {
        val crashes = listOf(now - 110_000, now - 60_000, now - 1_000)
        assertEquals(StartupMode.Rescue, CrashPolicy.decide(crashes, safeMode = false, now = now))
    }

    @Test
    fun `des plantages anciens ne comptent pas`() {
        val crashes = listOf(now - 600_000, now - 300_000, now - 1_000)
        assertEquals(StartupMode.Normal, CrashPolicy.decide(crashes, safeMode = false, now = now))
    }

    @Test
    fun `un horodatage dans le futur (horloge changée) ne compte pas`() {
        val crashes = listOf(now + 50_000, now + 60_000, now - 1_000)
        assertEquals(1, CrashPolicy.recentCount(crashes, now))
    }

    @Test
    fun `le mode sans échec est conservé tant qu'il ne plante pas`() {
        assertEquals(StartupMode.SafeMode, CrashPolicy.decide(listOf(now - 1_000), safeMode = true, now = now))
    }

    @Test
    fun `si le mode sans échec plante trois fois, retour au Secours`() {
        val crashes = listOf(now - 3_000, now - 2_000, now - 1_000)
        assertEquals(StartupMode.Rescue, CrashPolicy.decide(crashes, safeMode = true, now = now))
    }

    @Test
    fun `l'historique est borné`() {
        var crashes = emptyList<Long>()
        repeat(50) { crashes = CrashPolicy.append(crashes, it.toLong()) }
        assertEquals(CrashPolicy.MAX_KEPT, crashes.size)
        assertEquals(49L, crashes.last())
    }

    @Test
    fun `encodage et décodage, valeurs abîmées ignorées`() {
        val crashes = listOf(1L, 22L, 333L)
        assertEquals(crashes, CrashPolicy.decode(CrashPolicy.encode(crashes)))
        assertEquals(listOf(1L, 3L), CrashPolicy.decode("1,abc,,3"))
        assertEquals(emptyList<Long>(), CrashPolicy.decode(null))
    }

    @Test
    fun `le résumé remonte à la cause d'origine`() {
        val wrapped = RuntimeException("Unable to start activity", IllegalArgumentException("x"))
        assertTrue(CrashPolicy.summarize(wrapped, "io.github.touya0.nimbe").startsWith("IllegalArgumentException"))
    }

    @Test
    fun `le résumé ne contient pas le message de l'erreur`() {
        val error = IllegalStateException("token=secret pour quentin@exemple.fr")
        val summary = CrashPolicy.summarize(error, "io.github.touya0.nimbe")
        assertTrue(summary.startsWith("IllegalStateException"))
        assertFalse(summary.contains("secret"))
        assertFalse(summary.contains("@"))
    }
}
