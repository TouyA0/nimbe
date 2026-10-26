package io.github.touya0.nimbe.data.jellyfin

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class JellyfinJsonTest {

    private val episode = JSONObject(
        """
        {"Id":"ep4","Type":"Episode","Name":"Ce que cache la forêt","SeriesId":"brumes","SeriesName":"Brumes",
         "SeasonId":"s2","ParentIndexNumber":2,"IndexNumber":4,"RunTimeTicks":31200000000,
         "UserData":{"PlaybackPositionTicks":20400000000,"Played":false,"LastPlayedDate":"2026-10-25T21:10:03.1234567Z"},
         "ImageTags":{"Primary":"p1"},"BackdropImageTags":[],
         "ParentBackdropItemId":"brumes","ParentBackdropImageTags":["b1"],
         "SeriesPrimaryImageTag":"sp","DateCreated":"2026-10-20T08:00:00.0000000Z","Overview":null}
        """,
    )

    @Test
    fun `épisode en cours, images héritées de la série`() {
        val item = JellyfinJson.item(episode)
        assertEquals(ItemKind.Episode, item.kind)
        assertEquals("S02 · É04", item.episodeCode)
        assertEquals(18, item.remainingMinutes)
        assertEquals(0.6538f, item.progress, 0.001f)
        assertEquals(ImageRef("ep4", "p1", ImageType.Primary), item.primary)
        assertEquals(ImageRef("brumes", "b1", ImageType.Backdrop), item.backdrop)
        assertEquals(ImageRef("brumes", "sp", ImageType.Primary), item.seriesPrimary)
        assertEquals(Instant.parse("2026-10-25T21:10:03.1234567Z"), item.lastPlayed)
        assertNull(item.overview)
    }

    @Test
    fun `adresse d'image redimensionnée, sans jeton`() {
        val url = ImageRef("brumes", "b1", ImageType.Backdrop).url("http://192.168.1.182:8097", maxWidth = 1280)
        assertEquals("http://192.168.1.182:8097/Items/brumes/Images/Backdrop/0?tag=b1&quality=90&maxWidth=1280", url)
    }

    @Test
    fun `page avec total, ou tableau nu`() {
        val page = JellyfinJson.page(JSONObject("""{"Items":[{"Id":"a","Type":"Movie","Name":"Nord"}],"TotalRecordCount":120}"""))
        assertEquals(120, page.total)
        assertEquals("Nord", page.items.single().name)
        assertEquals(2, JellyfinJson.page(JSONArray("""[{"Id":"a","Type":"Movie"},{"Id":"b","Type":"Series"}]""")).items.size)
    }

    @Test
    fun `fiche avec langues, fichier et équipe`() {
        val detail = JellyfinJson.detail(
            JSONObject(
                """
                {"Id":"m","Type":"Movie","Name":"Sable rouge",
                 "People":[{"Id":"p1","Name":"Inès Varenne","Type":"Director"},{"Id":"p2","Name":"Léa Moreau","Role":"Nora","Type":"Actor","PrimaryImageTag":"t"}],
                 "MediaSources":[{"Size":14000000000,"MediaStreams":[
                   {"Type":"Video","Codec":"hevc","Height":2160},
                   {"Type":"Audio","Language":"fre"},{"Type":"Audio","Language":"eng"},
                   {"Type":"Subtitle","Language":"fre"},{"Type":"Subtitle","DisplayLanguage":"English"}]}]}
                """,
            ),
        )
        assertEquals("HEVC · 2160p", detail.video)
        assertEquals(listOf("Français", "Anglais"), detail.audioLanguages)
        assertEquals(listOf("Français", "English"), detail.subtitleLanguages)
        assertEquals(14_000_000_000, detail.sizeBytes)
        assertEquals("Nora", detail.people[1].role)
        assertEquals(ImageRef("p2", "t"), detail.people[1].image)
    }

    @Test
    fun `continuer à regarder, reprises récentes d'abord, sans doubler une série`() {
        fun item(id: String, series: String? = null, played: String? = null) =
            MediaItem(id = id, kind = if (series != null) ItemKind.Episode else ItemKind.Movie, name = id, seriesId = series, lastPlayed = played?.let(Instant::parse))
        val resume = listOf(item("film", played = "2026-10-20T20:00:00Z"), item("brumes-e4", "brumes", "2026-10-25T21:00:00Z"))
        val nextUp = listOf(item("brumes-e5", "brumes"), item("quai17-e1", "quai17"))
        val merged = JellyfinLibrary.mergeContinue(resume, nextUp).map { it.id }
        assertEquals(listOf("brumes-e4", "film", "quai17-e1"), merged)
    }

    @Test
    fun `progression nulle sans durée ni position`() {
        val item = MediaItem(id = "a", kind = ItemKind.Movie, name = "a")
        assertEquals(0f, item.progress)
        assertFalse(item.played)
        assertTrue(item.episodeCode == null)
    }
}
