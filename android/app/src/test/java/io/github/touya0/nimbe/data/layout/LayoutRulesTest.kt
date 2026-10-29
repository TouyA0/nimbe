package io.github.touya0.nimbe.data.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutRulesTest {

    private fun ids(items: List<LayoutItem>) = items.map { it.id }

    @Test
    fun `mise en page par défaut, tout est visible`() {
        val layout = LayoutRules.normalize(ProfileLayout.Default)
        assertEquals(HomeRow.entries.toList(), layout.visibleRows)
        assertEquals(TabKeys.defaults, layout.visibleTabs)
    }

    @Test
    fun `inconnus ignorés, doublons retirés`() {
        val saved = listOf(LayoutItem("latest", true), LayoutItem("martien", true), LayoutItem("latest", false), LayoutItem("apps", true))
        // « continue » manquait et n'a rien devant lui dans l'ordre par défaut : il revient en tête.
        assertEquals(listOf("continue", "latest", "apps"), ids(LayoutRules.reconcile(saved, listOf("continue", "apps", "latest"), customized = true)))
    }

    @Test
    fun `une nouvelle rangée arrive à sa place, masquée si le profil a personnalisé`() {
        val saved = listOf(LayoutItem("apps", true), LayoutItem("continue", true))
        val known = listOf("continue", "live", "apps")
        val custom = LayoutRules.reconcile(saved, known, customized = true)
        assertEquals(listOf("apps", "continue", "live"), ids(custom))
        assertEquals(false, custom.last().visible)
        assertTrue(LayoutRules.reconcile(saved, known, customized = false).all { it.visible })
    }

    @Test
    fun `l'Accueil reste premier et affiché, Applis reste affiché`() {
        val saved = ProfileLayout(
            homeRows = emptyList(),
            tabs = listOf(LayoutItem("movies", true), LayoutItem("home", false), LayoutItem("apps", false)),
            startTab = "gallery",
            customized = true,
        )
        val layout = LayoutRules.normalize(saved)
        assertEquals("home", layout.tabs.first().id)
        assertTrue(layout.tabs.first { it.id == "home" }.visible)
        assertTrue(layout.tabs.first { it.id == "apps" }.visible)
        // Galerie, ajoutée par la réconciliation, est masquée : on ne démarre pas sur un onglet caché.
        assertEquals("home", layout.startTab)
    }

    @Test
    fun `les anciens onglets TV et Musique disparaissent des profils enregistrés`() {
        val saved = ProfileLayout(
            homeRows = emptyList(),
            tabs = listOf("home", "tv", "movies", "series", "music", "gallery", "apps").map { LayoutItem(it, true) },
            startTab = "tv",
            customized = true,
        )
        val layout = LayoutRules.normalize(saved)
        assertEquals(TabKeys.defaults, ids(layout.tabs))
        assertEquals("home", layout.startTab)
    }

    @Test
    fun `déplacer sans passer devant l'Accueil`() {
        val tabs = TabKeys.defaults.map { LayoutItem(it, true) }
        assertEquals(tabs, LayoutRules.move(tabs, "movies", -1))
        assertEquals(tabs, LayoutRules.move(tabs, "home", 1))
        assertEquals(listOf("home", "series", "movies"), ids(LayoutRules.move(tabs, "movies", 1)).take(3))
        assertEquals(tabs, LayoutRules.move(tabs, "apps", 1))
    }

    @Test
    fun `une rangée remise va à la fin des visibles`() {
        val rows = listOf(LayoutItem("continue", false), LayoutItem("apps", true), LayoutItem("latest", true))
        val shown = LayoutRules.setVisible(rows, "continue", true)
        assertEquals(listOf("apps", "latest", "continue"), ids(shown))
        assertTrue(shown.all { it.visible })
        assertEquals(listOf(true, false, true), LayoutRules.setVisible(shown, "latest", false).map { it.visible })
    }

    @Test
    fun `onglets qu'on ne peut pas masquer`() {
        assertNotNull(LayoutRules.hideTabProblem("home"))
        assertNotNull(LayoutRules.hideTabProblem("apps"))
        assertNull(LayoutRules.hideTabProblem("movies"))
    }

    @Test
    fun `aller-retour JSON`() {
        val layout = ProfileLayout(
            homeRows = listOf(LayoutItem("apps", true), LayoutItem("continue", false), LayoutItem("latest", true)),
            tabs = TabKeys.defaults.map { LayoutItem(it, it != "gallery") },
            startTab = "movies",
            customized = true,
        )
        assertEquals(layout, LayoutCodec.decode(LayoutCodec.encode(layout)))
        assertNull(LayoutCodec.decode("pas du json"))
        assertNull(LayoutCodec.decode(null))
    }
}
