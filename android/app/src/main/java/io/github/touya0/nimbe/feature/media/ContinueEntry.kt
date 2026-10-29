package io.github.touya0.nimbe.feature.media

import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.streaming.StreamingOpen
import io.github.touya0.nimbe.data.streaming.TitleKind

/** Une carte de « Continuer à regarder » : une reprise Jellyfin, ou un titre ouvert sur une plateforme. */
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

    companion object {
        /**
         * Les deux sources mêlées, de la plus récente à la plus ancienne. Les épisodes suivants Jellyfin (sans date)
         * gardent leur ordre, après les dates connues. [kind] : seulement les films, ou seulement les séries ; null pour tout.
         * Un titre ouvert sur une plateforme qui est aussi en cours sur Jellyfin ([sameAs]) n'apparaît qu'une fois, côté Jellyfin.
         */
        fun merge(
            jellyfin: List<MediaItem>,
            streaming: List<StreamingOpen>,
            kind: TitleKind? = null,
            limit: Int = 16,
            sameAs: (StreamingOpen) -> MediaItem? = { null },
        ): List<ContinueEntry> {
            val items = jellyfin.filter { item ->
                when (kind) {
                    null -> true
                    TitleKind.Movie -> item.kind == ItemKind.Movie
                    TitleKind.Show -> item.kind == ItemKind.Episode || item.kind == ItemKind.Series
                }
            }
            val jellyfinIds = items.flatMap { listOfNotNull(it.id, it.seriesId) }.toSet()
            val opens = streaming.filter { (kind == null || it.titleKind == kind) && sameAs(it)?.id !in jellyfinIds }
            return (items.map(::Jellyfin) + opens.map(::Streaming))
                .withIndex()
                .sortedWith(compareByDescending<IndexedValue<ContinueEntry>> { it.value.at }.thenBy { it.index })
                .map { it.value }
                .take(limit)
        }
    }
}
