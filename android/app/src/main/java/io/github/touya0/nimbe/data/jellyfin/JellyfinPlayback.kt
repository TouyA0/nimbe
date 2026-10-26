package io.github.touya0.nimbe.data.jellyfin

import org.json.JSONArray
import org.json.JSONObject

/** Ce que l'appareil sait décoder, lu dans Android (voir player/Codecs.kt). Noms de codecs à la Jellyfin. */
data class DeviceCapabilities(val videoCodecs: Set<String>, val audioCodecs: Set<String>)

enum class PlayMethod(val jellyfin: String, val label: String) {
    DirectPlay("DirectPlay", "directe"),
    DirectStream("DirectStream", "flux direct"),
    Transcode("Transcode", "transcodée"),
}

/** Sous-titre texte servi à part par Jellyfin (fichier .srt à côté du film, par exemple). */
data class ExternalSubtitle(val url: String, val language: String?, val title: String, val mimeType: String)

/** Ce que le lecteur doit ouvrir, et de quoi rendre compte au serveur. */
data class PlaybackSource(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String?,
    val url: String,
    val method: PlayMethod,
    val isHls: Boolean,
    val subtitles: List<ExternalSubtitle>,
)

/**
 * Lecture Jellyfin : choix du flux (direct si l'appareil sait le lire, sinon transcodage HLS)
 * et rapports de position, pour que « Continuer à regarder » reste juste sur tous les écrans.
 */
class JellyfinPlayback(private val api: JellyfinApi) {

    suspend fun open(auth: JellyfinAuth, itemId: String, capabilities: DeviceCapabilities): PlaybackSource {
        val body = JSONObject()
            .put("UserId", auth.userId)
            .put("DeviceProfile", deviceProfile(capabilities))
            .put("MaxStreamingBitrate", MAX_BITRATE)
            .put("EnableDirectPlay", true)
            .put("EnableDirectStream", true)
            .put("EnableTranscoding", true)
            .put("AllowVideoStreamCopy", true)
            .put("AllowAudioStreamCopy", true)
            .put("AutoOpenLiveStream", true)
            // Pas de sous-titres incrustés par défaut : un transcodage juste pour eux coûterait cher au Pi.
            .put("SubtitleStreamIndex", -1)
        val response = api.post(auth, "/Items/$itemId/PlaybackInfo", body, mapOf("userId" to auth.userId))
        return source(response, auth.server, itemId)
    }

    suspend fun reportStart(auth: JellyfinAuth, source: PlaybackSource, positionTicks: Long) {
        api.post(auth, "/Sessions/Playing", report(source, positionTicks, paused = false))
    }

    suspend fun reportProgress(auth: JellyfinAuth, source: PlaybackSource, positionTicks: Long, paused: Boolean) {
        api.post(auth, "/Sessions/Playing/Progress", report(source, positionTicks, paused).put("EventName", "TimeUpdate"))
    }

    /** Le serveur arrête aussi le transcodage, et marque « vu » au-delà de 90 %. */
    suspend fun reportStopped(auth: JellyfinAuth, source: PlaybackSource, positionTicks: Long) {
        api.post(auth, "/Sessions/Playing/Stopped", report(source, positionTicks, paused = false))
    }

    private fun report(source: PlaybackSource, positionTicks: Long, paused: Boolean) = JSONObject()
        .put("ItemId", source.itemId)
        .put("MediaSourceId", source.mediaSourceId)
        .put("PlaySessionId", source.playSessionId)
        .put("PositionTicks", positionTicks)
        .put("IsPaused", paused)
        .put("CanSeek", true)
        .put("PlayMethod", source.method.jellyfin)

