package io.github.touya0.nimbe.feature.home

import io.github.touya0.nimbe.data.watchnext.WatchNextProgram
import io.github.touya0.nimbe.feature.media.CatalogFocus
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTextTest {

    @Test
    fun `bonjour le jour, bonsoir le soir et la nuit`() {
        assertEquals("Bonjour Quentin", HomeText.greeting(5, "Quentin"))
        assertEquals("Bonjour Quentin", HomeText.greeting(17, "Quentin"))
        assertEquals("Bonsoir Quentin", HomeText.greeting(18, "Quentin"))
        assertEquals("Bonsoir Quentin", HomeText.greeting(2, "Quentin"))
    }

    @Test
    fun `sans nom, le salut seul`() = assertEquals("Bonjour", HomeText.greeting(9, " "))

    @Test
    fun `un contenu d'une autre appli s'ouvre dans cette appli`() {
        val program = WatchNextProgram(1, "com.disney", "Disney+", "Zootopie 2")
        assertEquals(
            listOf("OK" to "ouvrir dans Disney+", "OK maintenu" to "options"),
            HomeText.hints(HomeHero.Media(CatalogFocus.WatchNext(program)), canEdit = true),
        )
    }

    @Test
    fun `rien à faire sans élément focalisé`() {
        assertEquals(emptyList<Pair<String, String>>(), HomeText.hints(HomeHero.Empty, canEdit = true))
        assertEquals(emptyList<Pair<String, String>>(), HomeText.hints(HomeHero.Loading, canEdit = true))
    }
}
