package io.github.touya0.nimbe.data.homeassistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class HomeAssistantJsonTest {

    private fun entity(id: String, state: String, unit: String? = null, deviceClass: String? = null, name: String? = null) =
        HaEntity(id, name ?: id, state, unit, deviceClass, updatedAt = null)

    @Test
    fun `état lu depuis get_states`() {
        val json = JSONObject(
            """{"entity_id":"sensor.system_monitor_processor_temperature","state":"52.3",
               "attributes":{"friendly_name":"Température du Pi","unit_of_measurement":"°C","device_class":"temperature"},
               "last_updated":"2026-10-27T10:15:00.123456+00:00"}""",
        )
        val parsed = HaEntity.fromState(json)!!
        assertEquals("Température du Pi", parsed.name)
        assertEquals("52 °C", parsed.displayValue)
        assertEquals(1_793_096_100_123L, parsed.updatedAt)
        assertNull(HaEntity.fromState(JSONObject("""{"state":"on"}""")))
    }

    @Test
    fun `valeurs affichées`() {
        assertEquals("23 %", entity("sensor.processor_use", "23.4", "%").displayValue)
        assertEquals("En ligne", entity("binary_sensor.jellyfin", "on", deviceClass = "connectivity").displayValue)
        assertEquals("Hors ligne", entity("binary_sensor.jellyfin", "off", deviceClass = "connectivity").displayValue)
        // « inconnu » n'est jamais présenté comme une valeur normale.
        assertEquals("Inconnu", entity("binary_sensor.jellyfin", "unavailable", deviceClass = "connectivity").displayValue)
        assertEquals("Inconnu", entity("sensor.processor_use", "unknown", "%").displayValue)
        assertEquals("1.5 GiB", entity("sensor.memory_free", "1.5", "GiB").displayValue)
    }

    @Test
    fun `capteurs reconnus, anciens et nouveaux noms de System Monitor`() {
        val all = listOf(
            entity("sensor.disk_use_percent", "81", "%", name = "Disque"),
            entity("binary_sensor.nextcloud", "on", deviceClass = "connectivity", name = "Nextcloud"),
            entity("sensor.system_monitor_processor_use", "23", "%", name = "Processeur"),
            entity("sensor.memory_use_percent", "39", "%", name = "Mémoire"),
            entity("sensor.processor_temperature", "52", "°C", "temperature", name = "Température"),
            entity("binary_sensor.jellyfin", "on", deviceClass = "connectivity", name = "Jellyfin"),
            entity("sensor.memory_use", "1500", "MiB"),
            entity("sensor.salon_temperature", "20", "°C", "temperature"),
            entity("binary_sensor.porte", "off", deviceClass = "door"),
            entity("light.salon", "on"),
        )
        assertEquals(
            listOf(
                "sensor.system_monitor_processor_use",
                "sensor.processor_temperature",
                "sensor.memory_use_percent",
                "sensor.disk_use_percent",
                "binary_sensor.jellyfin",
                "binary_sensor.nextcloud",
            ),
            SensorCatalog.candidates(all).map { it.id },
        )
        assertEquals(SensorKind.Service, SensorCatalog.kindOf(all[1]))
    }

    @Test
    fun `adresse du WebSocket`() {
        assertEquals("ws://192.168.1.182:8123/api/websocket", HomeAssistantApi.websocketUrl("http://192.168.1.182:8123"))
        assertEquals("wss://ha.example.org/api/websocket", HomeAssistantApi.websocketUrl("https://ha.example.org/"))
    }

    @Test
    fun `identifiant du jeton longue durée, pour le révoquer`() {
        fun part(json: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        val token = "${part("""{"alg":"HS256","typ":"JWT"}""")}.${part("""{"iss":"abc123","iat":1,"exp":2}""")}.signature"
        assertEquals("abc123", HomeAssistantApi.refreshTokenId(token))
        assertNull(HomeAssistantApi.refreshTokenId("pas-un-jwt"))
        assertNull(HomeAssistantApi.refreshTokenId("a.!!!.c"))
    }
}