    companion object {
        private const val MAX_BITRATE = 120_000_000

        /**
         * Profil décrit au serveur. Lecture directe pour les conteneurs et codecs que l'appareil décode ;
         * sinon HLS en H.264 + AAC stéréo, que tous les sticks Android TV savent lire.
         */
        fun deviceProfile(capabilities: DeviceCapabilities): JSONObject {
            val video = capabilities.videoCodecs.sorted().joinToString(",")
            val audio = capabilities.audioCodecs.sorted().joinToString(",")
            return JSONObject()
                .put("Name", "Nimbe")
                .put("MaxStreamingBitrate", MAX_BITRATE)
                .put("MaxStaticBitrate", MAX_BITRATE)
                .put(
                    "DirectPlayProfiles",
                    JSONArray()
                        .put(JSONObject().put("Type", "Video").put("Container", "mp4,m4v,mkv,webm,mov,ts,mpegts,avi").put("VideoCodec", video).put("AudioCodec", audio))
                        .put(JSONObject().put("Type", "Audio").put("Container", "mp3,aac,flac,ogg,opus,m4a").put("AudioCodec", audio)),
                )
                .put(
                    "TranscodingProfiles",
                    JSONArray().put(
                        JSONObject()
                            .put("Type", "Video")
                            .put("Container", "ts")
                            .put("Protocol", "hls")
                            .put("Context", "Streaming")
                            .put("VideoCodec", "h264")
                            .put("AudioCodec", "aac")
                            .put("MaxAudioChannels", "2")
                            .put("MinSegments", 1)
                            .put("BreakOnNonKeyFrames", true),
                    ),
                )
                .put("ContainerProfiles", JSONArray())
                .put("CodecProfiles", JSONArray())
                .put(
                    "SubtitleProfiles",
                    JSONArray().apply {
                        listOf("srt", "subrip", "vtt", "webvtt").forEach { put(JSONObject().put("Format", it).put("Method", "External")) }
                        listOf("srt", "subrip", "ass", "ssa", "vtt").forEach { put(JSONObject().put("Format", it).put("Method", "Embed")) }
                    },
                )
        }

        /**
         * Lit la réponse de PlaybackInfo. En transcodage, Jellyfin ajoute le jeton à l'adresse (ApiKey) :
         * on l'enlève, le lecteur l'envoie dans l'en-tête Authorization comme pour le reste.
         */
        fun source(response: JSONObject, server: String, itemId: String): PlaybackSource {
            val media = response.optJSONArray("MediaSources")?.optJSONObject(0)
                ?: throw JellyfinException(JellyfinException.Kind.Server, "Ce titre n'a pas de fichier lisible")
            val mediaSourceId = media.optString("Id").ifEmpty { itemId }
            val playSessionId = response.optString("PlaySessionId").ifEmpty { null }
            val transcodingUrl = media.optString("TranscodingUrl").ifEmpty { null }
            // Sans lecture directe, l'adresse de transcodage passe avant : le serveur y remballe parfois
            // simplement le flux (codecs copiés), ce qui marche mieux qu'un conteneur que l'appareil ignore.
            val method = when {
                media.optBoolean("SupportsDirectPlay") -> PlayMethod.DirectPlay
                transcodingUrl != null -> PlayMethod.Transcode
                media.optBoolean("SupportsDirectStream") -> PlayMethod.DirectStream
                else -> throw JellyfinException(JellyfinException.Kind.Server, "Le serveur ne sait pas préparer ce titre pour la TV")
            }
            val url = if (method == PlayMethod.Transcode) {
                server + withoutToken(transcodingUrl!!)
            } else {
                "$server/Videos/$itemId/stream?static=true&mediaSourceId=$mediaSourceId" +
                    (playSessionId?.let { "&playSessionId=$it" } ?: "")
            }
            val streams = media.optJSONArray("MediaStreams") ?: JSONArray()
            val subtitles = (0 until streams.length()).mapNotNull { index ->
                val stream = streams.optJSONObject(index) ?: return@mapNotNull null
                val deliveryUrl = stream.optString("DeliveryUrl").ifEmpty { null }
                if (stream.optString("Type") != "Subtitle" || stream.optString("DeliveryMethod") != "External" || deliveryUrl == null) {
                    return@mapNotNull null
                }
                val codec = stream.optString("Codec").lowercase()
                ExternalSubtitle(
                    url = server + withoutToken(deliveryUrl),
                    language = stream.optString("Language").ifEmpty { null },
                    title = stream.optString("DisplayTitle").ifEmpty { "Sous-titres" },
                    mimeType = if (codec == "vtt" || codec == "webvtt" || deliveryUrl.contains(".vtt")) "text/vtt" else "application/x-subrip",
                )
            }
            return PlaybackSource(
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                playSessionId = playSessionId,
                url = url,
                method = method,
                isHls = method == PlayMethod.Transcode && (transcodingUrl!!.contains(".m3u8") || media.optString("TranscodingSubProtocol") == "hls"),
                subtitles = subtitles,
            )
        }

        /** Retire api_key / ApiKey d'une adresse relative renvoyée par Jellyfin. */
        fun withoutToken(path: String): String {
            val (base, query) = path.split('?', limit = 2).let { it[0] to it.getOrNull(1) }
            if (query == null) return path
            val kept = query.split('&').filterNot { it.substringBefore('=').lowercase() in setOf("api_key", "apikey") }
            return if (kept.isEmpty()) base else "$base?" + kept.joinToString("&")
        }
    }
}
