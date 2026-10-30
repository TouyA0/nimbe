package io.github.touya0.nimbe.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTextTest {

    @Test
    fun `classement exact, début de mot, ailleurs`() {
        assertEquals(SearchText.EXACT, SearchText.score("Brumes", "brumes"))
        assertEquals(SearchText.WORD_START, SearchText.score("Route de nuit", "nuit"))
        assertEquals(SearchText.WORD_START, SearchText.score("Nuit au lac", "nui"))
        assertEquals(SearchText.CONTAINS, SearchText.score("Minuit à Paris", "nuit"))
        assertEquals(SearchText.NONE, SearchText.score("Brumes", "soleil"))
    }

    @Test
    fun `les accents et les majuscules ne comptent pas`() {
        assertEquals(SearchText.EXACT, SearchText.score("Noël à l'Élysée", "noel a l elysee"))
    }

    @Test
    fun `plusieurs mots dans le désordre`() {
        assertEquals(SearchText.CONTAINS, SearchText.score("photos/2025/Bretagne/plage.jpg", "bretagne 2025"))
    }

    @Test
    fun `la dictée retire les verbes`() {
        assertEquals("YouTube", SearchText.cleanSpoken("ouvre YouTube"))
        assertEquals("Noël", SearchText.cleanSpoken("photos de Noël"))
        assertEquals("Brumes", SearchText.cleanSpoken("Brumes"))
    }

    @Test
    fun `position à colorer`() {
        assertEquals(9..12, SearchText.highlight("Route de nuit", "nuit"))
        assertEquals(0..2, SearchText.highlight("Été indien", "ete"))
        assertNull(SearchText.highlight("Brumes", "soleil"))
    }

    @Test
    fun `historique sans doublon, le plus récent devant`() {
        assertEquals(listOf("Noël", "sable"), SearchHistoryRules.add(listOf("sable", "noel"), "Noël"))
        assertEquals(SearchHistoryRules.MAX, SearchHistoryRules.add(listOf("a", "b", "c", "d", "e"), "f").size)
    }
}
