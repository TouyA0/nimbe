package io.github.touya0.nimbe.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.core.network.ConnectivityStatus
import io.github.touya0.nimbe.core.network.InternetApps
import io.github.touya0.nimbe.core.network.NetworkState
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.layout.HomeRow
import io.github.touya0.nimbe.data.watchnext.WatchNextProgram
import io.github.touya0.nimbe.feature.apps.ActionCard
import io.github.touya0.nimbe.feature.apps.AppCard
import io.github.touya0.nimbe.feature.apps.InstalledApp
import io.github.touya0.nimbe.feature.apps.tileColor
import io.github.touya0.nimbe.feature.media.CatalogFocus
import io.github.touya0.nimbe.feature.media.ContinueCard
import io.github.touya0.nimbe.feature.media.ContinueEntry
import io.github.touya0.nimbe.feature.media.SkeletonBlock
import io.github.touya0.nimbe.feature.media.FadingBackdrop
import io.github.touya0.nimbe.feature.media.Hero
import io.github.touya0.nimbe.feature.media.HeroSwitch
import io.github.touya0.nimbe.feature.media.JellyfinUi
import io.github.touya0.nimbe.feature.media.Kicker
import io.github.touya0.nimbe.feature.media.Load
import io.github.touya0.nimbe.feature.media.LocalJellyfin
import io.github.touya0.nimbe.feature.media.MediaText
import io.github.touya0.nimbe.feature.media.PosterCard
import io.github.touya0.nimbe.feature.media.ScrollToRestore
import io.github.touya0.nimbe.feature.media.WatchNextHero
import io.github.touya0.nimbe.feature.media.focus
import io.github.touya0.nimbe.feature.media.id
import io.github.touya0.nimbe.feature.media.rememberBackdropUrl
import io.github.touya0.nimbe.feature.media.rememberJellyfin
import io.github.touya0.nimbe.feature.media.rememberWatchNext
import io.github.touya0.nimbe.feature.media.unreachableDetail
import io.github.touya0.nimbe.data.streaming.JustWatchImages
import io.github.touya0.nimbe.feature.streaming.LocalStreaming
import io.github.touya0.nimbe.feature.streaming.StreamingHero
import io.github.touya0.nimbe.shell.HeaderHeight
import io.github.touya0.nimbe.ui.components.ContentRow
import io.github.touya0.nimbe.ui.components.KeyHints
import io.github.touya0.nimbe.ui.components.NimbeIcons
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.PivotScroll
import io.github.touya0.nimbe.ui.components.RowCardPadding
import io.github.touya0.nimbe.ui.components.RowMessage
import io.github.touya0.nimbe.ui.components.RowTitle
import io.github.touya0.nimbe.ui.components.ScreenMargin
import io.github.touya0.nimbe.ui.components.rowCardTop
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeMotion
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import io.github.touya0.nimbe.ui.theme.nimbeTween
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Accueil : en haut l'élément focalisé et ses touches utiles, puis les rangées choisies par le profil, dans son ordre
 * ([rows] : Continuer à regarder, Mes applis, Ajouté récemment sur Jellyfin, Souvenirs). Sans Jellyfin, il reste les applis.
 * « Continuer à regarder » reprend aussi les titres ouverts sur les plateformes (leur carte ouvre la fiche).
 * Au démarrage ([HomeNav.startFocus]), le focus va sur la première carte : un seul OK pour reprendre.
 * [dock] vaut null pendant le chargement. [restoreFocusId] : la carte à refocaliser au retour d'une fiche.
 * Une carte de « Continuer à regarder » lance la lecture ; une affiche ouvre la fiche.
 */
