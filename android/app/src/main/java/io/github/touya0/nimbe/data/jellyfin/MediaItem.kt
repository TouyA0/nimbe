package io.github.touya0.nimbe.data.jellyfin

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException

enum class ItemKind { Movie, Series, Season, Episode, Other }

enum class ImageType(val path: String) { Primary("Primary"), Backdrop("Backdrop/0"), Thumb("Thumb") }

/**
 * Une image d'un élément Jellyfin : l'élément qui la porte (parfois le parent), son type
 * et son étiquette de version, qui change quand l'image change (cache sûr).
 */
data class ImageRef(val itemId: String, val tag: String, val type: ImageType = ImageType.Primary) {

    /**
     * Adresse de l'image redimensionnée par le serveur. Les images Jellyfin se lisent sans jeton :
     * aucun secret ne passe dans l'adresse.
     */
    fun url(server: String, maxWidth: Int? = null, maxHeight: Int? = null): String = buildString {
        append("$server/Items/$itemId/Images/${type.path}?tag=$tag&quality=90")
        maxWidth?.let { append("&maxWidth=$it") }
        maxHeight?.let { append("&maxHeight=$it") }
    }
}

/**
 * Film, série, saison ou épisode, tel que l'affichent les écrans.
 * Les durées sont en « ticks » Jellyfin (100 ns) : voir [TICKS_PER_SECOND].
 */
data class MediaItem(
    val id: String,
    val kind: ItemKind,
    val name: String,
    val overview: String? = null,
    val year: Int? = null,
    val endYear: Int? = null,
    val genres: List<String> = emptyList(),
    val officialRating: String? = null,
    val runTimeTicks: Long? = null,
    val positionTicks: Long = 0,
    val played: Boolean = false,
    val unplayedCount: Int? = null,
    val childCount: Int? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val dateCreated: Instant? = null,
    val lastPlayed: Instant? = null,
    val primary: ImageRef? = null,
    val backdrop: ImageRef? = null,
    val thumb: ImageRef? = null,
    val seriesPrimary: ImageRef? = null,
    /** Identifiant TMDB (film ou série), pour retrouver le même titre sur les plateformes de streaming. */
    val tmdbId: String? = null,
) {
    /** Part déjà vue, entre 0 et 1 ; 0 si rien n'a été commencé. */
    val progress: Float
        get() {
            val total = runTimeTicks ?: return 0f
            if (total <= 0 || positionTicks <= 0) return 0f
            return (positionTicks.toFloat() / total).coerceIn(0f, 1f)
        }

    /** Une position de reprise existe, y compris pour un film déjà vu qu'on revoit. */
    val resumable: Boolean
        get() = progress > 0f

    val remainingMinutes: Int?
        get() = runTimeTicks?.let { ((it - positionTicks).coerceAtLeast(0) / TICKS_PER_MINUTE).toInt() }

    val runtimeMinutes: Int?
        get() = runTimeTicks?.let { (it / TICKS_PER_MINUTE).toInt() }

    /** « S02 · É04 », pour un épisode dont on connaît la saison et le numéro. */
    val episodeCode: String?
        get() = if (seasonNumber != null && episodeNumber != null) "S%02d · É%02d".format(seasonNumber, episodeNumber) else null

    companion object {
        const val TICKS_PER_SECOND = 10_000_000L
        const val TICKS_PER_MINUTE = 60 * TICKS_PER_SECOND
    }
}

/** Liste paginée : [total] permet de savoir s'il reste des pages à charger. */
data class ItemPage(val items: List<MediaItem>, val total: Int)

/** Une personne de la fiche : acteur avec son rôle, réalisateur… */
data class Person(val id: String, val name: String, val role: String?, val type: String, val image: ImageRef?)

/** Ce que la fiche film affiche en plus : équipe, langues et fichier. */
data class ItemDetail(
    val item: MediaItem,
    val people: List<Person>,
    val audioLanguages: List<String>,
    val subtitleLanguages: List<String>,
    val video: String?,
    val sizeBytes: Long?,
)

/** Lecture des réponses JSON de Jellyfin. Tout champ absent reste null : le serveur omet ce qui est vide. */
object JellyfinJson {

