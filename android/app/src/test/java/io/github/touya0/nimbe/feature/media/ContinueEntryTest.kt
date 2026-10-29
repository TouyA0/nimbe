package io.github.touya0.nimbe.feature.media

import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.streaming.StreamingOpen
import io.github.touya0.nimbe.data.streaming.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ContinueEntryTest {

    private fun open(id: String, kind: TitleKind, at: Long) =
        StreamingOpen("p", id, kind.name, id, "dnp", null, null, null, null, at)

    private val film = MediaItem("f1", ItemKind.Movie, "Brumes", lastPlayed = Instant.ofEpochMilli(3_000))
    private val episode = MediaItem("e1", ItemKind.Episode, "Épisode", seriesId = "s1", lastPlayed = Instant.ofEpochMilli(1_000))
    private val nextUp = MediaItem("e2", ItemKind.Episode, "Suivant", seriesId = "s2")

    @Test
    fun `les deux sources mêlées, la plus récente d'abord, les épisodes suivants à la fin`() {
        val merged = ContinueEntry.merge(listOf(film, episode, nextUp), listOf(open("tm1", TitleKind.Movie, 2_000), open("ts1", TitleKind.Show, 4_000)))
        assertEquals(listOf("streaming:ts1", "jellyfin:f1", "streaming:tm1", "jellyfin:e1", "jellyfin:e2"), merged.map { it.key })
    }

    @Test
    fun `onglet Films ou Séries, seulement son type`() {
        val opens = listOf(open("tm1", TitleKind.Movie, 2_000), open("ts1", TitleKind.Show, 4_000))
        assertEquals(listOf("jellyfin:f1", "streaming:tm1"), ContinueEntry.merge(listOf(film, episode), opens, TitleKind.Movie).map { it.key })
        assertEquals(listOf("streaming:ts1", "jellyfin:e1"), ContinueEntry.merge(listOf(film, episode), opens, TitleKind.Show).map { it.key })
    }

    @Test
    fun `un titre en cours des deux côtés n'apparaît qu'une fois, côté Jellyfin`() {
        val opens = listOf(open("tm1", TitleKind.Movie, 9_000))
        val merged = ContinueEntry.merge(listOf(film), opens, sameAs = { if (it.titleId == "tm1") film else null })
        assertEquals(listOf("jellyfin:f1"), merged.map { it.key })
    }
}
