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
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.touya0.nimbe.data.jellyfin.JellyfinException
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.feature.media.JellyfinImage
import io.github.touya0.nimbe.feature.media.LocalJellyfin
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

private enum class Panel { Tracks, Sleep }

/** Où poser le focus quand les commandes apparaissent. */
private enum class Target { Play, Seek }

/**
 * Lecteur plein écran. Commandes cachées : OK met en pause, gauche et droite reculent de 10 s
 * ou avancent de 30 s, haut et bas font apparaître les commandes. Retour referme le panneau,
 * puis les commandes, puis quitte la lecture (la position est envoyée au serveur).
 *
 * Pour un épisode, la suite est proposée pendant le générique et se lance après 10 s ([onNext]
 * reçoit aussi le nombre d'épisodes vus sans toucher la télécommande). [sleepTimer] arrête la lecture
 * à l'heure dite ; il est gardé par l'appelant pour survivre au passage à l'épisode suivant.
 */
@Composable
fun PlayerScreen(
    request: PlayRequest,
    account: JellyfinAccount,
    reportScope: CoroutineScope,
    untouchedBefore: Int,
    sleepTimer: SleepTimer?,
    onSleepTimerChange: (SleepTimer?) -> Unit,
    onNext: (PlayRequest, Int) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { PlayerController(context.applicationContext, account, request, reportScope) }
    val library = LocalJellyfin.current?.library
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    val untouched = NextUpRule.untouched(untouchedBefore, touched)

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

    fun advance(next: MediaItem) {
        if (closing) return
        closing = true
        val report = controller.stop()
        // Un épisode entamé reprend où il en était ; sinon, depuis le début.
        val start = if (ResumeRule.shouldAsk(next)) next.positionTicks else 0
        scope.launch {
            withTimeoutOrNull(2_000) { report?.join() }
            onNext(PlayRequest(next, start), untouched)
        }
    }

    // Arrêt du minuteur : on quitte la lecture et l'écran peut de nouveau se mettre en veille.
    fun sleepNow() {
        onSleepTimerChange(null)
        exit()
    }

    LaunchedEffect(Unit) { controller.open() }
    LaunchedEffect(Unit) { controller.reportProgressPeriodically() }
    DisposableEffect(Unit) { onDispose { controller.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { exit() }

    // Épisode suivant et début du générique, demandés une fois au serveur.
    val next by produceState<MediaItem?>(null) {
        if (request.item.kind != ItemKind.Episode || library == null) return@produceState
        val auth = library.auth() ?: return@produceState
        value = try {
            library.nextEpisode(auth, request.item)
        } catch (_: JellyfinException) {
            null
        }
    }
    val outroMs by produceState<Long?>(null) {
        if (request.item.kind != ItemKind.Episode || library == null) return@produceState
        val auth = library.auth() ?: return@produceState
        value = library.outroStartTicks(auth, request.item.id)?.let { it / PlayerController.TICKS_PER_MS }
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val position by produceState(request.startTicks / PlayerController.TICKS_PER_MS, controller.player) {
        while (true) {
            value = controller.positionMs
            delay(500)
        }
    }

    // Carte « épisode suivant » : pendant le générique, sauf si l'on a annulé ou demandé l'arrêt à la fin.
    var nextCancelled by remember { mutableStateOf(false) }
    val showNextAt = NextUpRule.showAtMs(controller.durationMs, outroMs)
    val nextItem = next
    val nextCard = nextItem != null && !nextCancelled && sleepTimer != SleepTimer.EndOfItem &&
        showNextAt != null && position >= showNextAt && controller.status == PlayerStatus.Playing
    val askStillWatching = NextUpRule.askStillWatching(untouched)
    var countdown by remember { mutableIntStateOf(NextUpRule.COUNTDOWN_SECONDS) }
    LaunchedEffect(nextCard, askStillWatching) {
        if (!nextCard || askStillWatching || nextItem == null) return@LaunchedEffect
        countdown = NextUpRule.COUNTDOWN_SECONDS
        while (countdown > 0) {
            delay(1_000)
            countdown--
        }
        advance(nextItem)
    }
    LaunchedEffect(controller.status) {
        if (controller.status != PlayerStatus.Ended) return@LaunchedEffect
        when {
            sleepTimer == SleepTimer.EndOfItem -> sleepNow()
            nextItem != null && !nextCancelled && !askStillWatching -> advance(nextItem)
            else -> exit()
        }
    }

    // Minuteur de veille : avertissement 60 s avant, puis arrêt.
    val sleepRemaining = sleepTimer?.let { SleepTimer.remainingMs(it, now) }
    var warningDismissed by remember(sleepTimer) { mutableStateOf(false) }
    val sleepWarning = sleepRemaining != null && sleepRemaining <= SleepTimer.WARNING_MS && !warningDismissed
    LaunchedEffect(sleepTimer) {
        val timer = sleepTimer as? SleepTimer.At ?: return@LaunchedEffect
        delay(SleepTimer.remainingMs(timer) ?: 0)
        sleepNow()
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

    val cardFocus = remember { FocusRequester() }
    val warningFocus = remember { FocusRequester() }
    LaunchedEffect(overlay, target, panel, nextCard, sleepWarning) {
        if (panel != null) return@LaunchedEffect
        runCatching {
            when {
                nextCard -> cardFocus.requestFocus()
                sleepWarning -> warningFocus.requestFocus()
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
            nextCard -> nextCancelled = true
            sleepWarning -> warningDismissed = true
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
        touched = true
        when (event.key) {
            Key.MediaPlayPause -> controller.togglePause()
            Key.MediaPlay -> controller.player?.play()
            Key.MediaPause -> controller.player?.pause()
            Key.MediaFastForward -> controller.seekBy(PlayerController.SEEK_FORWARD_MS)
            Key.MediaRewind -> controller.seekBy(-PlayerController.SEEK_BACK_MS)
            Key.MediaStop -> exit()
            else -> {
                if (overlay || panel != null || nextCard || sleepWarning || controller.status is PlayerStatus.Failed) return false
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
                sleepLabel = sleepLabel(sleepTimer, sleepRemaining, request.item),
                onSleep = { panel = Panel.Sleep },
                onQuit = ::exit,
                onInteraction = { lastInteraction = System.currentTimeMillis() },
            )
        }
        if (panel == Panel.Tracks) TracksPanel(controller, onDone = { panel = null })
        if (panel == Panel.Sleep) {
            SleepPanel(sleepTimer, request.item, onChoose = {
                onSleepTimerChange(it)
                panel = null
            })
        }
        if (nextCard && nextItem != null) {
            NextUpCard(
                next = nextItem,
                countdown = countdown,
                askStillWatching = askStillWatching,
                untouched = untouched,
                focus = cardFocus,
                onPlay = { advance(nextItem) },
                onCancel = { nextCancelled = true },
                onStop = ::exit,
            )
        }
        if (sleepWarning && sleepRemaining != null) {
            SleepWarning(
                remainingMs = sleepRemaining,
                focus = warningFocus,
                onExtend = { onSleepTimerChange(SleepTimer.inMinutes(15)) },
                onDismiss = { warningDismissed = true },
            )
        }
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
    sleepLabel: String,
    onSleep: () -> Unit,
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
                controller.source?.let { PillButton("Lecture ${it.method.label}", onClick = {}) }
                PillButton(sleepLabel, onClick = onSleep)
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

/** « Minuteur de veille », « Minuteur : 23 min » ou « Minuteur : fin de l'épisode ». */
private fun sleepLabel(timer: SleepTimer?, remainingMs: Long?, item: MediaItem): String = when (timer) {
    null -> "Minuteur de veille"
    SleepTimer.EndOfItem -> "Minuteur : ${endOfItem(item)}"
    is SleepTimer.At -> "Minuteur : ${((remainingMs ?: 0) + 59_999) / 60_000} min"
}

private fun endOfItem(item: MediaItem) = if (item.kind == ItemKind.Episode) "fin de l'épisode" else "fin du film"

/** Choix du minuteur : désactivé, 15 à 60 min, ou fin du contenu. */
@Composable
private fun BoxScope.SleepPanel(current: SleepTimer?, item: MediaItem, onChoose: (SleepTimer?) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(32.dp)
            .width(340.dp)
            .background(Panneau.copy(alpha = 0.96f), RoundedCornerShape(16.dp))
            .border(1.dp, PanneauClair, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Minuteur de veille", style = MaterialTheme.typography.headlineSmall)
        Text("La lecture s'arrête et la TV peut se mettre en veille.", style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
        TrackRow(
            TrackChoice("Désactivé", null, null, 0, selected = current == null),
            onClick = { onChoose(null) },
            modifier = Modifier.focusRequester(first),
        )
        SleepTimer.MINUTES.forEach { minutes ->
            TrackRow(
                TrackChoice("$minutes min", null, null, 0, selected = (current as? SleepTimer.At)?.minutes == minutes),
                onClick = { onChoose(SleepTimer.inMinutes(minutes)) },
            )
        }
        TrackRow(
            TrackChoice(endOfItem(item).replaceFirstChar { it.uppercase() }, null, null, 0, selected = current == SleepTimer.EndOfItem),
            onClick = { onChoose(SleepTimer.EndOfItem) },
        )
    }
}

/**
 * Carte en bas à droite pendant le générique : l'épisode suivant se lance seul après le compte à rebours.
 * Après plusieurs épisodes sans toucher la télécommande, elle demande « Tu regardes toujours ? » et n'avance plus seule.
 */
@Composable
private fun BoxScope.NextUpCard(
    next: MediaItem,
    countdown: Int,
    askStillWatching: Boolean,
    untouched: Int,
    focus: FocusRequester,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(40.dp)
            .width(440.dp)
            .background(Panneau.copy(alpha = 0.96f), RoundedCornerShape(16.dp))
            .border(1.dp, PanneauClair, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (askStillWatching) {
            Text("Tu regardes toujours ?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "$untouched épisodes d'affilée sans toucher la télécommande. À suivre : ${next.episodeCode ?: next.name}.",
                style = MaterialTheme.typography.bodyMedium,
                color = LuneAttenuee,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Continuer", onClick = onPlay, modifier = Modifier.focusRequester(focus))
                PillButton("Arrêter", onClick = onStop)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(150.dp).height(84.dp).background(PanneauClair, RoundedCornerShape(8.dp))) {
                    JellyfinImage(next.primary ?: next.thumb ?: next.backdrop, width = 150.dp, height = null, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f)) {
                    Text("ÉPISODE SUIVANT", style = MaterialTheme.typography.labelMedium, color = Focus)
                    Text(next.episodeCode ?: "", style = MaterialTheme.typography.titleSmall)
                    Text(next.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("▶  Lire maintenant", onClick = onPlay, modifier = Modifier.focusRequester(focus))
                PillButton("Annuler", onClick = onCancel)
                Spacer(Modifier.weight(1f))
                Text("Dans $countdown s", style = MaterialTheme.typography.titleSmall, color = LuneAttenuee)
            }
        }
    }
}

/** Avertissement 60 s avant l'arrêt du minuteur, avec de quoi prolonger. */
@Composable
private fun BoxScope.SleepWarning(remainingMs: Long, focus: FocusRequester, onExtend: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .padding(40.dp)
            .width(380.dp)
            .background(Panneau.copy(alpha = 0.96f), RoundedCornerShape(16.dp))
            .border(1.dp, Focus.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Arrêt dans ${PlayerText.time(remainingMs)}", style = MaterialTheme.typography.headlineSmall)
        Text("Le minuteur de veille va arrêter la lecture.", style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Encore 15 min", onClick = onExtend, modifier = Modifier.focusRequester(focus))
            PillButton("OK", onClick = onDismiss)
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
