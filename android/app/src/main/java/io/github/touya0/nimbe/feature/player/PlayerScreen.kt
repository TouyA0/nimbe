package io.github.touya0.nimbe.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.ContentFrame
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.feature.media.MediaText
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.Spinner
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.ClockStyle
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class Panel { Tracks }

/** Où poser le focus quand les commandes apparaissent. */
private enum class Target { Play, Seek }

/**
 * Lecteur plein écran. Commandes cachées : OK met en pause, gauche et droite reculent de 10 s
 * ou avancent de 30 s, haut et bas font apparaître les commandes. Retour referme le panneau,
 * puis les commandes, puis quitte la lecture (la position est envoyée au serveur).
 */
@Composable
fun PlayerScreen(request: PlayRequest, account: JellyfinAccount, reportScope: CoroutineScope, onClose: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { PlayerController(context.applicationContext, account, request, reportScope) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    // Le rapport d'arrêt est attendu (2 s au plus) : l'écran d'où l'on vient affichera la bonne position.
    fun exit() {
        if (closing) return
        closing = true
        val report = controller.stop()
        scope.launch {
            withTimeoutOrNull(2_000) { report?.join() }
            onClose()
        }
    }

    LaunchedEffect(Unit) { controller.open() }
    LaunchedEffect(Unit) { controller.reportProgressPeriodically() }
    DisposableEffect(Unit) { onDispose { controller.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { exit() }
    LaunchedEffect(controller.status) { if (controller.status == PlayerStatus.Ended) exit() }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var overlay by remember { mutableStateOf(true) }
    var target by remember { mutableStateOf(Target.Play) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Touche dont on a déjà traité l'appui : son relâchement ne doit pas cliquer le bouton qui vient d'apparaître.
    var swallowUp by remember { mutableStateOf<Key?>(null) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }

    fun show(on: Target) {
        target = on
        overlay = true
    }

    LaunchedEffect(overlay, target, panel) {
        if (panel != null) return@LaunchedEffect
        runCatching {
            when {
                !overlay -> rootFocus.requestFocus()
                target == Target.Seek -> seekFocus.requestFocus()
                else -> playFocus.requestFocus()
            }
        }
    }
    LaunchedEffect(overlay, lastInteraction, controller.isPlaying, panel) {
        if (overlay && controller.isPlaying && panel == null) {
            delay(OVERLAY_TIMEOUT_MS)
            overlay = false
        }
    }

    BackHandler {
        when {
            panel != null -> panel = null
            overlay && controller.status !is PlayerStatus.Failed -> overlay = false
            else -> exit()
        }
    }

    fun handle(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyUp) {
            if (event.key == swallowUp) {
                swallowUp = null
                return true
            }
            return false
        }
        if (event.type != KeyEventType.KeyDown) return false
        lastInteraction = System.currentTimeMillis()
        when (event.key) {
            Key.MediaPlayPause -> controller.togglePause()
            Key.MediaPlay -> controller.player?.play()
            Key.MediaPause -> controller.player?.pause()
            Key.MediaFastForward -> controller.seekBy(PlayerController.SEEK_FORWARD_MS)
            Key.MediaRewind -> controller.seekBy(-PlayerController.SEEK_BACK_MS)
            Key.MediaStop -> exit()
            else -> {
                if (overlay || panel != null || controller.status is PlayerStatus.Failed) return false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        controller.togglePause()
                        show(Target.Play)
                    }
                    Key.DirectionLeft -> {
                        controller.seekBy(-PlayerController.SEEK_BACK_MS)
                        show(Target.Seek)
                    }
                    Key.DirectionRight -> {
                        controller.seekBy(PlayerController.SEEK_FORWARD_MS)
                        show(Target.Seek)
                    }
                    Key.DirectionUp, Key.DirectionDown -> show(Target.Play)
                    else -> return false
                }
                swallowUp = event.key
                return true
            }
        }
        return true
    }

    val position by produceState(request.startTicks / PlayerController.TICKS_PER_MS, controller.player) {
        while (true) {
            value = controller.positionMs
            delay(500)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent(::handle)
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        controller.player?.let { player ->
            ContentFrame(player = player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Subtitles(player)
        }
        val status = controller.status
        if (status !is PlayerStatus.Failed && (status == PlayerStatus.Opening || controller.buffering)) {
            Box(Modifier.align(Alignment.Center)) { Spinner(diameter = 48.dp) }
        }
        AnimatedVisibility(overlay && status !is PlayerStatus.Failed, enter = fadeIn(), exit = fadeOut()) {
            Controls(
                controller = controller,
                positionMs = position,
                playFocus = playFocus,
                seekFocus = seekFocus,
                onTracks = { panel = Panel.Tracks },
                onQuit = ::exit,
                onInteraction = { lastInteraction = System.currentTimeMillis() },
            )
        }
        if (panel == Panel.Tracks) TracksPanel(controller, onDone = { panel = null })
        if (status is PlayerStatus.Failed) {
            Failure(
                message = status.message,
                onRetry = { scope.launch { controller.open(position * PlayerController.TICKS_PER_MS) } },
                onQuit = ::exit,
            )
        }
    }
}

private const val OVERLAY_TIMEOUT_MS = 5_000L

/** Sous-titres dessinés par Media3, avec le style choisi dans les réglages d'accessibilité d'Android. */
@Composable
private fun Subtitles(player: Player) {
    val context = LocalContext.current
    val view = remember {
        SubtitleView(context).apply {
            setUserDefaultStyle()
            setUserDefaultTextSize()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) = view.setCues(cueGroup.cues)
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            view.setCues(emptyList())
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().padding(bottom = 24.dp))
}

@Composable
private fun BoxScope.Controls(
    controller: PlayerController,
    positionMs: Long,
    playFocus: FocusRequester,
    seekFocus: FocusRequester,
    onTracks: () -> Unit,
    onQuit: () -> Unit,
    onInteraction: () -> Unit,
) {
    val item = controller.item
    val duration = controller.durationMs
    Box(Modifier.fillMaxSize()) {
        // Haut : titre et horloge, sur un dégradé pour rester lisible sur une image claire.
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Nuit.copy(alpha = 0.85f), Color.Transparent)))
                .padding(horizontal = 48.dp, vertical = 28.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(MediaText.title(item), style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = if (item.kind == ItemKind.Episode) {
                    MediaText.episodeLine(item)
                } else {
                    listOfNotNull(item.year?.toString(), item.genres.firstOrNull()).joinToString(" · ")
                }
                Text(
                    listOf(detail, "Lecture depuis Jellyfin").filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LuneAttenuee,
                )
            }
            Clock()
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Nuit.copy(alpha = 0.9f))))
                .padding(horizontal = 48.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SeekBar(positionMs, duration, controller, seekFocus, onInteraction)
            Box(Modifier.fillMaxWidth()) {
                Text(PlayerText.time(positionMs), style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterStart))
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton("-10", 44, onClick = { controller.seekBy(-PlayerController.SEEK_BACK_MS) })
                    RoundButton(if (controller.isPlaying) "❚❚" else "▶", 56, onClick = controller::togglePause, modifier = Modifier.focusRequester(playFocus))
                    RoundButton("+30", 44, onClick = { controller.seekBy(PlayerController.SEEK_FORWARD_MS) })
                }
                Text(
                    "-" + PlayerText.time((duration - positionMs).coerceAtLeast(0)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val subtitle = controller.subtitleTracks.firstOrNull { it.selected }?.label ?: "Aucun"
                val audio = controller.audioTracks.firstOrNull { it.selected }?.let { listOfNotNull(it.label, it.detail).joinToString(" ") } ?: "—"
                PillButton("Sous-titres : $subtitle", onClick = onTracks)
                PillButton("Audio : $audio", onClick = onTracks)
                controller.source?.let { PillButton("Qualité : ${it.method.label}", onClick = {}) }
                PillButton("Quitter la lecture", onClick = onQuit)
            }
        }
    }
}

