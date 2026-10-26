package io.github.touya0.nimbe.feature.films

import androidx.compose.runtime.Composable
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.jellyfin.MovieSort
import io.github.touya0.nimbe.feature.media.FilterPill
import io.github.touya0.nimbe.feature.media.LibraryScreen
import io.github.touya0.nimbe.feature.media.MediaText

/** Onglet Films : la bibliothèque Jellyfin, triée selon [sort] (gardé par l'appelant, pour le retour d'une fiche). */
@Composable
fun FilmsScreen(
    sort: MovieSort,
    onSortChange: (MovieSort) -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
) {
    LibraryScreen(
        title = when (sort) {
            MovieSort.Recent -> "Ajouts récents"
            MovieSort.Unwatched -> "Pas encore vus"
            MovieSort.Alphabetical -> "Tous les films"
        },
        what = "tes films",
        emptyTitle = if (sort == MovieSort.Unwatched) "Tu as tout vu" else "Aucun film",
        emptyDetail = if (sort == MovieSort.Unwatched) {
            "Tous les films de ta bibliothèque sont marqués comme vus."
        } else {
            "Ajoute des films à une bibliothèque Jellyfin : ils apparaîtront ici."
        },
        pageKey = "movies:$sort",
        loadPage = { auth, start, limit -> movies(auth, sort, start, limit) },
        kicker = { item -> if (item.resumable) "EN COURS" else MediaText.added(item.dateCreated) },
        onOpenItem = onOpenItem,
        restoreFocusId = restoreFocusId,
        onRestored = onRestored,
        filters = {
            MovieSort.entries.forEach { option ->
                FilterPill(option.label, selected = option == sort, onClick = { onSortChange(option) })
            }
        },
    )
}
