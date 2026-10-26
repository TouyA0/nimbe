package io.github.touya0.nimbe.feature.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsTest {

    private fun app(pkg: String) = InstalledApp(pkg, pkg, AppCategory.Other, icon = null, removable = true)

    @Test
    fun `garde l'ordre des lancements, sans les applis masquées ou désinstallées`() {
        val apps = listOf(app("a"), app("b"), app("c"))
        val recents = Recents.resolve(listOf("c", "desinstallee", "a", "b"), apps, hidden = setOf("b"))
        assertEquals(listOf("c", "a"), recents.map { it.packageName })
    }

    @Test
    fun `limité à une rangée`() {
        val apps = (1..10).map { app("p$it") }
        assertEquals(Recents.SIZE, Recents.resolve(apps.map { it.packageName }, apps, emptySet()).size)
    }
}
