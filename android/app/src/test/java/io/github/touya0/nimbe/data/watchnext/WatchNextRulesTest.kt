package io.github.touya0.nimbe.data.watchnext

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchNextRulesTest {

    private fun program(id: Long, pkg: String = "com.disney.disneyplus", title: String = "Titre $id", at: Long = id, duration: Long = 1_000, type: Int = 0, wn: Int = 0) =
        WatchNextProgram(id, pkg, pkg, title, lastEngagement = at, durationMs = duration, programType = type, watchNextType = wn)

    private fun ids(vararg programs: WatchNextProgram, hidden: Set<String> = emptySet()) =
        WatchNextRules.select(programs.toList(), "io.github.touya0.nimbe", hidden).map { it.id }

    @Test
    fun `le plus récent d'abord`() = assertEquals(listOf(2L, 1L), ids(program(1), program(2)))

    @Test
    fun `sans durée, c'est du direct, et la musique ne compte pas`() =
        assertEquals(emptyList<Long>(), ids(program(1, duration = 0), program(2, type = 7), program(3, type = 6)))

    @Test
    fun `ni Nimbe, ni l'appli Jellyfin, ni les applis masquées`() = assertEquals(
        emptyList<Long>(),
        ids(program(1, "io.github.touya0.nimbe"), program(2, "org.jellyfin.androidtv"), program(3, "x"), hidden = setOf("x")),
    )

    @Test
    fun `en cours et à suivre seulement`() = assertEquals(listOf(2L, 1L), ids(program(1, wn = 0), program(2, wn = 1), program(3, wn = 2), program(4, wn = 3)))

    @Test
    fun `un titre revu n'a qu'une carte, la plus récente`() = assertEquals(listOf(2L), ids(program(1, title = "A"), program(2, title = "A")))

    @Test
    fun `une appli ne remplit pas la rangée`() {
        val twitch = (1L..6L).map { program(it, "tv.twitch.android.app") }.toTypedArray()
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), ids(*twitch, program(7)))
    }

    @Test
    fun `le texte d'épisode, rien pour un film`() {
        assertEquals("S2 É1 · Ouroboros", program(1).copy(season = "2", episode = "1", episodeTitle = "Ouroboros").episodeLine)
        assertEquals(null, program(1).episodeLine)
    }

    @Test
    fun `un titre retiré ne revient pas tant qu'on ne le reprend pas`() {
        val entries = WatchNextRules.dismiss(emptySet(), program(1, title = "Zootopie", at = 100))
        val select = { at: Long -> WatchNextRules.select(listOf(program(1, title = "Zootopie", at = at)), "nimbe", dismissed = entries).size }
        assertEquals(0, select(100))
        assertEquals(1, select(101))
    }

    @Test
    fun `retirer à nouveau remplace l'ancienne note`() {
        val once = WatchNextRules.dismiss(emptySet(), program(1, title = "Zootopie", at = 100))
        assertEquals(setOf(WatchNextRules.dismissEntry(program(1, title = "Zootopie", at = 200))), WatchNextRules.dismiss(once, program(1, title = "Zootopie", at = 200)))
    }

    @Test
    fun `un titre contenant une barre verticale reste reconnu`() {
        val entries = WatchNextRules.dismiss(emptySet(), program(1, title = "A | B", at = 100))
        assertEquals(true, WatchNextRules.isDismissed(program(1, title = "A | B", at = 100), entries))
    }
}