@Composable
fun HomeScreen(
    rows: List<HomeRow>,
    /** Profils Invité et Enfant : le haut de l'écran les accueille, tant qu'aucune carte n'a le focus. */
    welcome: HomeWelcome?,
    /** Prénom du profil, pour le salut quand il n'y a encore rien à montrer. */
    profileName: String?,
    /** Le profil peut modifier « Mes applis » (ni Invité, ni Enfant). */
    canEdit: Boolean,
    nav: HomeNav,
    dock: List<InstalledApp>?,
    onOpen: (InstalledApp) -> Unit,
    onMenu: (InstalledApp, FocusRequester) -> Unit,
    onAllApps: () -> Unit,
    /** Applis masquées pour ce profil : leurs contenus en cours non plus. */
    hiddenApps: Set<String> = emptySet(),
    connectivity: ConnectivityStatus,
    onNetworkSettings: () -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onPlayItem: (MediaItem) -> Unit,
    /** Options d'un contenu Jellyfin. */
    onItemMenu: (MediaItem, FocusRequester) -> Unit,
    /** Options d'un contenu d'une autre appli ; le dernier argument le retire de la rangée. */
    onWatchNextMenu: (WatchNextProgram, FocusRequester, () -> Unit) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
    /** Rangée « Souvenirs » de la galerie Nextcloud du profil ; elle ne dessine rien s'il n'y en a pas. */
    memories: @Composable (onFocus: (String, String, String, Any?) -> Unit, state: LazyListState, first: Modifier) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val jellyfin = LocalJellyfin.current
    val streaming = LocalStreaming.current
    val continueRow = rememberJellyfin("continue", nav.reloads) { continueWatching(it) }
    val latestRow = rememberJellyfin("latest", nav.reloads) { latest(it) }
    // Une rangée masquée ne nourrit pas non plus le haut de l'écran.
    // « Continuer à regarder » mêle les reprises Jellyfin, les titres ouverts sur les plateformes et Watch Next.
    val watchNext = rememberWatchNext(enabled = HomeRow.Continue in rows, hidden = hiddenApps)
    val resume = if (HomeRow.Continue in rows) {
        ContinueEntry.merge(continueRow.value.orEmpty(), streaming?.history.orEmpty(), sameAs = { streaming?.inJellyfin(it.toTitle()) }, watchNext = watchNext.programs)
    } else {
        emptyList()
    }
    val latest = if (HomeRow.Latest in rows) latestRow.value.orEmpty() else emptyList()
    val resumeLoading = HomeRow.Continue in rows && continueRow.state is Load.Loading && resume.isEmpty()

    // Le launcher reste ouvert des jours : les rangées se relisent au retour d'une autre appli, puis régulièrement.
    val scope = rememberCoroutineScope()
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedOnce) nav.reloads++
        resumedOnce = true
        val job = scope.launch {
            while (true) {
                delay(REFRESH_MS)
                nav.reloads++
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    var focusedPick by remember { mutableStateOf<HomeHero?>(null) }
    var contentFocused by remember { mutableStateOf(false) }
    // Avant tout focus sur une carte : la reprise la plus récente, sinon le dernier ajout.
    val fallback: HomeHero? = (
        resume.firstOrNull()?.focus(streaming)
            ?: latest.firstOrNull()?.let { CatalogFocus.Jellyfin(it, MediaText.added(it.dateCreated)) }
        )?.let { HomeHero.Media(it) }
    val shownIds = resume.map { it.id } + latest.map { it.id }
    val dockPackages = dock.orEmpty().map { it.packageName }
    // Un contenu qui a quitté les rangées (fini pendant la lecture, par exemple) ne reste pas en haut.
    // Une appli ou un souvenir ne se décrit que sous le focus : en remontant aux onglets, la reprise revient.
    val pick = focusedPick?.takeIf { picked ->
        when (picked) {
            is HomeHero.Media -> picked.focus.id in shownIds
            is HomeHero.App -> contentFocused && picked.app.packageName in dockPackages
            else -> contentFocused
        }
    }
    val hero: HomeHero = when {
        welcome != null && focusedPick == null -> HomeHero.Welcome(welcome)
        pick != null -> pick
        fallback != null -> fallback
        resumeLoading -> HomeHero.Loading
        else -> HomeHero.Empty
    }

    // Une clé et une liste par rangée, gardées ici : l'accueil sait remonter à la première carte.
    val scroll = rememberScrollState()
    val rowStates = HomeRow.entries.associateWith { rememberLazyListState() }
    val firstCards = remember { HomeRow.entries.associateWith { FocusRequester() } }
    var onFirstCard by remember { mutableStateOf(false) }
    fun Modifier.firstCard(row: HomeRow): Modifier = focusRequester(firstCards.getValue(row)).onFocusChanged { onFirstCard = it.hasFocus }

    val currentRows by rememberUpdatedState(rows)
    suspend fun focusFirstCard() {
        scroll.scrollTo(0)
        for (row in currentRows) {
            rowStates.getValue(row).scrollToItem(0)
            withFrameNanos { }
            // Une rangée vide n'a pas de carte : requestFocus échoue, on passe à la suivante.
            if (runCatching { firstCards.getValue(row).requestFocus() }.getOrDefault(false)) return
        }
    }
    DisposableEffect(nav) {
        nav.backToTop = {
            if (!contentFocused || (onFirstCard && scroll.value == 0)) {
                false
            } else {
                scope.launch { focusFirstCard() }
                true
            }
        }
        onDispose { nav.backToTop = { false } }
    }

    // On attend de savoir ce qu'il y a à reprendre, sans faire patienter si le serveur traîne :
    // les applis sont là tout de suite, et toucher à la télécommande annule tout.
    val waitingForResume by rememberUpdatedState(dock == null || resumeLoading || (HomeRow.Continue in rows && !watchNext.loaded))
    LaunchedEffect(nav.startFocus) {
        if (!nav.startFocus) return@LaunchedEffect
        if (welcome == null) {
            withTimeoutOrNull(START_FOCUS_WAIT_MS) { snapshotFlow { waitingForResume }.first { !it } }
            focusFirstCard()
        }
        nav.startFocus = false
    }

    val appColor = (hero as? HomeHero.App)?.let { tileColor(it.app.packageName).copy(alpha = 0.3f) } ?: Color.Transparent
    val glow by animateColorAsState(appColor, nimbeTween(NimbeMotion.SLOW_MS), label = "lueur")
    val heroMedia = (hero as? HomeHero.Media)?.focus ?: (hero as? HomeHero.Welcome)?.let { (fallback as? HomeHero.Media)?.focus }
    val jellyfinImage = rememberBackdropUrl((heroMedia as? CatalogFocus.Jellyfin)?.item?.backdrop)
    val image: Any? = when (heroMedia) {
        is CatalogFocus.Jellyfin -> jellyfinImage
        is CatalogFocus.Streaming -> JustWatchImages.backdrop(heroMedia.title.backdrop)
        is CatalogFocus.WatchNext -> heroMedia.program.image
        null -> (hero as? HomeHero.Memory)?.image
    }

    Box(
        modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(listOf(glow, Color.Transparent), center = Offset(size.width * 0.85f, size.height * 0.15f), radius = size.width * 0.6f))
        },
    ) {
        // Toujours le même fond : il passe d'une image à l'autre en fondu, quelle que soit leur source.
        if (!connectivity.offline && jellyfin?.down != true) FadingBackdrop(image)
        Column(Modifier.fillMaxSize().padding(top = HeaderHeight)) {
            Box(
                Modifier.padding(horizontal = ScreenMargin).padding(top = 10.dp).height(HERO_HEIGHT).fillMaxWidth(),
                contentAlignment = Alignment.TopStart,
            ) {
                when {
                    connectivity.offline -> OfflineBanner(connectivity, onNetworkSettings)
                    jellyfin?.down == true -> ServerDownBanner(jellyfin)
                    else -> {
                        // Les touches gardent leur place : un long titre ou un grand texte se coupe avant elles.
                        Box(Modifier.fillMaxSize().padding(bottom = HINTS_HEIGHT).clipToBounds()) {
                            HeroSwitch(hero, key = { it.key }) { shown ->
                                HeroContent(shown, profileName, resume, connectivity)
                            }
                        }
                        if (contentFocused) {
                            KeyHints(*HomeText.hints(hero, canEdit).toTypedArray(), modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp))
                        }
                    }
                }
            }

            // La rangée focalisée vient se poser juste sous le haut de l'écran, titre compris :
            // on garde l'œil au même endroit en descendant, comme sur les applis de streaming.
            PivotScroll(rowCardTop()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onFocusChanged { contentFocused = it.hasFocus }
                        .verticalScroll(scroll)
                        .padding(horizontal = ScreenMargin),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rows.forEach { row ->
                        // Une clé par rangée : déplacée, elle garde sa position de défilement.
                        key(row.key) {
                            when (row) {
                                HomeRow.Continue -> {
                                    if (resume.isNotEmpty()) {
                                        val state = rowStates.getValue(row)
                                        LaunchedEffect(restoreFocusId, resume) {
                                            val index = resume.indexOfFirst { it.id == restoreFocusId }
                                            if (index >= 0) state.scrollToItem(index)
                                        }
                                        ContentRow("Continuer à regarder", state = state) {
                                            itemsIndexed(resume, key = { _, it -> "continue:${it.key}" }) { index, entry ->
                                                Box(if (index == 0) Modifier.firstCard(row) else Modifier) {
                                                    ContinueCard(
                                                        entry = entry,
                                                        onPlayItem = onPlayItem,
                                                        onFocus = { focusedPick = HomeHero.Media(it) },
                                                        restoreFocusId = restoreFocusId,
                                                        onRestored = onRestored,
                                                        onJellyfinMenu = onItemMenu,
                                                        onWatchNextMenu = { program, back -> onWatchNextMenu(program, back) { watchNext.dismiss(program) } },
                                                        offline = connectivity.offline,
                                                    )
                                                }
                                            }
                                        }
                                    } else if (resumeLoading) {
                                        LoadingRow("Continuer à regarder")
                                    } else if (continueRow.state is Load.Failed && jellyfin?.down != true) {
                                        RowMessage("Jellyfin ne répond pas : tes films reviendront avec le serveur. Tes applis restent disponibles.")
                                    }
                                }
                                HomeRow.Apps -> {
                                    // La rangée n'apparaît qu'une fois la liste chargée : sinon, l'ajout des cartes devant
                                    // « Toutes les applis » décale la rangée et la première appli sort de l'écran.
                                    if (dock == null) {
                                        RowTitle("Mes applis")
                                        RowMessage("Chargement…")
                                    } else {
                                        ContentRow("Mes applis", state = rowStates.getValue(row)) {
                                            itemsIndexed(dock, key = { _, it -> it.packageName }) { index, app ->
                                                AppCard(
                                                    app = app,
                                                    onClick = { onOpen(app) },
                                                    onMenu = { requester -> onMenu(app, requester) },
                                                    offline = connectivity.offline && InternetApps.needsInternet(app.packageName),
                                                    modifier = (if (index == 0) Modifier.firstCard(row) else Modifier)
                                                        .onFocusChanged { if (it.hasFocus) focusedPick = HomeHero.App(app) }
                                                        .width(APP_CARD_WIDTH)
                                                        .animateItem(),
                                                )
                                            }
                                            item(key = "toutes") {
                                                ActionCard(
                                                    NimbeIcons.Apps,
                                                    "Toutes les applis",
                                                    onClick = onAllApps,
                                                    modifier = (if (dock.isEmpty()) Modifier.firstCard(row) else Modifier)
                                                        .onFocusChanged { if (it.hasFocus) focusedPick = HomeHero.AllApps }
                                                        .width(APP_CARD_WIDTH),
                                                )
                                            }
                                        }
                                    }
                                }
                                HomeRow.Latest -> {
                                    if (latest.isNotEmpty()) {
                                        val state = rowStates.getValue(row)
                                        ScrollToRestore(state, latest, restoreFocusId)
                                        ContentRow("Ajouté récemment", state = state) {
                                            itemsIndexed(latest, key = { _, it -> "latest:${it.id}" }) { index, item ->
                                                PosterCard(
                                                    item = item,
                                                    onClick = { onOpenItem(item) },
                                                    width = POSTER_WIDTH,
                                                    modifier = if (index == 0) Modifier.firstCard(row) else Modifier,
                                                    onFocus = { focusedPick = HomeHero.Media(CatalogFocus.Jellyfin(item, MediaText.added(item.dateCreated))) },
                                                    restoreFocus = item.id == restoreFocusId && resume.none { it.id == restoreFocusId },
                                                    onRestored = onRestored,
                                                    onMenu = { requester -> onItemMenu(item, requester) },
                                                )
                                            }
                                        }
                                    }
                                }
                                HomeRow.Memories -> memories(
                                    { kicker, title, detail, cover -> focusedPick = HomeHero.Memory(kicker, title, detail, cover) },
                                    rowStates.getValue(row),
                                    Modifier.firstCard(row),
                                )
                            }
                        }
                    }
                    // De la place sous la dernière rangée, pour qu'elle puisse monter à son tour sous le haut de l'écran.
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }
}

