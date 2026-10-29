package io.github.touya0.nimbe.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecksTest {

    private val allGood = StickState(
        defaultHome = false,
        homeKeyOn = true,
        homeKeyComponent = "io.github.touya0.nimbe/.homekey.HomeKeyService",
        stockLauncherFound = true,
        screensaverOn = true,
        screensaverComponent = "io.github.touya0.nimbe/.feature.screensaver.NimbeDream",
        canInstallUpdates = true,
    )

    @Test
    fun `tout est en place`() {
        val checks = SetupChecks.build(allGood)
        assertEquals(4, checks.size)
        assertTrue(checks.all { it.level == CheckLevel.Ok })
    }

    @Test
    fun `accueil par défaut, la touche Accueil n'a rien à activer`() {
        val check = SetupChecks.build(allGood.copy(defaultHome = true, homeKeyOn = false)).first()
        assertEquals(CheckLevel.Ok, check.level)
        assertEquals(null, check.action)
    }

    @Test
    fun `ce qui manque donne la commande adb pour ce paquet`() {
        val checks = SetupChecks.build(allGood.copy(homeKeyOn = false, screensaverOn = false, canInstallUpdates = false))
        assertEquals(CheckLevel.ToDo, checks[0].level)
        assertTrue("io.github.touya0.nimbe/.homekey.HomeKeyService" in checks[0].command.orEmpty())
        assertEquals(CheckLevel.ToDo, checks[2].level)
        assertTrue("screensaver_components io.github.touya0.nimbe/.feature.screensaver.NimbeDream" in checks[2].command.orEmpty())
        assertEquals(CheckAction.InstallPermission, checks[3].action)
    }

    @Test
    fun `écran de veille illisible, inconnu et jamais OK`() {
        assertEquals(CheckLevel.Unknown, SetupChecks.build(allGood.copy(screensaverOn = null))[2].level)
    }
}
