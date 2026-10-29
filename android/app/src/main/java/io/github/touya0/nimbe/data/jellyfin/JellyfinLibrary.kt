package io.github.touya0.nimbe.data.jellyfin

import io.github.touya0.nimbe.core.storage.CachedResponse
import io.github.touya0.nimbe.core.storage.CachedResponseDao
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.ConcurrentHashMap

/** Tri de la bibliothèque de films, comme les filtres de la maquette. */
enum class MovieSort(val label: String) {
    Recent("Récents"),
    Unwatched("Non vus"),
    Alphabetical("A–Z"),
}

/**
 * Le serveur répond-il ? [since] : début de la coupure. [recoveries] augmente à chaque retour :
 * les écrans s'en servent pour se recharger dès que Jellyfin revient.
 */
data class JellyfinHealth(val reachable: Boolean = true, val since: Long? = null, val recoveries: Int = 0)

/**
 * Les contenus Jellyfin de l'utilisateur connecté. Chaque réponse est gardée en mémoire
 * (revenir d'une fiche affiche aussitôt la dernière liste) et dans la base : quand le Pi est coupé,
 * on relit la dernière réponse reçue, et [health] passe à « injoignable ».
 */
class JellyfinLibrary(private val account: JellyfinAccount, private val cache: CachedResponseDao) {

    private val healthState = MutableStateFlow(JellyfinHealth())
    val health: StateFlow<JellyfinHealth> = healthState.asStateFlow()

    /** Demande au serveur s'il répond (sans compte), pour l'essai automatique pendant une coupure. */
    suspend fun check(): Boolean {
        val auth = auth() ?: return true
        return try {
            account.api.publicInfo(auth.server)
            markReachable()
            true
        } catch (e: JellyfinException) {
            if (e.kind == JellyfinException.Kind.Unreachable) markUnreachable()
            false
        }
    }

    private val memory = ConcurrentHashMap<String, Any>()

    /** Dernière réponse connue pour [key], ou null. */
    @Suppress("UNCHECKED_CAST")
    fun <T> peek(key: String): T? = memory[key] as T?

    /** null si la TV n'est pas connectée à Jellyfin. */
    suspend fun auth(): JellyfinAuth? = account.auth()

    /** Reprises en cours, puis épisodes suivants des séries entamées, sans doublon de série. */
    suspend fun continueWatching(auth: JellyfinAuth): List<MediaItem> = remember(auth, "continue") {
        coroutineScope {
            val resume = async {
                page(auth, "/UserItems/Resume", mapOf("mediaTypes" to "Video", "limit" to 12))
            }
            val nextUp = async {
                page(auth, "/Shows/NextUp", mapOf("limit" to 12, "enableResumable" to false, "disableFirstEpisode" to true))
            }
            mergeContinue(resume.await().items, nextUp.await().items)
        }
    }

    /** Ajouts récents, films et épisodes ; les épisodes d'une même série sont regroupés par le serveur. */
    suspend fun latest(auth: JellyfinAuth): List<MediaItem> = remember(auth, "latest") {
        page(auth, "/Items/Latest", mapOf("includeItemTypes" to "Movie,Episode", "groupItems" to true, "limit" to 16)).items
    }

    /** [genres] : noms de genres Jellyfin acceptés (l'un ou l'autre) ; null pour tous. */
    suspend fun movies(auth: JellyfinAuth, sort: MovieSort, startIndex: Int, limit: Int, genres: List<String>? = null): ItemPage =
        remember(auth, "movies:$sort:${genres?.joinToString("|")}:$startIndex:$limit") {
            val order = when (sort) {
                MovieSort.Recent, MovieSort.Unwatched -> mapOf("sortBy" to "DateCreated,SortName", "sortOrder" to "Descending")
                MovieSort.Alphabetical -> mapOf("sortBy" to "SortName", "sortOrder" to "Ascending")
            }
            page(
                auth,
                "/Items",
                order + mapOf(
                    "includeItemTypes" to "Movie",
                    "recursive" to true,
                    "filters" to if (sort == MovieSort.Unwatched) "IsUnplayed" else null,
                    "genres" to genres?.joinToString("|"),
                    "startIndex" to startIndex,
                    "limit" to limit,
                    "enableTotalRecordCount" to true,
                ),
            )
        }