private val HERO_HEIGHT = 204.dp
private val HINTS_HEIGHT = 34.dp
private val POSTER_WIDTH = 112.dp
private val APP_CARD_WIDTH = 176.dp
private val SKELETON_CARD_WIDTH = 196.dp
private const val START_FOCUS_WAIT_MS = 2_500L
private const val REFRESH_MS = 15 * 60_000L

@Composable
private fun HeroContent(hero: HomeHero, profileName: String?, resume: List<ContinueEntry>, connectivity: ConnectivityStatus) {
    when (hero) {
        is HomeHero.Media -> when (val focus = hero.focus) {
            is CatalogFocus.Jellyfin -> Hero(focus.item, focus.kicker, overviewLines = 2)
            is CatalogFocus.Streaming -> StreamingHero(focus.title, focus.kicker, overviewLines = 2)
            is CatalogFocus.WatchNext -> WatchNextHero(focus.program)
        }
        is HomeHero.App -> TextHero(
            kicker = "MES APPLIS",
            title = hero.app.label,
            text = appLine(hero.app, resume, connectivity),
        )
        HomeHero.AllApps -> TextHero("APPLIS", "Toutes les applis", "Tout ce qui est installé sur le stick, y compris ce qui n'est pas dans Mes applis.")
        is HomeHero.Memory -> TextHero(hero.kicker.uppercase(), hero.title, hero.detail)
        is HomeHero.Welcome -> TextHero(hero.welcome.kicker, hero.welcome.title, hero.welcome.text)
        HomeHero.Loading -> HeroSkeleton()
        HomeHero.Empty -> TextHero(
            kicker = "NIMBE",
            title = remember(profileName) { HomeText.greeting(LocalTime.now().hour, profileName) },
            text = "Rien en cours pour l'instant. Commence un film, une série ou une vidéo : tu le retrouveras ici, quelle que soit la plateforme.",
        )
    }
}

