package io.github.touya0.nimbe.data.notifications

import io.github.touya0.nimbe.data.homeassistant.HaConnection
import io.github.touya0.nimbe.data.homeassistant.HaEntity
import io.github.touya0.nimbe.data.homeassistant.HaLive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceAlertsTest {

    private val minute = 60_000L
    private val start = 100 * minute

    private fun service(state: String, at: Long) = HaEntity("binary_sensor.jellyfin", "Jellyfin joignable", state, null, "connectivity", at)

    private fun live(vararg entities: HaEntity) = HaLive(HaConnection.Live(0), entities.map { it.id }, entities.associateBy { it.id })

    @Test
    fun `une coupure de moins de 2 minutes ne dit rien`() {
        assertTrue(ServiceAlerts().evaluate(live(service("off", start)), true, start + minute).isEmpty())
    }

    @Test
    fun `panne annoncée une fois, puis son retour`() {
        val alerts = ServiceAlerts()
        val down = alerts.evaluate(live(service("off", start)), true, start + 3 * minute).single()
        assertEquals(NotificationLevel.Critical, down.level)
        assertEquals("Jellyfin ne répond plus", down.title)
        val up = alerts.evaluate(live(service("on", start + 8 * minute)), true, start + 8 * minute).single()
        assertEquals("Jellyfin est de nouveau en ligne", up.title)
        assertEquals("Coupure de 8 min", up.body)
        assertTrue(alerts.evaluate(live(service("on", start + 8 * minute)), true, start + 9 * minute).isEmpty())
    }

    @Test
    fun `sans réseau sur le stick, le Pi n'est pas accusé`() {
        val lost = HaLive(HaConnection.Lost(start, refused = false))
        assertTrue(ServiceAlerts().evaluate(lost, networkUp = false, now = start + 10 * minute).isEmpty())
        assertEquals(1, ServiceAlerts().evaluate(lost, networkUp = true, now = start + 10 * minute).size)
    }

    @Test
    fun `un jeton refusé n'est pas une panne`() {
        assertTrue(ServiceAlerts().evaluate(HaLive(HaConnection.Lost(start, refused = true)), true, start + 10 * minute).isEmpty())
    }

    @Test
    fun `disque au-delà de 80 %, une alerte par semaine`() {
        val disk = HaEntity("sensor.disk_use_percent", "Disque utilisé", "81", "%", null, start)
        val alert = ServiceAlerts().evaluate(live(disk), true, start).single()
        assertEquals(NotificationType.Disk, alert.type)
        assertEquals("Disque du Pi rempli à ${disk.displayValue}", alert.title)
    }
}