    suspend fun series(auth: JellyfinAuth, startIndex: Int, limit: Int, genres: List<String>? = null): ItemPage =
        remember(auth, "series:${genres?.joinToString("|")}:$startIndex:$limit") {
            page(
                auth,
                "/Items",
                mapOf(
                    "includeItemTypes" to "Series",
                    "genres" to genres?.joinToString("|"),
                    "recursive" to true,
                    "sortBy" to "DatePlayed,DateLastContentAdded,SortName",
                    "sortOrder" to "Descending",
                    "startIndex" to startIndex,
                    "limit" to limit,
                    "enableTotalRecordCount" to true,
                ),
            )
        }

    /**
     * Films et séries de la bibliothèque par identifiant TMDB (« Movie:603 », « Series:1399 ») :
     * un titre d'une plateforme déjà présent sur Jellyfin s'ouvre alors sur Jellyfin. Réponse légère, sans images.
     */
    suspend fun tmdbIndex(auth: JellyfinAuth): Map<String, MediaItem> = remember(auth, "tmdb-index") {
        val response = fetch(
            auth,
            "/Items",
            mapOf(
                "userId" to auth.userId,
                "includeItemTypes" to "Movie,Series",
                "recursive" to true,
                "fields" to "ProviderIds",
                "enableImages" to false,
                "enableUserData" to false,
            ),
        )
        JellyfinJson.page(response).items.mapNotNull { item -> item.tmdbId?.let { "${item.kind.name}:$it" to item } }.toMap()
    }

    suspend fun seasons(auth: JellyfinAuth, seriesId: String): List<MediaItem> = remember(auth, "seasons:$seriesId") {
        page(auth, "/Shows/$seriesId/Seasons", emptyMap()).items
    }

    suspend fun episodes(auth: JellyfinAuth, seriesId: String, seasonId: String): List<MediaItem> =
        remember(auth, "episodes:$seasonId") {
            page(auth, "/Shows/$seriesId/Episodes", mapOf("seasonId" to seasonId)).items
        }

    /** Prochain épisode à regarder dans cette série : une liste vide s'il n'y en a pas. */
    suspend fun nextUp(auth: JellyfinAuth, seriesId: String): List<MediaItem> = remember(auth, "nextup:$seriesId") {
        page(auth, "/Shows/NextUp", mapOf("seriesId" to seriesId, "limit" to 1, "enableResumable" to true)).items
    }

    /** L'épisode qui suit [episode] dans sa série, saisons suivantes comprises ; null en fin de série. */
    suspend fun nextEpisode(auth: JellyfinAuth, episode: MediaItem): MediaItem? {
        val seriesId = episode.seriesId ?: return null
        val items = page(auth, "/Shows/$seriesId/Episodes", mapOf("startItemId" to episode.id, "limit" to 2)).items
        return items.firstOrNull { it.id != episode.id }
    }

    /** Début du générique de fin (ticks), si le serveur le connaît ; null sinon, ou en cas d'échec. */
    suspend fun outroStartTicks(auth: JellyfinAuth, itemId: String): Long? = try {
        val response = account.api.get(auth, "/MediaSegments/$itemId", mapOf("includeSegmentTypes" to "Outro")) as? JSONObject
        val segments = response?.optJSONArray("Items")
        (0 until (segments?.length() ?: 0)).mapNotNull { segments?.optJSONObject(it) }
            .filter { it.optString("Type") == "Outro" }
            .minOfOrNull { it.optLong("StartTicks") }
    } catch (_: JellyfinException) {
        null
    }

