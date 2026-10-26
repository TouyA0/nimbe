package io.github.touya0.nimbe.feature.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.data.jellyfin.JellyfinAuth
import io.github.touya0.nimbe.data.jellyfin.JellyfinException
import io.github.touya0.nimbe.data.jellyfin.JellyfinPlayback
import io.github.touya0.nimbe.data.jellyfin.PlaybackSource
import io.github.touya0.nimbe.data.jellyfin.MediaItem as JellyfinItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Une piste audio ou de sous-titres proposée dans le panneau ; [group] null pour « Aucun ». */
data class TrackChoice(val label: String, val detail: String?, val group: Tracks.Group?, val index: Int, val selected: Boolean)

sealed interface PlayerStatus {
    data object Opening : PlayerStatus
    data object Playing : PlayerStatus
    data object Ended : PlayerStatus
    data class Failed(val message: String) : PlayerStatus
}

/**
 * Lecture d'un contenu Jellyfin dans Nimbe. Ouvre le flux choisi par le serveur, envoie la position
 * au départ, toutes les 10 s, à chaque pause ou saut, et à l'arrêt.
 * Toutes les méthodes s'appellent depuis le fil principal ; seuls les rapports partent sur [reportScope],
 * qui survit à l'écran : le rapport d'arrêt doit arriver même si l'on quitte aussitôt.
 */
