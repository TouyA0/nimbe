package io.github.touya0.nimbe.data.nextcloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NextcloudTest {

    @Test
    fun `un lien se relit tel quel, un lien abîmé est ignoré`() {
        val link = NextcloudLink("https://cloud.example.fr", "quentin@example.fr", "quentin", "Quentin")
        assertEquals(link, NextcloudLinkCodec.decode(NextcloudLinkCodec.encode(link)))
        assertNull(NextcloudLinkCodec.decode("{\"server\":\"https://cloud.example.fr\"}"))
        assertNull(NextcloudLinkCodec.decode("pas du json"))
    }

    @Test
    fun `le mot de passe d'application ne sort jamais dans un message`() {
        val auth = NextcloudAuth("https://cloud.example.fr", "quentin", "secret-123")
        assertFalse("secret-123" in auth.toString())
        assertEquals("Basic cXVlbnRpbjpzZWNyZXQtMTIz", NextcloudApi.basic(auth))
    }

    @Test
    fun `les adresses renvoyées par le serveur suivent la règle du https`() {
        assertEquals("https://cloud.example.fr/login/v2/poll", NextcloudApi.safeUrl("https://cloud.example.fr/login/v2/poll"))
        assertEquals("http://192.168.1.182:8080/login/v2/poll", NextcloudApi.safeUrl("http://192.168.1.182:8080/login/v2/poll"))
        assertThrows(NextcloudException::class.java) { NextcloudApi.safeUrl("http://cloud.example.fr/login/v2/poll") }
        assertThrows(NextcloudException::class.java) { NextcloudApi.safeUrl("javascript:alert(1)") }
    }

    @Test
    fun `l'adresse de la maison est gardée avec le lien`() {
        val link = NextcloudLink("https://cloud.example.fr", "q", "q", "Q", listOf("Photos"), localUrl = "http://192.168.1.182:7580")
        assertEquals(link, NextcloudLinkCodec.decode(NextcloudLinkCodec.encode(link)))
        assertNull(NextcloudLinkCodec.decode(NextcloudLinkCodec.encode(link.copy(localUrl = null)))?.localUrl)
    }
}
