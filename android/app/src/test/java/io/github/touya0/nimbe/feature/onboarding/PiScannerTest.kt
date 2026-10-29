package io.github.touya0.nimbe.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PiScannerTest {

    @Test
    fun `hôte d'une adresse de service`() {
        assertEquals("192.168.1.182", PiScanner.hostOf("http://192.168.1.182:8097"))
        assertEquals("pi5.lan", PiScanner.hostOf("https://pi5.lan/jellyfin"))
        assertEquals("[fd00::2]", PiScanner.hostOf("http://[fd00::2]:8096"))
        assertNull(PiScanner.hostOf("pas une adresse"))
    }

    @Test
    fun `Home Assistant cherché sur la machine de Jellyfin`() {
        assertEquals("http://192.168.1.182:8123", PiScanner.homeAssistantUrl("192.168.1.182"))
        assertEquals("http://[fd00::2]:8123", PiScanner.homeAssistantUrl("[fd00::2]"))
    }

    @Test
    fun `manifest de Home Assistant reconnu, les autres non`() {
        assertTrue(PiScanner.isHomeAssistantManifest("""{"name":"Home Assistant","short_name":"Home Assistant"}"""))
        assertFalse(PiScanner.isHomeAssistantManifest("""{"name":"Nextcloud"}"""))
        assertFalse(PiScanner.isHomeAssistantManifest("<html>Jellyfin</html>"))
        assertFalse(PiScanner.isHomeAssistantManifest(""))
    }

    @Test
    fun `le Pi est la machine de Jellyfin, sinon celle de Home Assistant`() {
        val jellyfin = ServiceSpot.Found("http://192.168.1.182:8097")
        val ha = ServiceSpot.Linked("http://192.168.1.50:8123", reachable = true)
        assertEquals("192.168.1.182", PiScanner.piHost(jellyfin, ha))
        assertEquals("192.168.1.50", PiScanner.piHost(ServiceSpot.Missing, ha))
        assertNull(PiScanner.piHost(ServiceSpot.Missing, ServiceSpot.Missing))
        // Un Jellyfin relié qui ne répond plus ne désigne pas le Pi si Home Assistant, lui, répond.
        assertEquals("192.168.1.50", PiScanner.piHost(ServiceSpot.Linked("http://10.0.2.2:18097", reachable = false), ha))
    }

    @Test
    fun `rien trouvé tant que les deux services manquent`() {
        assertFalse(PiScan(null, ServiceSpot.Missing, ServiceSpot.Missing).foundAnything)
        assertTrue(PiScan(null, ServiceSpot.Linked("http://pi:8096", reachable = false), ServiceSpot.Missing).foundAnything)
    }
}
