package io.github.touya0.nimbe.feature.films

import androidx.compose.runtime.Composable
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.jellyfin.MovieSort
import io.github.touya0.nimbe.data.streaming.Category
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.feature.media.CatalogScreen
import io.github.touya0.nimbe.feature.media.CatalogState
import io.github.touya0.nimbe.feature.media.SortChoice
import io.github.touya0.nimbe.feature.media.JellyfinSection
import io.github.touya0.nimbe.feature.media.JellyfinShelf
import io.github.touya0.nimbe.feature.media.MediaText

/** Les rangées de « À la une », de la plus utile à la plus large. Une rangée trop courte ne s'affiche pas. */
private val FILM_SHELVES = listOf(
    shelf("Ajoutés récemment", MovieSort.Recent),
    shelf("Pas encore vus", MovieSort.Unwatched),
    shelf("Les mieux notés", MovieSort.TopRated),
    shelf("Pour rire", MovieSort.TopRated, Category.Comedy),
    shelf("Action et aventure", MovieSort.TopRated, Category.Action),
    shelf("Science-fiction", MovieSort.TopRated, Category.SciFi),
    shelf("Fantastique", MovieSort.TopRated, Category.Fantasy),
    shelf("Animation", MovieSort.TopRated, Category.Animation),
    shelf("En famille", MovieSort.TopRated, Category.Family),
)

private fun shelf(title: String, sort: MovieSort, category: Category? = null) = JellyfinShelf(title, "movies:$sort:${category?.code}") { auth, start, limit ->
    movies(auth, sort, start, limit, category?.jellyfin)
}

/**
 * Onglet Films : la bibliothèque Jellyfin, triée selon [sort], et les films des plateformes de streaming.
 * [sort] et [state] sont gardés par l'appelant, pour le retour d'une fiche.
 */
@Composable
fun FilmsScreen(
    sort: MovieSort,
    onSortChange: (MovieSort) -> Unit,
    state: CatalogState,
    onOpenItem: (MediaItem) -> Unit,
    onPlayItem: (MediaItem) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
) {
    val genres = state.category?.jellyfin
    CatalogScreen(
        kind = TitleKind.Movie,
        title = "Films",
        state = state,
        jellyfin = JellyfinSection(
            rowTitle = when (sort) {
                MovieSort.Recent -> "Ajoutés récemment sur Jellyfin"
                MovieSort.Unwatched -> "Pas encore vus sur Jellyfin"
                MovieSort.Alphabetical -> "Sur ton Jellyfin"
                MovieSort.TopRated -> "Les mieux notés sur Jellyfin"
                MovieSort.Newest -> "Les plus récents sur Jellyfin"
            },
            what = "tes films",
            emptyTitle = if (sort == MovieSort.Unwatched) "Tu as tout vu" else "Aucun film",
            emptyDetail = if (sort == MovieSort.Unwatched) {
                "Tous les films de ta bibliothèque sont marqués comme vus."
            } else {
                "Ajoute des films à une bibliothèque Jellyfin : ils apparaîtront ici."
            },
            pageKey = "movies:$sort:${genres?.joinToString("|")}",
            loadPage = { auth, start, limit -> movies(auth, sort, start, limit, genres) },
            kicker = { item -> if (item.resumable) "EN COURS" else MediaText.added(item.dateCreated) },
            sort = SortChoice(MovieSort.entries.map { it.label }, sort.ordinal) { onSortChange(MovieSort.entries[it]) },
            shelves = FILM_SHELVES,
        ),
        onOpenItem = onOpenItem,
        onPlayItem = onPlayItem,
        restoreFocusId = restoreFocusId,
        onRestored = onRestored,
    )
}