@Stable
@OptIn(UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val account: JellyfinAccount,
    val request: PlayRequest,
    private val reportScope: CoroutineScope,
) {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var status by mutableStateOf<PlayerStatus>(PlayerStatus.Opening)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(true)
        private set
    var source by mutableStateOf<PlaybackSource?>(null)
        private set
    var audioTracks by mutableStateOf<List<TrackChoice>>(emptyList())
        private set
    var subtitleTracks by mutableStateOf<List<TrackChoice>>(emptyList())
        private set

    private var auth: JellyfinAuth? = null
    private var stopped = false

    val item: JellyfinItem get() = request.item

    /** Ouvre le flux et lance la lecture à [startTicks]. Rappelé par « Réessayer » à la dernière position. */
    suspend fun open(startTicks: Long = request.startTicks) {
        status = PlayerStatus.Opening
        releasePlayer()
        try {
            val auth = account.auth() ?: throw JellyfinException(JellyfinException.Kind.Unauthorized, "Jellyfin n'est plus connecté")
            this.auth = auth
            val source = account.playback.open(auth, item.id, Codecs.capabilities)
            this.source = source
            val header = account.api.authorizationFor(auth.token)
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent("Nimbe")
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(20_000)
                .setDefaultRequestProperties(mapOf("Authorization" to header))
            val exo = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(http))
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true,
                )
                .setHandleAudioBecomingNoisy(true)
                .setSeekBackIncrementMs(SEEK_BACK_MS)
                .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
                .build()
            exo.addListener(listener)
            exo.setMediaItem(mediaItem(source), startTicks / TICKS_PER_MS)
            exo.prepare()
            exo.playWhenReady = true
            player = exo
            status = PlayerStatus.Playing
            report { reportStart(it, source, startTicks) }
        } catch (e: JellyfinException) {
            status = PlayerStatus.Failed(e.message.orEmpty())
        }
    }

    fun togglePause() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekBy(deltaMs: Long) {
        val exo = player ?: return
        val duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0, duration))
    }

    val positionMs: Long get() = player?.currentPosition ?: request.startTicks / TICKS_PER_MS
    val durationMs: Long get() = player?.duration?.takeIf { it != C.TIME_UNSET } ?: (item.runTimeTicks ?: 0) / TICKS_PER_MS

    fun selectAudio(choice: TrackChoice) {
        val exo = player ?: return
        val group = choice.group ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.index))
            .build()
    }

    fun selectSubtitle(choice: TrackChoice) {
        val exo = player ?: return
        val group = choice.group
        exo.trackSelectionParameters = if (group == null) {
            exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.index))
                .build()
        }
    }

    /**
     * Arrête la lecture et prévient le serveur. Le rapport part sur [reportScope] ; le Job permet
     * d'attendre qu'il soit arrivé avant de rafraîchir « Continuer à regarder ».
     */
    fun stop(): Job? {
        if (stopped) return null
        stopped = true
        val exo = player
        val source = source
        val ended = status == PlayerStatus.Ended
        val positionTicks = when {
            exo == null -> request.startTicks
            ended -> (exo.duration.takeIf { it != C.TIME_UNSET } ?: exo.currentPosition) * TICKS_PER_MS
            else -> exo.currentPosition * TICKS_PER_MS
        }
        releasePlayer()
        if (source == null) return null
        return report { reportStopped(it, source, positionTicks) }
    }

    private fun mediaItem(source: PlaybackSource): MediaItem = MediaItem.Builder()
        .setUri(source.url)
        .apply { if (source.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .setSubtitleConfigurations(
            source.subtitles.map { subtitle ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                    .setMimeType(subtitle.mimeType)
                    .setLanguage(subtitle.language)
                    .setLabel(subtitle.title)
                    .build()
            },
        )
        .build()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            reportProgressNow()
        }

        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            if (state == Player.STATE_ENDED) status = PlayerStatus.Ended
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) reportProgressNow()
        }

        override fun onTracksChanged(tracks: Tracks) {
            audioTracks = choices(tracks, C.TRACK_TYPE_AUDIO)
            val text = choices(tracks, C.TRACK_TYPE_TEXT)
            subtitleTracks = listOf(TrackChoice("Aucun", null, null, 0, selected = text.none { it.selected })) + text
        }

        override fun onPlayerError(error: PlaybackException) {
            status = PlayerStatus.Failed(
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    -> "Le serveur ne répond plus"
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    -> "Ce format ne se lit pas sur cette TV"
                    else -> "La lecture s'est interrompue"
                },
            )
        }
    }

    private fun choices(tracks: Tracks, type: Int): List<TrackChoice> = tracks.groups
        .filter { it.type == type }
        .flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
                val format = group.getTrackFormat(index)
                val language = format.language?.takeUnless { it == "und" }?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.FRENCH) }
                    ?.replaceFirstChar { it.titlecase(Locale.FRENCH) }
                val channels = when (format.channelCount) {
                    1 -> "mono"
                    2 -> "stéréo"
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> null
                }
                TrackChoice(
                    label = language ?: format.label ?: "Piste ${index + 1}",
                    detail = listOfNotNull(format.label?.takeIf { language != null && it != language }, channels).joinToString(" · ").ifEmpty { null },
                    group = group,
                    index = index,
                    selected = group.isTrackSelected(index),
                )
            }
        }

    /** Rapport périodique, lancé par l'écran (fil principal) tant qu'il est affiché. */
    suspend fun reportProgressPeriodically() {
        while (true) {
            delay(PROGRESS_INTERVAL_MS)
            if (isPlaying) reportProgressNow()
        }
    }

    private fun reportProgressNow() {
        val exo = player ?: return
        val source = source ?: return
        if (stopped) return
        val positionTicks = exo.currentPosition * TICKS_PER_MS
        val paused = !exo.isPlaying
        report { reportProgress(it, source, positionTicks, paused) }
    }

    /** Un rapport qui échoue (Pi coupé) n'interrompt pas la lecture : le suivant rattrapera. */
    private fun report(block: suspend JellyfinPlayback.(JellyfinAuth) -> Unit): Job? {
        val auth = auth ?: return null
        return reportScope.launch {
            try {
                account.playback.block(auth)
            } catch (_: JellyfinException) {
            }
        }
    }

    private fun releasePlayer() {
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        isPlaying = false
        buffering = true
    }

    companion object {
        const val TICKS_PER_MS = 10_000L
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 30_000L
        private const val PROGRESS_INTERVAL_MS = 10_000L
    }
}
