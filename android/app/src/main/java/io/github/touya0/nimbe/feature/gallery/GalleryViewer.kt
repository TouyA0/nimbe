package io.github.touya0.nimbe.feature.gallery

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.ContentFrame
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import io.github.touya0.nimbe.data.nextcloud.NextcloudApi
import io.github.touya0.nimbe.data.nextcloud.NextcloudAuth
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import kotlinx.coroutines.delay

/** Ouvrir la visionneuse : la liste affichée par la grille, et la photo choisie. */
data class GalleryViewerRequest(
    val auth: NextcloudAuth,
    val userId: String,
    val items: List<GalleryItem>,
    val index: Int,
    /** Rythme du diaporama, réglé par profil (Réglages → Galerie). */
    val slideMs: Long = SLIDE_MS,
    /** « ▶ Diaporama » d'un dossier ou d'un souvenir : le diaporama démarre tout de suite. */
    val startSlideshow: Boolean = false,
)

/**
 * Photo ou vidéo en plein écran. Gauche et droite : précédente ou suivante. OK : lance ou arrête le
 * diaporama (photo suivante au rythme réglé, en fondu), ou met en pause et reprend une vidéo, qui se lit
 * d'elle-même dès qu'elle s'affiche.
 * Haut, bas : réaffichent les infos. Retour : quitte, [onClose] reçoit l'élément regardé en dernier
 * pour que la grille le retrouve.
 */
@Composable
fun GalleryViewer(request: GalleryViewerRequest, onClose: (GalleryItem) -> Unit) {
    val items = request.items
    var index by remember { mutableIntStateOf(request.index.coerceIn(items.indices)) }
    val item = items[index]
    var slideshow by remember { mutableStateOf(request.startSlideshow) }
    // Infos par-dessus : visibles au début et après chaque touche, puis masquées pour laisser la photo.
    var touches by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    LaunchedEffect(touches) {
        overlay = true
        delay(OVERLAY_MS)
        overlay = false
    }
    // Une vidéo se lit dès qu'elle s'affiche, comme sur un téléphone ; OK met en pause ou reprend.
    var videoPlaying by remember { mutableStateOf(false) }

    fun go(delta: Int) {
        val target = index + delta
        if (target in items.indices) index = target else if (slideshow) slideshow = false
    }

    BackHandler { onClose(item) }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Diaporama : une photo toutes les 8 s ; une vidéo passe à la suite quand elle se termine.
    LaunchedEffect(slideshow, index) {
        if (slideshow && !item.isVideo) {
            delay(request.slideMs)
            go(1)
        }
    }

    // Les voisines sont demandées d'avance : gauche et droite affichent aussitôt.
    val platformContext = LocalPlatformContext.current
    LaunchedEffect(index) {
        val loader = SingletonImageLoader.get(platformContext)
        listOf(index + 1, index - 1).mapNotNull { items.getOrNull(it) }.filter { !it.isVideo && it.hasPreview }.forEach {
            loader.enqueue(previewRequest(platformContext, request.auth, it, FULL_WIDTH, FULL_HEIGHT, crop = false))
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> go(-1)
                    Key.DirectionRight -> go(1)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> when {
                        item.isVideo -> videoPlaying = !videoPlaying
                        else -> slideshow = !slideshow
                    }
                    Key.DirectionUp, Key.DirectionDown -> Unit
                    else -> return@onPreviewKeyEvent false
                }
                touches++
                true
            },
    ) {
        Crossfade(targetState = item, animationSpec = tween(FADE_MS), label = "photo") { shown ->
            if (shown.isVideo && shown == item) {
                GalleryVideo(
                    url = NextcloudApi.fileUrl(request.auth.server, request.userId, shown.path),
                    authorization = NextcloudApi.basic(request.auth),
                    playing = videoPlaying,
                    onPlayingChange = { videoPlaying = it },
                    onEnded = { if (slideshow) go(1) },
                    showProgress = overlay || !videoPlaying,
                )
            } else {
                NextcloudPreview(
                    request.auth,
                    shown,
                    FULL_WIDTH,
                    FULL_HEIGHT,
                    contentScale = ContentScale.Fit,
                    crop = false,
                    modifier = Modifier.fillMaxSize(),
                    // Sans aperçu : fond uni le temps du fondu, la vidéo arrive aussitôt.
                    fallback = {},
                )
            }
        }
        LaunchedEffect(index) { videoPlaying = item.isVideo }

        AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            ViewerOverlay(item, index, items.size, slideshow)
        }
    }
}

@Composable
private fun ViewerOverlay(item: GalleryItem, index: Int, total: Int, slideshow: Boolean) {
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                .padding(horizontal = 56.dp, vertical = 36.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.path.substringBeforeLast('/', "").substringAfterLast('/').ifEmpty { "Nextcloud" }, style = MaterialTheme.typography.headlineMedium)
                Text(GalleryText.fullDate(item), style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
            }
            Text(
                if (slideshow) "Diaporama · ${index + 1} / $total" else "${index + 1} / $total",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            GalleryText.viewerHints(item.isVideo, slideshow),
            style = MaterialTheme.typography.labelLarge,
            color = LuneAttenuee,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(horizontal = 56.dp, vertical = 32.dp),
        )
    }
}

/**
 * Vidéo du compte, lue directement depuis Nextcloud (WebDAV) ; le mot de passe d'application part
 * dans l'en-tête, jamais dans l'adresse. Le lecteur est libéré dès qu'on quitte la vidéo.
 */
@OptIn(UnstableApi::class)
@Composable
private fun GalleryVideo(
    url: String,
    authorization: String,
    playing: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onEnded: () -> Unit,
    /** Barre de progression et durée, affichées avec les infos par-dessus. */
    showProgress: Boolean = true,
) {
    val context = LocalContext.current
    var error by remember(url) { mutableStateOf<String?>(null) }
    val player = remember(url) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Nimbe")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(mapOf("Authorization" to authorization))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = onPlayingChange(isPlaying)
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onPlayerError(e: PlaybackException) {
                error = "Lecture impossible : ${GalleryText.playbackError(e.errorCode)}"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(playing) { player.playWhenReady = playing }
    Box(Modifier.fillMaxSize()) {
        ContentFrame(player = player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        // Où en est la vidéo, relu deux fois par seconde : « 0:42 / 1:15 » et une barre ambre.
        val progress by produceState(0L to 0L, player) {
            while (true) {
                value = player.currentPosition.coerceAtLeast(0) to player.duration.coerceAtLeast(0)
                delay(500)
            }
        }
        val (position, duration) = progress
        if (showProgress && duration > 0) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 56.dp, bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${GalleryText.duration(position)} / ${GalleryText.duration(duration)}", style = MaterialTheme.typography.labelLarge, color = Lune)
                Box(Modifier.fillMaxWidth().height(4.dp).background(Lune.copy(alpha = 0.25f), RoundedCornerShape(2.dp))) {
                    Box(
                        Modifier
                            .fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(Focus, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = Alerte,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Nuit.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}

/** Taille de l'aperçu plein écran demandé à Nextcloud : la photo entière, dans du 1080p. */
private const val FULL_WIDTH = 1920
private const val FULL_HEIGHT = 1080
private const val SLIDE_MS = 8_000L
private const val FADE_MS = 600
private const val OVERLAY_MS = 4_000L
