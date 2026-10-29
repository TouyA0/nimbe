package io.github.touya0.nimbe.data.streaming

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Réponses réduites, copiées de vraies réponses de JustWatch (29/10/2026). */
class JustWatchJsonTest {

    private val page = JSONObject(
        """
        {"data":{"popularTitles":{"totalCount":485,"pageInfo":{"endCursor":"Mg==","hasNextPage":true},"edges":[
          {"node":{"id":"tm1422016","objectType":"MOVIE","content":{"title":"Zootopie 2","originalReleaseYear":2025,
            "shortDescription":"Judy Hopps et Nick Wilde…","runtime":108,"ageCertification":"TP",
            "posterUrl":"/poster/340617694/{profile}/zootopie-2.{format}",
            "backdrops":[{"backdropUrl":"/backdrop/341180063/{profile}/zootopie-2.{format}"}],
            "genres":[{"shortName":"ani"},{"shortName":"cmy"},{"shortName":"zzz"}],"externalIds":{"tmdbId":"1084242"}},
           "offers":[
             {"package":{"shortName":"dnp"},"standardWebURL":"https://www.disneyplus.com/browse/entity-ec9f",
              "deeplinkURL":"intent://www.disneyplus.com/play/ec9f#Intent;launchFlags=0x00800000;scheme=https;end"},
             {"package":{"shortName":"dnp"},"standardWebURL":"https://www.disneyplus.com/browse/entity-ec9f",
              "deeplinkURL":"intent://www.disneyplus.com/play/ec9f#Intent;launchFlags=0x00800000;scheme=https;end"},
             {"package":{"shortName":"xyz"},"standardWebURL":"https://example.com/x","deeplinkURL":null}]}},
          {"node":{"id":"ts226904","objectType":"SHOW","totalSeasonCount":2,"content":{"title":"The Last of Us ","originalReleaseYear":2023,
            "ageCertification":"","posterUrl":null,"backdrops":[],"genres":[],"externalIds":{"tmdbId":"100088"}},
           "offers":[{"package":{"shortName":"mxx"},
             "standardWebURL":"https://play.hbomax.com/video/watch/dae9?utm_source=universal_search",
             "deeplinkURL":"intent://play.hbomax.com/video/watch/dae9?utm_source=universal_search#Intent;launchFlags=0x00800000;scheme=https;end"}]}},
          {"node":{"id":"tse1","objectType":"SHOW_EPISODE","content":{"title":"Épisode"}}}
        ]}}}
        """,
    )

    @Test
    fun `page du catalogue, titres et curseur suivant`() {
        val result = JustWatchJson.page(page)
        assertEquals(485, result.total)
        assertEquals("Mg==", result.next)
        // Le type inconnu (épisode) est ignoré plutôt que de faire échouer la liste.
        assertEquals(listOf("tm1422016", "ts226904"), result.items.map { it.id })
    }

    @Test
    fun `film complet, offres sans doublon ni plateforme inconnue`() {
        val film = JustWatchJson.page(page).items.first()
        assertEquals(TitleKind.Movie, film.kind)
        assertEquals("Zootopie 2", film.title)
        assertEquals(2025, film.year)
        assertEquals(108, film.runtimeMinutes)
        assertEquals("1084242", film.tmdbId)
        assertEquals(listOf("Animation", "Comédie"), film.categoryLabels)
        assertEquals(listOf(StreamingService.Disney), film.services)
        assertEquals(1, film.offers.size)
    }

    @Test
    fun `série, titre nettoyé, saisons et classification vide`() {
        val show = JustWatchJson.page(page).items[1]
        assertEquals(TitleKind.Show, show.kind)
        assertEquals("The Last of Us", show.title)
        assertEquals(2, show.seasons)
        assertNull(show.certification)
        assertNull(show.poster)
    }

    @Test
    fun `Disney+ a une fiche distincte de la lecture, HBO Max non`() {
        val (film, show) = JustWatchJson.page(page).items
        assertTrue(film.offer(StreamingService.Disney)!!.hasSeparatePage)
        assertFalse(show.offer(StreamingService.HboMax)!!.hasSeparatePage)
    }

    @Test
    fun `fin du catalogue, sans page suivante`() {
        val last = JSONObject("""{"data":{"popularTitles":{"totalCount":0,"pageInfo":{"endCursor":"Mg==","hasNextPage":false},"edges":[]}}}""")
        assertNull(JustWatchJson.page(last).next)
        assertEquals(0, JustWatchJson.page(JSONObject("{}")).total)
    }

    @Test
    fun `fiche avec distribution et réalisation`() {
        val detail = JSONObject(
            """
            {"data":{"node":{"id":"tm1422016","objectType":"MOVIE","content":{"title":"Zootopie 2","credits":[
              {"role":"ACTOR","name":"Ginnifer Goodwin","characterName":"Judy Hopps (voice)"},
              {"role":"DIRECTOR","name":"Jared Bush","characterName":""}]},"offers":[]}}}
            """,
        )
        val title = JustWatchJson.detail(detail)!!
        assertEquals(listOf(CastMember("Ginnifer Goodwin", "Judy Hopps (voice)")), title.cast)
        assertEquals(listOf("Jared Bush"), title.directors)
        assertNull(JustWatchJson.detail(JSONObject("""{"data":{"node":null}}""")))
    }

    @Test
    fun `variables du catalogue pour un profil Enfant`() {
        val variables = JustWatchJson.catalogVariables(
            TitleKind.Movie, listOf(StreamingService.Disney, StreamingService.HboMax), Category.Animation,
            StreamingSort.Recent, kids = true, first = 40, after = null,
        )
        val filter = variables.getJSONObject("filter")
        assertEquals("[\"dnp\",\"mxx\"]", filter.getJSONArray("packages").toString())
        assertEquals("[\"MOVIE\"]", filter.getJSONArray("objectTypes").toString())
        assertEquals("[\"ani\"]", filter.getJSONArray("genres").toString())
        assertEquals("[\"TP\"]", filter.getJSONArray("ageCertifications").toString())
        assertEquals("RELEASE_YEAR", variables.getString("sort"))
        assertTrue(variables.isNull("after"))
    }

    @Test
    fun `adresses des images JustWatch`() {
        assertEquals(
            "https://images.justwatch.com/poster/340617694/s166/zootopie-2.webp",
            JustWatchImages.poster("/poster/340617694/{profile}/zootopie-2.{format}"),
        )
        assertEquals(
            "https://images.justwatch.com/backdrop/1/s1440/x.webp",
            JustWatchImages.backdrop("/backdrop/1/{profile}/x.{format}"),
        )
        assertNull(JustWatchImages.poster(null))
    }
}
