package io.github.touya0.nimbe.data.streaming

import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingRulesTest {

    private val installed = setOf("com.disney.disneyplus", "com.wbd.stream", "com.google.android.youtube.tv")
    private val adult = Profile("a", "Quentin", 0, ProfileKind.Adult)

    private val both = setOf("dnp", "mxx")

    @Test
    fun `sans choix, aucune plateforme, même si leur appli est installée`() {
        assertTrue(StreamingRules.visible(null, installed, adult).isEmpty())
        assertEquals(listOf(StreamingService.Disney, StreamingService.HboMax), StreamingRules.visible(both, installed, adult))
    }

    @Test
    fun `un abonnement sans appli n'apparaît pas, un choix explicite retire une plateforme`() {
        assertEquals(listOf(StreamingService.HboMax), StreamingRules.visible(setOf("mxx", "nfx"), installed, adult))
        assertTrue(StreamingRules.visible(emptySet(), installed, adult).isEmpty())
    }

    @Test
    fun `l'invité ne voit pas les comptes de la famille, sauf choix contraire`() {
        val guest = Profile("g", "Invité", 1, ProfileKind.Guest)
        assertTrue(StreamingRules.visible(both, installed, guest).isEmpty())
        val open = guest.copy(hiddenApps = setOf("com.wbd.stream"))
        assertEquals(listOf(StreamingService.Disney), StreamingRules.visible(both, installed, open))
    }

    @Test
    fun `l'enfant ne voit que les plateformes de ses applis autorisées, en tous publics`() {
        val child = Profile("c", "Léa", 2, ProfileKind.Child, allowedApps = setOf("com.disney.disneyplus"))
        assertEquals(listOf(StreamingService.Disney), StreamingRules.visible(both, installed, child))
        assertTrue(StreamingRules.kidsOnly(child))
        assertTrue(StreamingRules.visible(both, installed, child.copy(allowedApps = null)).isEmpty())
    }
}
