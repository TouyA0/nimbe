package io.github.touya0.nimbe.data.search

import io.github.touya0.nimbe.feature.apps.AppCategory
import io.github.touya0.nimbe.feature.apps.InstalledApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    private fun app(name: String, score: Int) = SearchHit.App(InstalledApp(name, name, AppCategory.entries.first(), null, removable = false), score)

    @Test
    fun `une source lente ou en panne n'empêche pas les autres`() = runBlocking {
        val states = withTimeout(5_000) {
            SearchEngine.search(
                "x",
                listOf(
                    SearchProvider(SearchSource.Apps) { listOf(app("a", 2)) },
                    SearchProvider(SearchSource.Jellyfin) {
                        delay(60_000)
                        emptyList()
                    },
                    SearchProvider(SearchSource.Streaming) { throw IllegalStateException("panne") },
                ),
                timeoutMs = 100,
            ).toList()
        }
        val last = states.last()
        assertFalse(last.loading)
        assertEquals("a", last.best?.title)
        assertEquals(listOf(SearchSource.Jellyfin, SearchSource.Streaming), last.failed)
        assertTrue(states.first().loading)
    }

    @Test
    fun `le meilleur résultat sort de son groupe`() {
        val results = SearchResults(
            "x",
            mapOf(SearchSource.Apps to SourceState.Ready(listOf(app("exact", 3), app("autre", 1)))),
        )
        assertEquals("exact", results.best?.title)
        assertEquals(listOf("autre"), results.hits(SearchSource.Apps).map { it.title })
    }

    @Test
    fun `à score égal, l'ordre des sources décide`() {
        val results = SearchResults(
            "x",
            mapOf(
                SearchSource.Apps to SourceState.Ready(listOf(app("appli", 2))),
                SearchSource.Jellyfin to SourceState.Ready(listOf(app("film", 2))),
            ),
        )
        assertEquals("film", results.best?.title)
    }
}