/** Barre de lecture focalisable : gauche recule de 10 s, droite avance de 30 s. */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, controller: PlayerController, focus: FocusRequester, onInteraction: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> controller.seekBy(-PlayerController.SEEK_BACK_MS)
                    Key.DirectionRight -> controller.seekBy(PlayerController.SEEK_FORWARD_MS)
                    Key.DirectionCenter, Key.Enter -> controller.togglePause()
                    else -> return@onKeyEvent false
                }
                onInteraction()
                true
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(if (focused) 6.dp else 4.dp).background(Lune.copy(alpha = 0.3f), RoundedCornerShape(3.dp)))
        Box(Modifier.fillMaxWidth(fraction).height(if (focused) 6.dp else 4.dp).background(Focus, RoundedCornerShape(3.dp)))
        if (focused) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.fillMaxWidth(fraction).height(1.dp))
                Box(Modifier.size(18.dp).background(Lune, CircleShape).border(3.dp, Focus, CircleShape))
            }
        }
    }
}

@Composable
private fun RoundButton(text: String, size: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.size(size.dp).onFocusChanged { focused = it.isFocused }.focusRing(focused, (size / 2).dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PanneauClair.copy(alpha = 0.8f),
            contentColor = Lune,
            focusedContainerColor = Lune,
            focusedContentColor = Nuit,
            pressedContainerColor = Lune,
            pressedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Panneau de droite : sous-titres puis audio, la piste en cours cochée. */
@Composable
private fun BoxScope.TracksPanel(controller: PlayerController, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(32.dp)
            .width(340.dp)
            .background(Panneau.copy(alpha = 0.96f), RoundedCornerShape(16.dp))
            .border(1.dp, PanneauClair, RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Sous-titres", style = MaterialTheme.typography.headlineSmall)
        controller.subtitleTracks.forEachIndexed { index, choice ->
            TrackRow(choice, onClick = { controller.selectSubtitle(choice); onDone() }, modifier = if (index == 0) Modifier.focusRequester(first) else Modifier)
        }
        Text("Audio", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        if (controller.audioTracks.isEmpty()) {
            Text("Une seule piste", style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
        }
        controller.audioTracks.forEach { choice ->
            TrackRow(choice, onClick = { controller.selectAudio(choice); onDone() })
        }
    }
}

@Composable
private fun TrackRow(choice: TrackChoice, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PanneauClair.copy(alpha = 0.6f),
            contentColor = Lune,
            focusedContainerColor = PanneauClair,
            focusedContentColor = Lune,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (choice.selected) "●" else "○", style = MaterialTheme.typography.titleSmall, color = if (choice.selected) Focus else LuneAttenuee)
            Text(choice.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            choice.detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee) }
        }
    }
}

@Composable
private fun BoxScope.Failure(message: String, onRetry: () -> Unit, onQuit: () -> Unit) {
    val retry = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
    Column(
        Modifier
            .align(Alignment.Center)
            .width(520.dp)
            .background(Panneau, RoundedCornerShape(20.dp))
            .border(1.dp, Alerte.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Lecture impossible", style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Alerte)
        Text(
            "Ta position est gardée. Vérifie que le Raspberry Pi est allumé, puis réessaie.",
            style = MaterialTheme.typography.bodyMedium,
            color = LuneAttenuee,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Réessayer", onClick = onRetry, modifier = Modifier.focusRequester(retry))
            PillButton("Quitter la lecture", onClick = onQuit)
        }
    }
}

@Composable
private fun Clock() {
    val time by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(10_000)
        }
    }
    Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), style = ClockStyle)
}

/** Temps affichés par le lecteur. */
object PlayerText {
    /** « 1:21:04 » ou « 47:12 ». */
    fun time(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }
}
