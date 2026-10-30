package io.github.touya0.nimbe.data.search

import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import io.github.touya0.nimbe.data.streaming.StreamingTitle
import io.github.touya0.nimbe.feature.apps.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Les sources, dans l'ordre d'affichage : à score égal, la première donne le meilleur résultat. */
enum class SearchSource(val label: String) {
    Jellyfin("Films et séries"),
    Apps("Applis"),
    Streaming("Plateformes"),
    Gallery("Galerie"),
}

/** Un résultat ; [score] vient de [SearchText.score]. */
sealed interface SearchHit {
    val key: String
    val title: String
    val score: Int

    data class Media(val item: MediaItem, override val score: Int) : SearchHit {
        override val key get() = "media:${item.id}"
        override val title get() = item.name
    }

    data class Streaming(val value: StreamingTitle, override val score: Int) : SearchHit {
        override val key get() = "streaming:${value.id}"
        override val title get() = value.title
    }

    data class App(val app: InstalledApp, override val score: Int) : SearchHit {
        override val key get() = "app:${app.packageName}"
        override val title get() = app.label
    }

    data class Photo(val item: GalleryItem, override val title: String, override val score: Int) : SearchHit {
        override val key get() = "photo:${item.fileId}"
    }
}

sealed interface SourceState {
    data object Loading : SourceState
    data class Ready(val hits: List<SearchHit>) : SourceState

    /** Erreur, ou pas de réponse dans le temps imparti : les autres sources s'affichent quand même. */
    data object Failed : SourceState
}

/** Une source de résultats ; [load] reçoit le texte cherché. */
class SearchProvider(val source: SearchSource, val load: suspend (String) -> List<SearchHit>)

data class SearchResults(val query: String = "", val sources: Map<SearchSource, SourceState> = emptyMap()) {

    private fun ready(source: SearchSource) = (sources[source] as? SourceState.Ready)?.hits.orEmpty()

    /** Le meilleur score, et à score égal l'ordre des sources puis celui de la source. */
    val best: SearchHit?
        get() = SearchSource.entries.flatMap(::ready).maxByOrNull { it.score }

    /** Les résultats d'une source, sans celui mis en avant. */
    fun hits(source: SearchSource): List<SearchHit> = ready(source).filterNot { it.key == best?.key }

    val loading: Boolean get() = sources.values.any { it is SourceState.Loading }

    val failed: List<SearchSource> get() = SearchSource.entries.filter { sources[it] is SourceState.Failed }

    /** Toutes les sources ont répondu ou renoncé, et aucune n'a rien trouvé. */
    val empty: Boolean get() = !loading && best == null
}

object SearchEngine {

    /** Chaque source a 2 secondes : au-delà, on ne l'attend plus. */
    const val SOURCE_TIMEOUT_MS = 2_000L

    /**
     * Interroge toutes les sources en même temps et émet l'état à chaque réponse. Les appels réseau ne
     * s'interrompent pas d'un coup : le temps limite est donc surveillé à côté, sans attendre l'appel.
     */
    fun search(query: String, providers: List<SearchProvider>, timeoutMs: Long = SOURCE_TIMEOUT_MS): Flow<SearchResults> = channelFlow {
        val guard = Mutex()
        var results = SearchResults(query, providers.associate { it.source to SourceState.Loading })
        send(results)
        providers.forEach { provider ->
            launch {
                val answer = async { attempt(provider, query) }
                val state = select<SourceState> {
                    answer.onAwait { it }
                    onTimeout(timeoutMs) { SourceState.Failed }
                }
                answer.cancel()
                guard.withLock {
                    results = results.copy(sources = results.sources + (provider.source to state))
                    send(results)
                }
            }
        }
    }

    private suspend fun attempt(provider: SearchProvider, query: String): SourceState = try {
        SourceState.Ready(provider.load(query).sortedByDescending { it.score })
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        SourceState.Failed
    }
}