/** Ce qu'on peut dire d'une appli sous le focus : ce qu'elle a d'en cours, ou pourquoi elle ne marchera pas. */
private fun appLine(app: InstalledApp, resume: List<ContinueEntry>, connectivity: ConnectivityStatus): String? {
    if (connectivity.offline && InternetApps.needsInternet(app.packageName)) return "Hors ligne : elle reviendra avec la connexion."
    val current = resume.firstNotNullOfOrNull { entry ->
        when (entry) {
            is ContinueEntry.WatchNext -> entry.program.title.takeIf { entry.program.packageName == app.packageName }
            is ContinueEntry.Streaming -> entry.open.title.takeIf { entry.open.streamingService?.packageName == app.packageName }
            is ContinueEntry.Jellyfin -> null
        }
    }
    return current?.let { "En cours : $it" }
}

@Composable
private fun TextHero(kicker: String, title: String, text: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        Kicker(kicker)
        Text(title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 40.sp), maxLines = 1)
        text?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee, maxLines = 2, modifier = Modifier.width(620.dp))
        }
    }
}

/** Blocs gris à la place du haut de l'écran : la mise en page est déjà la bonne quand le contenu arrive. */
@Composable
private fun HeroSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(Modifier.width(150.dp).height(14.dp))
        SkeletonBlock(Modifier.width(320.dp).height(52.dp))
        SkeletonBlock(Modifier.width(420.dp).height(16.dp))
        SkeletonBlock(Modifier.width(280.dp).height(6.dp))
    }
}

