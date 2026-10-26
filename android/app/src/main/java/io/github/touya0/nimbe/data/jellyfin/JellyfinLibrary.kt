package io.github.touya0.nimbe.data.jellyfin

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Tri de la bibliothèque de films, comme les filtres de la maquette. */
enum class MovieSort(val label: String) {
    Recent("Récents"),
    Unwatched("Non vus"),
    Alphabetical("A–Z"),
}

/**
 * Les contenus Jellyfin de l'utilisateur connecté. Chaque réponse est gardée en mémoire :
 * revenir d'une fiche affiche aussitôt la dernière liste, pendant qu'on la rafraîchit.
 */
class JellyfinLibrary(private val account: JellyfinAccount) {

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

    suspend fun movies(auth: JellyfinAuth, sort: MovieSort, startIndex: Int, limit: Int): ItemPage =
        remember(auth, "movies:$sort:$startIndex:$limit") {
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
                    "startIndex" to startIndex,
                    "limit" to limit,
                    "enableTotalRecordCount" to true,
                ),
            )
        }

    suspend fun series(auth: JellyfinAuth, startIndex: Int, limit: Int): ItemPage = remember(auth, "series:$startIndex:$limit") {
        page(
            auth,
            "/Items",
            mapOf(
                "includeItemTypes" to "Series",
                "recursive" to true,
                "sortBy" to "DatePlayed,DateLastContentAdded,SortName",
                "sortOrder" to "Descending",
                "startIndex" to startIndex,
                "limit" to limit,
                "enableTotalRecordCount" to true,
            ),
        )
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
        JellyfinJson.item(account.api.get(auth, "/Items/$id", mapOf("userId" to auth.userId, "fields" to FIELDS)) as JSONObject)
    }

    suspend fun detail(auth: JellyfinAuth, id: String): ItemDetail = remember(auth, "detail:$id") {
        val fields = "$FIELDS,People,MediaSources,MediaStreams"
        JellyfinJson.detail(account.api.get(auth, "/Items/$id", mapOf("userId" to auth.userId, "fields" to fields)) as JSONObject)
    }

    private suspend fun page(auth: JellyfinAuth, path: String, params: Map<String, Any?>): ItemPage {
        val common = mapOf(
            "userId" to auth.userId,
            "fields" to FIELDS,
            "enableImageTypes" to "Primary,Backdrop,Thumb",
            "imageTypeLimit" to 1,
            "enableUserData" to true,
        )
        return JellyfinJson.page(account.api.get(auth, path, common + params))
    }

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
        private const val FIELDS = "Overview,Genres,DateCreated,ChildCount,PrimaryImageAspectRatio"

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
