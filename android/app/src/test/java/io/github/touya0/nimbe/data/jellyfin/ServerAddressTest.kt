package io.github.touya0.nimbe.data.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {

    private fun parsed(input: String) = ServerAddress.parse(input).getOrThrow()
    private fun error(input: String) = ServerAddress.parse(input).exceptionOrNull()?.message

    @Test
    fun `adresse saisie à la télécommande, sans schéma ni port`() {
        assertEquals("http://192.168.1.20:8096", parsed("192.168.1.20"))
        assertEquals("http://192.168.1.182:8097", parsed(" 192.168.1.182:8097/ "))
        assertEquals("http://jellyfin.lan:8096", parsed("jellyfin.lan"))
        assertEquals("http://192.168.1.182:8123", ServerAddress.parse("192.168.1.182", defaultPort = 8123).getOrThrow())
    }

    @Test
    fun `Nextcloud part en https sans schéma, le http reste possible à la maison`() {
        fun nextcloud(input: String) = ServerAddress.parse(input, defaultPort = 80, secureByDefault = true).getOrThrow()
        assertEquals("https://cloud.example.fr", nextcloud("cloud.example.fr/"))
        assertEquals("http://192.168.1.182:8080", nextcloud("http://192.168.1.182:8080"))
    }

    @Test
    fun `le chemin d'un proxy est gardé, sans barre finale`() {
        assertEquals("https://films.example.org/jellyfin", parsed("https://films.example.org/jellyfin/"))
        assertEquals("https://films.example.org:8920", parsed("https://films.example.org:8920"))
    }

    @Test
    fun `le HTTP en clair est refusé hors du réseau local`() {
        assertEquals("Hors de la maison, utilise une adresse en https://", error("http://films.example.org"))
        assertEquals("Hors de la maison, utilise une adresse en https://", error("8.8.8.8:8096"))
    }

    @Test
    fun `saisies invalides`() {
        assertEquals("Saisis l'adresse du serveur", error("   "))
        assertEquals("L'adresse doit commencer par http:// ou https://", error("ftp://192.168.1.20"))
        assertEquals("Adresse invalide", error("http://"))
    }

    @Test
    fun `hôtes locaux`() {
        listOf("10.0.2.2", "172.16.0.1", "172.31.255.1", "192.168.0.1", "127.0.0.1", "169.254.1.1", "pi5", "nas.local", "pi.home.arpa", "::1", "fd00::1")
            .forEach { assertTrue(it, ServerAddress.isLocalHost(it)) }
        listOf("172.32.0.1", "8.8.8.8", "example.org", "192.169.1.1", "2001:db8::1", "10.0.0.256.example.com")
            .forEach { assertFalse(it, ServerAddress.isLocalHost(it)) }
    }
}
