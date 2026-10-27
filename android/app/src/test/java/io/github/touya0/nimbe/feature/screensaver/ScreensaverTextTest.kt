package io.github.touya0.nimbe.feature.screensaver

import io.github.touya0.nimbe.data.homeassistant.HaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ScreensaverTextTest {

    private fun weather(state: String, temperature: Double?) =
        HaEntity("weather.forecast_maison", "Maison", state, null, null, 1L, temperature = temperature)

    @Test
    fun `météo en français, jamais inventée`() {
        assertEquals("12 °C · Éclaircies", ScreensaverText.weather(weather("partlycloudy", 12.4)))
        assertEquals("Pluie", ScreensaverText.weather(weather("rainy", null)))
        assertEquals("-3 °C", ScreensaverText.weather(weather("inconnu-du-dico", -2.6)))
        assertNull(ScreensaverText.weather(weather("unavailable", 12.0)))
        assertNull(ScreensaverText.weather(null))
    }

    @Test
    fun `mesures du Pi sur une ligne, sans les valeurs inconnues`() {
        fun sensor(id: String, state: String, unit: String) = HaEntity(id, id, state, unit, null, 1L, platform = "systemmonitor",
            translationKey = id.substringAfter('.'))
        val line = ScreensaverText.stats(
            listOf(
                sensor("sensor.processor_use", "6.2", "%"),
                sensor("sensor.processor_temperature", "57.1", "°C"),
                sensor("sensor.memory_use_percent", "unavailable", "%"),
                sensor("sensor.disk_use_percent", "50", "%"),
            ),
        )
        assertEquals("Processeur 6 % · 57 °C · Disque 50 %", line)
    }

    @Test
    fun `anti-marquage, un tour par heure`() {
        (0L until 120L).forEach { minute ->
            val (x, y) = ScreensaverText.burnInOffset(minute, 12f)
            assertEquals(12.0, hypot(x.toDouble(), y.toDouble()), 0.001)
        }
        assertEquals(ScreensaverText.burnInOffset(5, 12f), ScreensaverText.burnInOffset(65, 12f))
        assertTrue(ScreensaverText.burnInOffset(0, 12f) != ScreensaverText.burnInOffset(1, 12f))
    }
}
