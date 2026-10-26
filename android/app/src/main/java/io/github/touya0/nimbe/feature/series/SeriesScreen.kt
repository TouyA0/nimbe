package io.github.touya0.nimbe.feature.series

import androidx.compose.runtime.Composable
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.feature.media.LibraryScreen
import io.github.touya0.nimbe.feature.media.MediaText

/** Onglet Séries : les séries Jellyfin, les plus récemment regardées d'abord. */
@Composable
fun SeriesScreen(onOpenItem: (MediaItem) -> Unit, restoreFocusId: String?, onRestored: () -> Unit) {
    LibraryScreen(
        title = "Mes séries",
        what = "tes séries",
        emptyTitle = "Aucune série",
        emptyDetail = "Ajoute des séries à une bibliothèque Jellyfin : elles apparaîtront ici.",
        pageKey = "series",
        loadPage = { auth, start, limit -> series(auth, start, limit) },
        kicker = { item ->
            val unplayed = item.unplayedCount ?: 0
            when {
                unplayed == 1 -> "1 ÉPISODE À VOIR"
                unplayed > 1 -> "$unplayed ÉPISODES À VOIR"
                else -> MediaText.added(item.dateCreated)
            }
        },
        onOpenItem = onOpenItem,
        restoreFocusId = restoreFocusId,
        onRestored = onRestored,
    )
}