/** Rangée en attente de son contenu, jamais focalisable : le focus reste sur ce qui marche déjà. */
@Composable
private fun LoadingRow(title: String) {
    Column {
        RowTitle(title)
        Row(Modifier.padding(vertical = RowCardPadding), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(SKELETON_CARDS) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.width(SKELETON_CARD_WIDTH).aspectRatio(16f / 9f), radius = 10.dp)
                    SkeletonBlock(Modifier.width(120.dp).height(12.dp))
                }
            }
        }
    }
}

private const val SKELETON_CARDS = 5

/** Haut de l'accueil quand Jellyfin ne répond plus : ce qui est en pause, ce qui marche, le prochain essai. */
@Composable
private fun ServerDownBanner(jellyfin: JellyfinUi) {
    val color = if (jellyfin.piUp) Focus else Alerte
    Column(
        Modifier
            .width(620.dp)
            .background(Panneau.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("!", style = MaterialTheme.typography.titleMedium, color = color)
            }
            Text(if (jellyfin.piDown) "Ton Raspberry Pi ne répond pas" else "Jellyfin ne répond pas", style = MaterialTheme.typography.headlineSmall)
        }
        Text(unreachableDetail(jellyfin, short = true), style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee, maxLines = 2)
        PillButton("Réessayer maintenant", onClick = jellyfin.onRetry)
    }
}

/** Haut de l'accueil pendant une coupure : ce qui marche encore, et depuis quand ça ne marche plus. */
@Composable
private fun OfflineBanner(connectivity: ConnectivityStatus, onNetworkSettings: () -> Unit) {
    val since = connectivity.since?.let { " depuis ${formatHour(it)}" }.orEmpty()
    val noNetwork = connectivity.state == NetworkState.NoNetwork
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (noNetwork) "Le stick n'est plus connecté au réseau$since" else "La box n'a plus internet$since",
            style = MaterialTheme.typography.titleSmall,
            color = Focus,
        )
        Text(
            text = if (noNetwork) "Plus de connexion" else "Ce qui est chez toi marche toujours",
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = if (noNetwork) {
                "Vérifie le Wi-Fi ou le câble du stick. Tes applis restent accessibles, " +
                    "mais la plupart ont besoin du réseau."
            } else {
                "Les applis du réseau de la maison restent disponibles. Les plateformes " +
                    "comme Netflix ou YouTube reviendront avec la connexion."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
            modifier = Modifier.width(620.dp),
        )
        if (noNetwork) {
            PillButton("Ouvrir les réglages réseau", onClick = onNetworkSettings, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private val HourFormat = DateTimeFormatter.ofPattern("H 'h' mm")

private fun formatHour(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(HourFormat)
