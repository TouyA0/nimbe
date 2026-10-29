package io.github.touya0.nimbe.data.streaming

import io.github.touya0.nimbe.core.storage.CachedResponse
import io.github.touya0.nimbe.core.storage.CachedResponseDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class StreamingException(message: String) : IOException(message)

/**
 * Client de l'API GraphQL publique de JustWatch : aucun compte ni clé, rien de secret ne part du stick.
 * Elle n'est pas officielle et peut changer ; une erreur remonte en [StreamingException].
 */
class JustWatchApi(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun query(query: String, variables: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", query).put("variables", variables).toString().toRequestBody(JSON)
        val request = Request.Builder().url(ENDPOINT).post(body).header("User-Agent", "Nimbe (Android TV)").build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw StreamingException("JustWatch a répondu ${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                if (json.has("errors") && !json.has("data")) throw StreamingException("Réponse JustWatch refusée")
                json
            }
        } catch (e: JSONException) {
            throw StreamingException("Réponse JustWatch illisible")
        } catch (e: StreamingException) {
            throw e
        } catch (e: IOException) {
            throw StreamingException("JustWatch injoignable")
        }
    }

    private companion object {
        const val ENDPOINT = "https://apis.justwatch.com/graphql"
        val JSON = "application/json".toMediaType()
    }
}

/**
 * Catalogue des plateformes de streaming. Comme pour Jellyfin, chaque réponse est gardée en mémoire
 * (retour instantané d'une fiche) et dans la base : sans internet, on relit la dernière réponse reçue.
 * Les clés commencent par « justwatch| » ; elles ne contiennent que des filtres, rien de personnel.
 */
class StreamingCatalog(private val api: JustWatchApi, private val cache: CachedResponseDao) {

    private val memory = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> peek(key: String): T? = memory[key] as T?

    suspend fun catalog(
        kind: TitleKind?,
        services: List<StreamingService>,
        category: Category?,
        sort: StreamingSort,
        kids: Boolean,
        after: String? = null,
        first: Int = PAGE_SIZE,
    ): StreamingPage {
        if (services.isEmpty()) return StreamingPage(emptyList(), 0, null)
        val variables = JustWatchJson.catalogVariables(kind, services, category, sort, kids, first, after)
        val key = pageKey(kind, services, category, sort, kids, after, first)
        return remember(key) { JustWatchJson.page(fetch(JustWatchJson.CATALOG, variables)) }
    }

    suspend fun detail(id: String, services: List<StreamingService>): StreamingTitle? {
        // Seul un titre trouvé va en mémoire : [peek] ne rend jamais autre chose qu'un titre.
        val title = JustWatchJson.detail(fetch(JustWatchJson.DETAIL, JustWatchJson.detailVariables(id, services)))
        if (title != null) memory[detailKey(id, services)] = title
        return title
    }

    /**
     * Le titre des plateformes qui correspond à un film ou une série Jellyfin : recherche par nom,
     * puis même identifiant TMDB (sinon même titre et même année). null si aucune plateforme ne l'a.
     */
    suspend fun find(name: String, year: Int?, tmdbId: String?, kind: TitleKind, services: List<StreamingService>): StreamingTitle? {
        if (services.isEmpty() || name.isBlank()) return null
        val key = "justwatch|find|${kind.name}|${services.joinToString(",") { it.code }}|$name|$year"
        return remember(key) {
            val variables = JustWatchJson.catalogVariables(kind, services, null, StreamingSort.Popular, kids = false, first = 8, after = null, query = name)
            val candidates = JustWatchJson.page(fetch(JustWatchJson.CATALOG, variables)).items
            Optional(
                candidates.firstOrNull { tmdbId != null && it.tmdbId == tmdbId }
                    ?: candidates.firstOrNull { it.title.equals(name, ignoreCase = true) && (year == null || it.year == year) },
            )
        }.value
    }

    /** Requête enregistrée dans la base ; sans réseau, la dernière réponse la remplace. */
    private suspend fun fetch(query: String, variables: JSONObject): JSONObject {
        val key = "justwatch|" + sha1(query + variables.toString())
        return try {
            api.query(query, variables).also { cache.put(CachedResponse(key, it.toString(), System.currentTimeMillis())) }
        } catch (e: StreamingException) {
            val saved = cache.get(key) ?: throw e
            try {
                JSONObject(saved.json)
            } catch (_: JSONException) {
                throw e
            }
        }
    }

    private suspend fun <T : Any> remember(key: String, load: suspend () -> T): T = load().also { memory[key] = it }

    /** Enveloppe pour garder en mémoire une réponse « rien trouvé ». */
    private data class Optional<T>(val value: T?)

    companion object {
        const val PAGE_SIZE = 40

        fun pageKey(
            kind: TitleKind?,
            services: List<StreamingService>,
            category: Category?,
            sort: StreamingSort,
            kids: Boolean,
            after: String?,
            first: Int = PAGE_SIZE,
        ): String = "justwatch|page|${kind?.name}|${services.joinToString(",") { it.code }}|${category?.code}|${sort.name}|$kids|$after|$first"

        fun detailKey(id: String, services: List<StreamingService>): String = "justwatch|detail|$id|${services.joinToString(",") { it.code }}"

        private fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
