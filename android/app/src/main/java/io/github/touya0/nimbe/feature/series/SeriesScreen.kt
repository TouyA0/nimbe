package io.github.touya0.nimbe.feature.series

import androidx.compose.runtime.Composable
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.feature.media.CatalogScreen
import io.github.touya0.nimbe.feature.media.CatalogState
import io.github.touya0.nimbe.feature.media.JellyfinSection
import io.github.touya0.nimbe.feature.media.MediaText

/** Onglet Séries : les séries Jellyfin (les plus récemment regardées d'abord) et celles des plateformes. */
@Composable
fun SeriesScreen(
    state: CatalogState,
    onOpenItem: (MediaItem) -> Unit,
    onPlayItem: (MediaItem) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
) {
    val genres = state.category?.jellyfin
    CatalogScreen(
        kind = TitleKind.Show,
        title = "Séries",
        state = state,
        jellyfin = JellyfinSection(
            rowTitle = "Mes séries sur Jellyfin",
            what = "tes séries",
            emptyTitle = "Aucune série",
            emptyDetail = "Ajoute des séries à une bibliothèque Jellyfin : elles apparaîtront ici.",
            pageKey = "series:${genres?.joinToString("|")}",
            loadPage = { auth, start, limit -> series(auth, start, limit, genres) },
            kicker = { item ->
                val unplayed = item.unplayedCount ?: 0
                when {
                    unplayed == 1 -> "1 ÉPISODE À VOIR"
                    unplayed > 1 -> "$unplayed ÉPISODES À VOIR"
                    else -> MediaText.added(item.dateCreated)
                }
            },
        ),
        onOpenItem = onOpenItem,
        onPlayItem = onPlayItem,
        restoreFocusId = restoreFocusId,
        onRestored = onRestored,
    )
}
