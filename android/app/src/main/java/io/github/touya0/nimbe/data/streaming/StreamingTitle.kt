package io.github.touya0.nimbe.data.streaming

import org.json.JSONArray
import org.json.JSONObject

/**
 * Où regarder un titre sur une plateforme. [deepLink] : lien `intent://` prévu pour Android TV
 * (souvent la lecture directe) ; [webUrl] : adresse https de la plateforme (la fiche chez Disney+,
 * la lecture chez HBO Max). L'un ou l'autre peut manquer.
 */
data class StreamingOffer(val service: StreamingService, val webUrl: String?, val deepLink: String?) {

    /**
     * La page web ouvre autre chose que la lecture (une fiche), et l'appli sait l'ouvrir ([StreamingService.pageLinks]) :
     * on peut proposer les deux boutons.
     */
    val hasSeparatePage: Boolean
        get() = service.pageLinks && webUrl != null && deepLink != null &&
            pathOf(webUrl) != pathOf(deepLink.removePrefix("intent:").let { "https:$it" })

    private fun pathOf(url: String): String = url.substringAfter("://").substringBefore('?').substringBefore('#').substringAfter('/')
}

data class CastMember(val name: String, val role: String?)

/**
 * Un film ou une série du catalogue des plateformes, tel que JustWatch le décrit.
 * [poster] et [backdrop] sont des modèles d'adresse (voir [JustWatchImages]).
 * [certification] : classification française (« TP » tous publics, « 12 », « 16 »…).
 */
data class StreamingTitle(
    val id: String,
    val kind: TitleKind,
    val title: String,
    val year: Int? = null,
    val overview: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val certification: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val tmdbId: String? = null,
    val seasons: Int? = null,
    val offers: List<StreamingOffer> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val directors: List<String> = emptyList(),
    /** Note du public (TMDB), sur 10. */
    val score: Float? = null,
) {
    /** Plateformes qui le proposent, sans doublon, dans l'ordre de JustWatch. */
    val services: List<StreamingService> get() = offers.map { it.service }.distinct()

    fun offer(service: StreamingService): StreamingOffer? =
        offers.firstOrNull { it.service == service && it.deepLink != null } ?: offers.firstOrNull { it.service == service }

    /** Noms français des catégories connues (« Action, Comédie »). */
    val categoryLabels: List<String> get() = genres.mapNotNull { Category.fromCode(it)?.label }
}

/** Une page du catalogue ; [next] : curseur de la page suivante, null à la fin. */
data class StreamingPage(val items: List<StreamingTitle>, val total: Int, val next: String?)

/** Adresses des images JustWatch : le modèle contient {profile} (taille) et {format}. */
object JustWatchImages {
    private const val HOST = "https://images.justwatch.com"

    /** Affiche : s276 pour une carte (230 pixels de large en 1080p), s332 pour une fiche. */
    fun poster(template: String?, large: Boolean = false): String? = template?.let { url(it, if (large) "s332" else "s276") }

    /** Fond : s1440 pour l'écran entier, s640 pour une vignette. */
    fun backdrop(template: String?, large: Boolean = true): String? = template?.let { url(it, if (large) "s1440" else "s640") }

    private fun url(template: String, profile: String) = HOST + template.replace("{profile}", profile).replace("{format}", "webp")
}

/**
 * Requêtes GraphQL de JustWatch et lecture de leurs réponses. L'API n'est pas officielle :
 * tout champ absent ou inattendu est ignoré plutôt que de faire échouer l'écran.
 */
object JustWatchJson {

    const val COUNTRY = "FR"
    private const val LANGUAGE = "fr"

    private val CONTENT = """
        title originalReleaseYear shortDescription runtime ageCertification posterUrl
        backdrops { backdropUrl } genres { shortName } externalIds { tmdbId } scoring { tmdbScore }
    """.trimIndent()

    private val OFFERS = """
        offers(country: $COUNTRY, platform: WEB, filter: { monetizationTypes: [FLATRATE], packages: ${'$'}packages }) {
          package { shortName } standardWebURL deeplinkURL(platform: ANDROID_TV)
        }
    """.trimIndent()

    /** Catalogue filtré et trié, par pages ; [JustWatchApi.catalog] fournit les variables. */
    val CATALOG = """
        query Catalog(${'$'}filter: TitleFilter, ${'$'}sort: PopularTitlesSorting!, ${'$'}first: Int!, ${'$'}after: String, ${'$'}packages: [String!]!) {
          popularTitles(country: $COUNTRY, first: ${'$'}first, after: ${'$'}after, filter: ${'$'}filter, sortBy: ${'$'}sort) {
            totalCount pageInfo { endCursor hasNextPage }
            edges { node { id objectType content(country: $COUNTRY, language: "$LANGUAGE") { $CONTENT } $OFFERS ... on Show { totalSeasonCount } } }
          }
        }
    """.trimIndent()

