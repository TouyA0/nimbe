package io.github.touya0.nimbe.data.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinProtocolTest {

    private val client = ClientInfo(deviceName = "Salon", deviceId = "1b4e28ba-2fa1-11d2-883f-0016d3cca427", version = "0.4.0")

    @Test
    fun `en-tête sans jeton puis avec`() {
        assertEquals(
            "MediaBrowser Client=\"Nimbe\", Device=\"Salon\", DeviceId=\"1b4e28ba-2fa1-11d2-883f-0016d3cca427\", Version=\"0.4.0\"",
            JellyfinApi.authorization(client, token = null),
        )
        assertEquals(
            "MediaBrowser Client=\"Nimbe\", Device=\"Salon\", DeviceId=\"1b4e28ba-2fa1-11d2-883f-0016d3cca427\", " +
                "Version=\"0.4.0\", Token=\"abc\"",
            JellyfinApi.authorization(client, token = "abc"),
        )
    }

    @Test
    fun `un nom de TV avec guillemets ou virgules ne casse pas l'en-tête`() {
        val header = JellyfinApi.authorization(client.copy(deviceName = "TV \"Chambre\", étage"), token = null)
        assertEquals(true, header.contains("Device=\"TV Chambre étage\""))
        assertEquals(true, JellyfinApi.authorization(client.copy(deviceName = "\"\""), null).contains("Device=\"TV\""))
    }

    @Test
    fun `réponse de découverte, adresse annoncée puis celle de l'expéditeur`() {
        val reply = """{"Address":"http://172.17.0.4:8096","Id":"9580","Name":"jellyfin","EndpointAddress":null}"""
        val server = JellyfinDiscovery.parseReply(reply, senderIp = "192.168.1.182")!!
        assertEquals("9580", server.id)
        assertEquals("jellyfin", server.name)
        assertEquals(listOf("http://172.17.0.4:8096", "http://192.168.1.182:8096"), server.addresses)
    }

    @Test
    fun `réponse de découverte sans doublon ni déchet`() {
        val reply = """{"Address":"http://192.168.1.20:8096","Id":"a","Name":"Pi"}"""
        assertEquals(listOf("http://192.168.1.20:8096"), JellyfinDiscovery.parseReply(reply, "192.168.1.20")!!.addresses)
        assertNull(JellyfinDiscovery.parseReply("pas du JSON", "192.168.1.20"))
        assertNull(JellyfinDiscovery.parseReply("""{"Address":"http://192.168.1.20:8096"}""", "192.168.1.20"))
    }
}
