package io.github.touya0.nimbe.feature.player

import android.media.MediaCodecList
import io.github.touya0.nimbe.data.jellyfin.DeviceCapabilities

/**
 * Codecs que l'appareil sait décoder, d'après Android. Le serveur s'en sert pour choisir
 * entre lecture directe et transcodage : on n'annonce rien qu'Android ne déclare pas.
 */
object Codecs {

    private val video = mapOf(
        "video/avc" to "h264",
        "video/hevc" to "hevc",
        "video/x-vnd.on2.vp9" to "vp9",
        "video/x-vnd.on2.vp8" to "vp8",
        "video/av01" to "av1",
        "video/mpeg2" to "mpeg2video",
        "video/mp4v-es" to "mpeg4",
    )

    private val audio = mapOf(
        "audio/mp4a-latm" to listOf("aac"),
        "audio/mpeg" to listOf("mp3"),
        "audio/ac3" to listOf("ac3"),
        "audio/eac3" to listOf("eac3"),
        "audio/opus" to listOf("opus"),
        "audio/flac" to listOf("flac"),
        "audio/vorbis" to listOf("vorbis"),
        "audio/raw" to listOf("pcm_s16le", "pcm_s24le"),
    )

    val capabilities: DeviceCapabilities by lazy {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filterNot { it.isEncoder }
            .flatMap { it.supportedTypes.map(String::lowercase) }
            .toSet()
        DeviceCapabilities(
            videoCodecs = video.filterKeys { it in decoders }.values.toSet() + "h264",
            audioCodecs = audio.filterKeys { it in decoders }.values.flatten().toSet() + "aac",
        )
    }
}
