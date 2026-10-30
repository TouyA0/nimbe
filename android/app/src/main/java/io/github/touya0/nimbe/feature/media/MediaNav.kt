package io.github.touya0.nimbe.feature.media

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.jellyfin.MediaItem

/** Fiche ouverte par-dessus les onglets. */
sealed interface MediaDestination {
    /** [parent] : la fiche d'où l'on vient (film similaire) ; Retour y ramène. */
    data class Movie(val item: MediaItem, val parent: Movie? = null) : MediaDestination

    /** [focusEpisodeId] : l'épisode à mettre sous le focus en arrivant (depuis « Continuer à regarder »). */
    data class Series(val seriesId: String, val seasonId: String? = null, val focusEpisodeId: String? = null) : MediaDestination

    companion object {
        fun of(item: MediaItem): MediaDestination? = when (item.kind) {
            ItemKind.Movie -> Movie(item)
            ItemKind.Series -> Series(item.id)
            ItemKind.Season -> item.seriesId?.let { Series(it, seasonId = item.id) }
            ItemKind.Episode -> item.seriesId?.let { Series(it, seasonId = item.seasonId, focusEpisodeId = item.id) }
            ItemKind.Other -> null
        }
    }
}

/**
 * Au retour d'une fiche, la carte d'où l'on venait reprend le focus. Dans une liste paresseuse,
 * elle n'existe que si elle est visible : on fait d'abord défiler la liste jusqu'à elle.
 */
@Composable
fun ScrollToRestore(state: LazyListState, items: List<MediaItem>, restoreFocusId: String?) {
    LaunchedEffect(restoreFocusId, items) {
        val index = items.indexOfFirst { it.id == restoreFocusId }
        if (index >= 0) state.scrollToItem(index)
    }
}

@Composable
fun ScrollToRestore(state: LazyGridState, items: List<MediaItem>, restoreFocusId: String?) {
    LaunchedEffect(restoreFocusId, items) {
        val index = items.indexOfFirst { it.id == restoreFocusId }
        if (index >= 0) state.scrollToItem(index)
    }
}
