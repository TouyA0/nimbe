package io.github.touya0.nimbe.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevocationTest {

    @Test
    fun `chaque service dit ce qui est fait et ce qui reste à faire`() {
        assertEquals("Jellyfin : accès révoqué sur le serveur", Revocation("Jellyfin", onServer = true, "x").summary())
        val offline = Revocation("Home Assistant", onServer = false, "supprime aussi le jeton").summary()
        assertTrue(offline.startsWith("Home Assistant : oublié sur la TV"))
        assertTrue(offline.endsWith("supprime aussi le jeton"))
    }
}
