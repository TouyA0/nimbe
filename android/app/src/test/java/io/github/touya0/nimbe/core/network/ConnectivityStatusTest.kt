package io.github.touya0.nimbe.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityStatusTest {

    private fun next(previous: ConnectivityStatus, state: NetworkState, now: Long) =
        ConnectivityStatus.next(previous, state, now)

    @Test
    fun `au démarrage, rien n'est signalé`() {
        assertFalse(ConnectivityStatus.Initial.offline)
        assertNull(ConnectivityStatus.Initial.since)
    }

    @Test
    fun `une coupure retient son heure de début`() {
        val online = next(ConnectivityStatus.Initial, NetworkState.Online, now = 100)
        val cut = next(online, NetworkState.NoInternet, now = 200)
        assertTrue(cut.offline)
        assertEquals(200L, cut.since)
    }

    @Test
    fun `passer de pas d'internet à pas de réseau garde l'heure de début`() {
        val cut = next(ConnectivityStatus.Initial, NetworkState.NoInternet, now = 200)
        val worse = next(cut, NetworkState.NoNetwork, now = 500)
        assertEquals(NetworkState.NoNetwork, worse.state)
        assertEquals(200L, worse.since)
    }

    @Test
    fun `le retour d'internet efface la coupure, la suivante repart de zéro`() {
        val cut = next(ConnectivityStatus.Initial, NetworkState.NoInternet, now = 200)
        val back = next(cut, NetworkState.Online, now = 300)
        assertFalse(back.offline)
        assertNull(back.since)
        assertEquals(400L, next(back, NetworkState.NoNetwork, now = 400).since)
    }

    @Test
    fun `les applis locales ne sont pas marquées hors ligne`() {
        assertTrue(InternetApps.needsInternet("com.netflix.ninja"))
        assertFalse(InternetApps.needsInternet("org.jellyfin.androidtv"))
    }
}
