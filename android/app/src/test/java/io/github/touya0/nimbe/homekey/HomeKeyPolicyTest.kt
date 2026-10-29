package io.github.touya0.nimbe.homekey

import io.github.touya0.nimbe.crashguard.StartupMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeKeyPolicyTest {

    @Test
    fun `Nimbe prend la touche quand il va bien`() {
        assertTrue(HomeKeyPolicy.takesHomeKey(StartupMode.Normal))
    }

    @Test
    fun `en Secours ou en mode sans échec, la touche reste à Google TV`() {
        assertFalse(HomeKeyPolicy.takesHomeKey(StartupMode.Rescue))
        assertFalse(HomeKeyPolicy.takesHomeKey(StartupMode.SafeMode))
    }

    @Test
    fun `appui court vers Nimbe, appui long vers l'accueil d'origine`() {
        assertEquals(HomeTarget.Nimbe, HomeKeyPolicy.target(heldMs = 120, longPressMs = 500))
        assertEquals(HomeTarget.Stock, HomeKeyPolicy.target(heldMs = 500, longPressMs = 500))
        assertEquals(HomeTarget.Stock, HomeKeyPolicy.target(heldMs = 2_000, longPressMs = 500))
    }

    @Test
    fun `Nimbe s'ouvre une seule fois par démarrage du stick`() {
        assertTrue(HomeKeyPolicy.opensAtBoot(bootCount = 12, lastOpenedBoot = 11, mode = StartupMode.Normal))
        assertTrue(HomeKeyPolicy.opensAtBoot(bootCount = 12, lastOpenedBoot = null, mode = StartupMode.Normal))
        assertFalse(HomeKeyPolicy.opensAtBoot(bootCount = 12, lastOpenedBoot = 12, mode = StartupMode.Normal))
    }

    @Test
    fun `au démarrage, pas d'ouverture si Nimbe ne va pas bien ou si le compteur est illisible`() {
        assertFalse(HomeKeyPolicy.opensAtBoot(bootCount = 12, lastOpenedBoot = 11, mode = StartupMode.Rescue))
        assertFalse(HomeKeyPolicy.opensAtBoot(bootCount = 12, lastOpenedBoot = 11, mode = StartupMode.SafeMode))
        assertFalse(HomeKeyPolicy.opensAtBoot(bootCount = null, lastOpenedBoot = 11, mode = StartupMode.Normal))
    }

    @Test
    fun `profil Enfant, l'appui long reste dans Nimbe`() {
        assertEquals(HomeTarget.Nimbe, HomeKeyPolicy.target(heldMs = 2_000, longPressMs = 500, childActive = true))
        assertEquals(HomeTarget.Nimbe, HomeKeyPolicy.target(heldMs = 120, longPressMs = 500, childActive = true))
        assertEquals(HomeTarget.Stock, HomeKeyPolicy.target(heldMs = 2_000, longPressMs = 500, childActive = false))
    }
}
