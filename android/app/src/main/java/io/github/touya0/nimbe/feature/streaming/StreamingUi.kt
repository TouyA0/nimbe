package io.github.touya0.nimbe.feature.streaming

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.streaming.StreamingCatalog
import io.github.touya0.nimbe.data.streaming.StreamingException
import io.github.touya0.nimbe.data.streaming.StreamingOpen
import io.github.touya0.nimbe.data.streaming.StreamingService
import io.github.touya0.nimbe.data.streaming.StreamingTitle
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.feature.media.Load
import io.github.touya0.nimbe.feature.media.Loader

/**
 * Ce dont les écrans ont besoin pour les plateformes : le catalogue, les plateformes que le profil voit,
 * ses titres ouverts récemment, et les actions (ouvrir une fiche, lancer l'appli, options d'un titre ouvert).
 * [jellyfinIndex] : films et séries Jellyfin par identifiant TMDB, vide tant qu'il n'est pas chargé.
 */
@Stable
class StreamingUi(
    val catalog: StreamingCatalog,
    val services: List<StreamingService>,
    val kids: Boolean,
    val history: List<StreamingOpen>,
    val jellyfinIndex: Map<String, MediaItem>,
    val onOpenTitle: (StreamingTitle) -> Unit,
    val onLaunch: (StreamingTitle, StreamingService, Boolean) -> Unit,
    /** Options d'un titre de « Continuer à regarder » (le relancer, le retirer). */
    val onMenu: (StreamingOpen, FocusRequester) -> Unit,
    val onOpenSettings: (() -> Unit)?,
) {
    /** Le même titre dans la bibliothèque Jellyfin, s'il y est. */
    fun inJellyfin(title: StreamingTitle): MediaItem? {
        val tmdb = title.tmdbId ?: return null
        return jellyfinIndex[(if (title.kind == TitleKind.Movie) "Movie" else "Series") + ":" + tmdb]
    }
}

val LocalStreaming = compositionLocalOf<StreamingUi?> { null }

/**
 * Charge des données du catalogue. La dernière réponse connue ([cacheKey]) s'affiche tout de suite ;
 * un échec ensuite la garde plutôt que d'afficher une erreur.
 */
@Composable
fun <T> rememberStreaming(cacheKey: String?, vararg keys: Any?, load: suspend StreamingCatalog.() -> T): Loader<T> {
    val ui = LocalStreaming.current
    var attempt by remember { mutableIntStateOf(0) }
    val cached = remember(ui?.catalog, cacheKey) { cacheKey?.let { ui?.catalog?.peek<T>(it) } }
    val state by produceState<Load<T>>(cached?.let { Load.Ready(it) } ?: Load.Loading, ui?.catalog, attempt, *keys) {
        val catalog = ui?.catalog ?: return@produceState
        if (value !is Load.Ready) value = Load.Loading
        value = try {
            Load.Ready(catalog.load())
        } catch (e: StreamingException) {
            value as? Load.Ready ?: Load.Failed(e.message.orEmpty(), reconnect = false)
        }
    }
    return Loader(state, retry = { attempt++ })
}
