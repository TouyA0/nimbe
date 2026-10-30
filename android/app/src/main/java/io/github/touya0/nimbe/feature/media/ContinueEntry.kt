package io.github.touya0.nimbe.feature.media

import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.streaming.StreamingOpen
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.data.search.SearchText
import io.github.touya0.nimbe.data.watchnext.WatchNextProgram

/** Une carte de « Continuer à regarder » : une reprise Jellyfin, un titre ouvert sur une plateforme, ou un contenu en cours dans une autre appli. */
sealed interface ContinueEntry {
    val key: String

    /** Moment de la dernière activité, en millisecondes ; 0 si inconnu (épisode suivant d'une série). */
    val at: Long

    data class Jellyfin(val item: MediaItem) : ContinueEntry {
        override val key get() = "jellyfin:${item.id}"
        override val at get() = item.lastPlayed?.toEpochMilli() ?: 0
    }

    data class Streaming(val open: StreamingOpen) : ContinueEntry {
        override val key get() = "streaming:${open.titleId}"
        override val at get() = open.openedAt
    }

    data class WatchNext(val program: WatchNextProgram) : ContinueEntry {
        override val key get() = "watchnext:${program.id}"
        override val at get() = program.lastEngagement
    }

    companion object {
        /**
         * Les sources mêlées, de la plus récente à la plus ancienne. Les épisodes suivants Jellyfin (sans date)
         * gardent leur ordre, après les dates connues. [kind] : seulement les films, ou seulement les séries ; null pour tout.
         * Un titre ouvert sur une plateforme qui est aussi en cours sur Jellyfin ([sameAs]) n'apparaît qu'une fois, côté Jellyfin.
         * Un titre ouvert sur une plateforme et présent dans [watchNext] n'apparaît que côté Watch Next, qui connaît la progression.
         * [watchNext] ne sert qu'à l'accueil : les applis ne disent pas toujours si c'est un film ou une série.
         */
        fun merge(
            jellyfin: List<MediaItem>,
            streaming: List<StreamingOpen>,
            kind: TitleKind? = null,
            limit: Int = 16,
            sameAs: (StreamingOpen) -> MediaItem? = { null },
            watchNext: List<WatchNextProgram> = emptyList(),
        ): List<ContinueEntry> {
            val items = jellyfin.filter { item ->
                when (kind) {
                    null -> true
                    TitleKind.Movie -> item.kind == ItemKind.Movie
                    TitleKind.Show -> item.kind == ItemKind.Episode || item.kind == ItemKind.Series
                }
            }
            val jellyfinIds = items.flatMap { listOfNotNull(it.id, it.seriesId) }.toSet()
            val watchNextTitles = watchNext.map { SearchText.normalize(it.title) }.toSet()
            val opens = streaming.filter {
                (kind == null || it.titleKind == kind) && sameAs(it)?.id !in jellyfinIds && SearchText.normalize(it.title) !in watchNextTitles
            }
            return (items.map(::Jellyfin) + opens.map(::Streaming) + watchNext.map(::WatchNext))
                .withIndex()
                .sortedWith(compareByDescending<IndexedValue<ContinueEntry>> { it.value.at }.thenBy { it.index })
                .map { it.value }
                .take(limit)
        }
    }
}