    /** Un titre avec sa distribution, pour la fiche. */
    val DETAIL = """
        query Detail(${'$'}id: ID!, ${'$'}packages: [String!]!) {
          node(id: ${'$'}id) {
            id
            ... on MovieOrShow {
              objectType
              content(country: $COUNTRY, language: "$LANGUAGE") { $CONTENT credits { role name characterName } }
              $OFFERS
            }
            ... on Show { totalSeasonCount }
          }
        }
    """.trimIndent()

    /**
     * Variables du catalogue. Sans plateforme, rien n'est demandé : l'appelant n'affiche alors rien.
     * [kids] : seulement les titres tous publics (profil Enfant).
     */
    fun catalogVariables(
        kind: TitleKind?,
        services: List<StreamingService>,
        category: Category?,
        sort: StreamingSort,
        kids: Boolean,
        first: Int,
        after: String?,
        query: String? = null,
    ): JSONObject {
        val codes = JSONArray(services.map { it.code })
        val filter = JSONObject()
            .put("packages", codes)
            .put("monetizationTypes", JSONArray(listOf("FLATRATE")))
        kind?.let { filter.put("objectTypes", JSONArray(listOf(it.justWatch))) }
        category?.let { filter.put("genres", JSONArray(listOf(it.code))) }
        if (kids) filter.put("ageCertifications", JSONArray(listOf("TP")))
        query?.let { filter.put("searchQuery", it) }
        return JSONObject()
            .put("filter", filter)
            .put("sort", sort.justWatch)
            .put("first", first)
            .put("after", after ?: JSONObject.NULL)
            .put("packages", codes)
    }

    fun detailVariables(id: String, services: List<StreamingService>): JSONObject =
        JSONObject().put("id", id).put("packages", JSONArray(services.map { it.code }))

    fun page(response: JSONObject): StreamingPage {
        val titles = response.optJSONObject("data")?.optJSONObject("popularTitles") ?: return StreamingPage(emptyList(), 0, null)
        val items = titles.optJSONArray("edges").objects().mapNotNull { edge -> edge.optJSONObject("node")?.let(::title) }
        val info = titles.optJSONObject("pageInfo")
        val next = info?.text("endCursor")?.takeIf { info.optBoolean("hasNextPage") }
        return StreamingPage(items, titles.optInt("totalCount", items.size), next)
    }

    /** null si le titre n'existe plus. */
    fun detail(response: JSONObject): StreamingTitle? = response.optJSONObject("data")?.optJSONObject("node")?.let(::title)

    fun title(node: JSONObject): StreamingTitle? {
        val id = node.text("id") ?: return null
        val kind = when (node.text("objectType")) {
            "MOVIE" -> TitleKind.Movie
            "SHOW" -> TitleKind.Show
            else -> return null
        }
        val content = node.optJSONObject("content") ?: return null
        val credits = content.optJSONArray("credits").objects()
        return StreamingTitle(
            id = id,
            kind = kind,
            title = content.text("title")?.trim() ?: return null,
            year = content.int("originalReleaseYear"),
            overview = content.text("shortDescription"),
            runtimeMinutes = content.int("runtime")?.takeIf { it > 0 },
            genres = content.optJSONArray("genres").objects().mapNotNull { it.text("shortName") },
            certification = content.text("ageCertification"),
            poster = content.text("posterUrl"),
            backdrop = content.optJSONArray("backdrops").objects().firstNotNullOfOrNull { it.text("backdropUrl") },
            tmdbId = content.optJSONObject("externalIds")?.text("tmdbId"),
            seasons = node.int("totalSeasonCount")?.takeIf { it > 0 },
            offers = node.optJSONArray("offers").objects().mapNotNull { offer ->
                val service = StreamingService.fromCode(offer.optJSONObject("package")?.text("shortName")) ?: return@mapNotNull null
                StreamingOffer(service, offer.text("standardWebURL"), offer.text("deeplinkURL"))
            }.distinctBy { it.service to it.deepLink },
            cast = credits.filter { it.text("role") == "ACTOR" }.take(20).mapNotNull { person ->
                person.text("name")?.let { CastMember(it, person.text("characterName")) }
            },
            directors = credits.filter { it.text("role") == "DIRECTOR" }.mapNotNull { it.text("name") },
            score = content.optJSONObject("scoring")?.takeUnless { it.isNull("tmdbScore") }?.optDouble("tmdbScore")?.takeIf { it > 0 }?.toFloat(),
        )
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
    private fun JSONObject.int(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
    private fun JSONArray?.objects(): List<JSONObject> = this?.let { array -> List(array.length()) { array.optJSONObject(it) }.filterNotNull() }.orEmpty()
}