    suspend fun item(auth: JellyfinAuth, id: String): MediaItem = remember(auth, "item:$id") {
        JellyfinJson.item(fetch(auth, "/Items/$id", mapOf("userId" to auth.userId, "fields" to FIELDS)) as JSONObject)
    }

    suspend fun detail(auth: JellyfinAuth, id: String): ItemDetail = remember(auth, "detail:$id") {
        val fields = "$FIELDS,People,MediaSources,MediaStreams"
        JellyfinJson.detail(fetch(auth, "/Items/$id", mapOf("userId" to auth.userId, "fields" to fields)) as JSONObject)
    }

    private suspend fun page(auth: JellyfinAuth, path: String, params: Map<String, Any?>): ItemPage {
        val common = mapOf(
            "userId" to auth.userId,
            "fields" to FIELDS,
            "enableImageTypes" to "Primary,Backdrop,Thumb",
            "imageTypeLimit" to 1,
            "enableUserData" to true,
        )
        return JellyfinJson.page(fetch(auth, path, common + params))
    }

    /**
     * Requête de lecture, enregistrée dans la base. Si le serveur ne répond pas (ou répond mal),
     * la dernière réponse enregistrée la remplace ; sans elle, l'erreur remonte à l'écran.
     * Un jeton refusé n'est jamais masqué : il faut se reconnecter.
     */
    private suspend fun fetch(auth: JellyfinAuth, path: String, params: Map<String, Any?>): Any {
        val key = cacheKey(auth, path, params)
        try {
            val response = account.api.get(auth, path, params)
            markReachable()
            cache.put(CachedResponse(key, response.toString(), System.currentTimeMillis()))
            return response
        } catch (e: JellyfinException) {
            if (e.kind == JellyfinException.Kind.Unauthorized) throw e
            if (e.kind == JellyfinException.Kind.Unreachable) markUnreachable()
            val saved = cache.get(key) ?: throw e
            return try {
                JSONTokener(saved.json).nextValue()
            } catch (_: JSONException) {
                throw e
            }
        }
    }

    private fun markReachable() = healthState.update { if (it.reachable) it else JellyfinHealth(true, null, it.recoveries + 1) }

    private fun markUnreachable() = healthState.update { if (it.reachable) it.copy(reachable = false, since = System.currentTimeMillis()) else it }

    /** Compte à qui appartient la mémoire : un autre compte repart de zéro. */
    @Volatile
    private var owner: String? = null

    private suspend fun <T : Any> remember(auth: JellyfinAuth, key: String, load: suspend () -> T): T {
        val value = load()
        val who = "${auth.server}|${auth.userId}"
        if (owner != who) {
            memory.clear()
            owner = who
        }
        memory[key] = value
        return value
    }

    companion object {
        private const val FIELDS = "Overview,Genres,DateCreated,ChildCount,PrimaryImageAspectRatio,ProviderIds"

        /** Clé d'enregistrement : serveur, utilisateur, route et paramètres triés (l'ordre ne compte pas). */
        fun cacheKey(auth: JellyfinAuth, path: String, params: Map<String, Any?>): String {
            val query = params.filterValues { it != null }.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
            return "${auth.server}|${auth.userId}|$path?$query"
        }

        /**
         * Les reprises d'abord, de la plus récente à la plus ancienne ; puis les épisodes suivants
         * des séries qui n'ont pas déjà une reprise en cours. 12 éléments au plus.
         */
        fun mergeContinue(resume: List<MediaItem>, nextUp: List<MediaItem>): List<MediaItem> {
            val sorted = resume.sortedByDescending { it.lastPlayed }
            val seriesInResume = sorted.mapNotNull { it.seriesId }.toSet()
            return (sorted + nextUp.filter { it.seriesId == null || it.seriesId !in seriesInResume })
                .distinctBy { it.id }
                .take(12)
        }
    }
}
