package io.github.touya0.nimbe.feature.screensaver

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import io.github.touya0.nimbe.data.homeassistant.HaLive
import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import io.github.touya0.nimbe.data.nextcloud.NextcloudAlbum
import io.github.touya0.nimbe.data.nextcloud.NextcloudAuth
import io.github.touya0.nimbe.data.nextcloud.NextcloudException
import io.github.touya0.nimbe.data.nextcloud.NextcloudGallery
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.feature.gallery.GalleryText
import io.github.touya0.nimbe.feature.gallery.NextcloudPreview
import io.github.touya0.nimbe.feature.gallery.previewRequest
import io.github.touya0.nimbe.feature.status.ServerVerdict
import io.github.touya0.nimbe.ui.theme.ClockStyle
import io.github.touya0.nimbe.ui.theme.Lune
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ce que montre l'écran de veille, d'après ses réglages et le profil actif : photos (avec ou sans infos),
 * ou l'heure et le serveur. Sert au vrai écran de veille d'Android et à l'aperçu des réglages.
 * Sans photo à montrer (galerie non reliée, Pi coupé sans rien en cache, la nuit), c'est l'heure et le serveur.
 */
@Composable
fun ScreensaverHost(verdict: ServerVerdict, ha: HaLive, prefs: ScreensaverPrefs, active: Profile?, gallery: NextcloudGallery) {
    val night by produceState(ScreensaverRules.isNight(LocalTime.now(), prefs), prefs) {
        while (true) {
            value = ScreensaverRules.isNight(LocalTime.now(), prefs)
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val source = ScreensaverRules.source(prefs, active)
    if (night || source == null) {
        ScreensaverContent(verdict, ha)
        return
    }
    val auth by produceState<NextcloudAuth?>(null, source.profileId) { value = gallery.auth(source.profileId) }
    // Les photos de la source, d'abord ce qui est gardé sur la TV, puis le serveur s'il répond.
    val items by produceState<List<GalleryItem>?>(null, source) {
        val pick = source.pick
        val album = pick?.albumPath?.let { NextcloudAlbum(pick.name, it, 0, null) }
        val folder = pick?.folder
        value = when {
            album != null -> gallery.cachedAlbumItems(source.profileId, album)
            folder != null -> gallery.cachedFolderItems(source.profileId, folder)
            else -> gallery.prefs.prefs(source.profileId).first().filter(gallery.all(source.profileId).first())
        } ?: emptyList()
        try {
            when {
                album != null -> value = gallery.refreshAlbumItems(source.profileId, album)
                folder != null -> value = gallery.refreshFolderItems(source.profileId, folder)
            }
        } catch (_: NextcloudException) {
            // Pi coupé : on garde ce qui était en cache.
        }
    }
    val playlist = remember(items, prefs.shuffle) { ScreensaverRules.playlist(items.orEmpty(), prefs.shuffle, seed = System.currentTimeMillis()) }
    val current = auth
    if (current == null || playlist.isEmpty()) {
        ScreensaverContent(verdict, ha)
        return
    }
    PhotoSlides(current, playlist, prefs, verdict, ha, caption = source.pick?.name)
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

@Composable
private fun PhotoSlides(auth: NextcloudAuth, playlist: List<GalleryItem>, prefs: ScreensaverPrefs, verdict: ServerVerdict, ha: HaLive, caption: String?) {
    var index by remember(playlist) { mutableIntStateOf(0) }
    val item = playlist[index % playlist.size]
    LaunchedEffect(playlist, prefs.slideSeconds) {
        while (true) {
            delay(prefs.slideSeconds * 1000L)
            index = (index + 1) % playlist.size
        }
    }
    // Les trois suivantes sont demandées d'avance : chaque fondu montre une photo déjà là.
    val context = LocalPlatformContext.current
    LaunchedEffect(index) {
        val loader = SingletonImageLoader.get(context)
        (1..PRELOAD).map { playlist[(index + it) % playlist.size] }.forEach {
            loader.enqueue(previewRequest(context, auth, it, FULL_WIDTH, FULL_HEIGHT, crop = false))
        }
    }
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val (dx, dy) = ScreensaverText.burnInOffset(System.currentTimeMillis() / 60_000, radius = 12f)
    val withInfo = prefs.mode == ScreensaverMode.PhotosInfo

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = item, animationSpec = tween(FADE_MS), label = "veille") { shown ->
            NextcloudPreview(
                auth,
                shown,
                FULL_WIDTH,
                FULL_HEIGHT,
                contentScale = ContentScale.Fit,
                crop = false,
                showNameWithoutPreview = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!withInfo) return@Box
        if (prefs.showCaption) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .offset(dx.dp, dy.dp)
                    .padding(horizontal = 72.dp, vertical = 40.dp),
            ) {
                Text(caption ?: item.path.substringBeforeLast('/', "").substringAfterLast('/'), style = MaterialTheme.typography.titleLarge, color = Dim)
                Text(GalleryText.fullDate(item), style = MaterialTheme.typography.bodyMedium, color = Dimmer)
            }
        }
        if (prefs.showClock || prefs.showServer) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .offset(dx.dp, dy.dp)
                    .padding(horizontal = 72.dp, vertical = 48.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (prefs.showClock) {
                    Column {
                        Text(now.format(TimeFormat), style = ClockStyle.copy(fontSize = 88.sp, lineHeight = 92.sp), color = Dim)
                        val date = now.format(DateFormat).replaceFirstChar { it.uppercase() }
                        Text(
                            listOfNotNull(date, ScreensaverText.weather(ha.weather)).joinToString(" · "),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
                            color = Dimmer,
                        )
                    }
                } else {
                    Box(Modifier)
                }
                if (prefs.showServer) ServerCorner(verdict, ha)
            }
        }
    }
}

private val Dim = Lune.copy(alpha = 0.85f)
private val Dimmer = Lune.copy(alpha = 0.6f)

/** Aperçus en 1080p : la photo entière, jamais l'original. */
private const val FULL_WIDTH = 1920
private const val FULL_HEIGHT = 1080
private const val PRELOAD = 3
private const val FADE_MS = 1_200