    fun item(json: JSONObject): MediaItem {
        val userData = json.optJSONObject("UserData")
        val imageTags = json.optJSONObject("ImageTags")
        val id = json.getString("Id")
        val type = json.optString("Type")
        return MediaItem(
            id = id,
            kind = when (type) {
                "Movie" -> ItemKind.Movie
                "Series" -> ItemKind.Series
                "Season" -> ItemKind.Season
                "Episode" -> ItemKind.Episode
                else -> ItemKind.Other
            },
            name = json.optString("Name"),
            overview = json.text("Overview"),
            year = json.int("ProductionYear"),
            endYear = json.text("EndDate")?.take(4)?.toIntOrNull(),
            genres = json.optJSONArray("Genres").strings(),
            officialRating = json.text("OfficialRating"),
            runTimeTicks = json.long("RunTimeTicks"),
            positionTicks = userData?.long("PlaybackPositionTicks") ?: 0,
            played = userData?.optBoolean("Played") ?: false,
            unplayedCount = userData?.int("UnplayedItemCount"),
            childCount = json.int("ChildCount"),
            seriesId = json.text("SeriesId"),
            seriesName = json.text("SeriesName"),
            seasonId = json.text("SeasonId"),
            seasonNumber = json.int("ParentIndexNumber"),
            episodeNumber = json.int("IndexNumber"),
            dateCreated = json.instant("DateCreated"),
            lastPlayed = userData?.instant("LastPlayedDate"),
            primary = imageTags?.text("Primary")?.let { ImageRef(id, it) },
            backdrop = json.optJSONArray("BackdropImageTags").strings().firstOrNull()?.let { ImageRef(id, it, ImageType.Backdrop) }
                ?: ref(json.text("ParentBackdropItemId"), json.optJSONArray("ParentBackdropImageTags").strings().firstOrNull(), ImageType.Backdrop),
            thumb = imageTags?.text("Thumb")?.let { ImageRef(id, it, ImageType.Thumb) }
                ?: ref(json.text("ParentThumbItemId"), json.text("ParentThumbImageTag"), ImageType.Thumb),
            seriesPrimary = ref(json.text("SeriesId"), json.text("SeriesPrimaryImageTag")),
            tmdbId = json.optJSONObject("ProviderIds")?.let { ids -> ids.text("Tmdb") ?: ids.text("tmdb") },
        )
    }

    /** Liste d'éléments : un tableau nu (/Items/Latest) ou {Items, TotalRecordCount}. */
    fun page(response: Any): ItemPage = when (response) {
        is JSONArray -> response.objects().map(::item).let { ItemPage(it, it.size) }
        is JSONObject -> {
            val items = response.optJSONArray("Items").objects().map(::item)
            ItemPage(items, response.int("TotalRecordCount") ?: items.size)
        }
        else -> ItemPage(emptyList(), 0)
    }

    fun detail(json: JSONObject): ItemDetail {
        val source = json.optJSONArray("MediaSources")?.optJSONObject(0)
        val streams = (source?.optJSONArray("MediaStreams") ?: json.optJSONArray("MediaStreams")).objects()
        fun languages(type: String) = streams.filter { it.optString("Type") == type }
            .map { it.text("DisplayLanguage") ?: languageName(it.text("Language")) ?: it.text("DisplayTitle") ?: "Inconnue" }
            .distinct()
        val video = streams.firstOrNull { it.optString("Type") == "Video" }?.let { stream ->
            listOfNotNull(stream.text("Codec")?.uppercase(), stream.int("Height")?.let { "${it}p" }).joinToString(" · ").ifEmpty { null }
        }
        return ItemDetail(
            item = item(json),
            people = json.optJSONArray("People").objects().map { person ->
                val id = person.getString("Id")
                Person(
                    id = id,
                    name = person.optString("Name"),
                    role = person.text("Role"),
                    type = person.optString("Type"),
                    image = person.text("PrimaryImageTag")?.let { ImageRef(id, it) },
                )
            },
            audioLanguages = languages("Audio"),
            subtitleLanguages = languages("Subtitle"),
            video = video,
            sizeBytes = source?.long("Size"),
        )
    }

    /** Codes ISO 639-2 courants ; les autres restent en code. */
    private fun languageName(code: String?): String? = when (code?.lowercase()) {
        null -> null
        "fre", "fra", "fr" -> "Français"
        "eng", "en" -> "Anglais"
        "ger", "deu", "de" -> "Allemand"
        "spa", "es" -> "Espagnol"
        "ita", "it" -> "Italien"
        "jpn", "ja" -> "Japonais"
        "und" -> null
        else -> code.uppercase()
    }

    private fun ref(itemId: String?, tag: String?, type: ImageType = ImageType.Primary) =
        if (itemId != null && tag != null) ImageRef(itemId, tag, type) else null

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }
    private fun JSONObject.int(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
    private fun JSONObject.long(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
    private fun JSONObject.instant(key: String): Instant? = text(key)?.let {
        try {
            Instant.parse(it)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun JSONArray?.strings(): List<String> = this?.let { array -> List(array.length()) { array.optString(it) }.filter { it.isNotEmpty() } }.orEmpty()
    private fun JSONArray?.objects(): List<JSONObject> = this?.let { array -> List(array.length()) { array.optJSONObject(it) }.filterNotNull() }.orEmpty()
}
