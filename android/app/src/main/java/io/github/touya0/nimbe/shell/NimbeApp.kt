package io.github.touya0.nimbe.shell

import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import io.github.touya0.nimbe.data.jellyfin.JellyfinLibrary
import io.github.touya0.nimbe.data.jellyfin.JellyfinLink
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.jellyfin.MovieSort
import io.github.touya0.nimbe.feature.films.FilmDetailScreen
import io.github.touya0.nimbe.feature.films.FilmsScreen
import io.github.touya0.nimbe.feature.media.JellyfinUi
import io.github.touya0.nimbe.feature.media.LocalJellyfin
import io.github.touya0.nimbe.feature.media.MediaDestination
import io.github.touya0.nimbe.feature.onboarding.JellyfinConnectScreen
import io.github.touya0.nimbe.feature.series.SeriesDetailScreen
import io.github.touya0.nimbe.feature.series.SeriesScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.touya0.nimbe.core.network.ConnectivityMonitor
import io.github.touya0.nimbe.core.network.rememberConnectivity
import io.github.touya0.nimbe.core.storage.AppLaunchDao
import io.github.touya0.nimbe.core.storage.LauncherPrefs
import io.github.touya0.nimbe.crashguard.StockLauncher
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.feature.apps.AppActions
import io.github.touya0.nimbe.feature.apps.AppLauncher
import io.github.touya0.nimbe.feature.apps.AppsRepository
import io.github.touya0.nimbe.feature.apps.AppsScreen
import io.github.touya0.nimbe.feature.apps.Dock
import io.github.touya0.nimbe.feature.apps.Recents
import io.github.touya0.nimbe.feature.apps.appMenu
import io.github.touya0.nimbe.feature.apps.hiddenAppsMenu
import io.github.touya0.nimbe.feature.apps.rememberInstalledApps
import io.github.touya0.nimbe.feature.home.ComingSoonScreen
import io.github.touya0.nimbe.feature.home.HomeScreen
import io.github.touya0.nimbe.feature.settings.SettingsLinks
import io.github.touya0.nimbe.feature.settings.SettingsScreen
import io.github.touya0.nimbe.ui.components.MenuHost
import io.github.touya0.nimbe.ui.components.MenuState
import io.github.touya0.nimbe.ui.theme.LocalDisplay
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.update.UpdateController
import io.github.touya0.nimbe.update.UpdateScreen
import io.github.touya0.nimbe.update.UpdateState
import kotlinx.coroutines.launch

/** Enveloppe pour distinguer « compte pas encore lu » (null) de « pas connecté ». */
private data class Linked(val link: JellyfinLink?)

@Composable
fun NimbeApp(
    repository: AppsRepository,
    prefs: LauncherPrefs,
    launcher: AppLauncher,
    connectivityMonitor: ConnectivityMonitor,
    launches: AppLaunchDao,
    updates: UpdateController,
    jellyfin: JellyfinAccount,
    library: JellyfinLibrary,
) {
    val linked by remember(jellyfin) { jellyfin.link.map { Linked(it) } }.collectAsStateWithLifecycle(initialValue = null)
    var jellyfinConnectOpen by rememberSaveable { mutableStateOf(false) }
    val ui = remember(linked) {
        JellyfinUi(library, linked?.link, linkKnown = linked != null, onConnect = { jellyfinConnectOpen = true })
    }
    CompositionLocalProvider(LocalJellyfin provides ui) {
        if (jellyfinConnectOpen) {
            JellyfinConnectScreen(jellyfin, linked?.link?.serverUrl, onClose = { jellyfinConnectOpen = false })
        } else {
            NimbeShell(repository, prefs, launcher, connectivityMonitor, launches, updates, jellyfin)
        }
    }
}

@Composable
private fun NimbeShell(
    repository: AppsRepository,
    prefs: LauncherPrefs,
    launcher: AppLauncher,
    connectivityMonitor: ConnectivityMonitor,
    launches: AppLaunchDao,
    updates: UpdateController,
    jellyfin: JellyfinAccount,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var returnToSettingsButton by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val settingsRequester = remember { FocusRequester() }
    val display = LocalDisplay.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val requesters = remember { Tab.entries.associateWith { FocusRequester() } }
    fun focusTab(target: Tab) {
        tab = target
        runCatching { requesters.getValue(target).requestFocus() }
    }

    val apps = rememberInstalledApps(repository)
    val connectivity by rememberConnectivity(connectivityMonitor)
    // null tant que les réglages ne sont pas lus : on n'affiche pas un dock par défaut qui changerait aussitôt.
    val savedDock by prefs.dock.collectAsStateWithLifecycle(initialValue = null)
    val hidden by prefs.hidden.collectAsStateWithLifecycle(initialValue = emptySet())
    val visibleApps = apps?.filterNot { it.packageName in hidden }
    val recentLaunches by remember(launches) { launches.recent(limit = 20) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val recents = apps?.let { Recents.resolve(recentLaunches.map { it.packageName }, it, hidden) }.orEmpty()
    val dock = if (apps == null || savedDock == null) null else Dock.resolve(savedDock?.packages, apps, hidden)
    // Toute modification part du dock affiché, pour que le dock par défaut devienne le point de départ.
    val dockPackages = dock.orEmpty().map { it.packageName }

    val scope = rememberCoroutineScope()
    val menu = remember { MenuState() }
    // Quand une action fait disparaître la tuile qui a le focus, on pose le focus sur l'onglet courant
    // avant d'enregistrer : sinon Android le donne au premier élément venu, l'onglet Accueil.
    fun leaveTile() {
        menu.dropReturnFocus()
        focusTab(tab)
    }
    val actions = AppActions(
        open = launcher::open,
        addToDock = { app -> scope.launch { prefs.setDock(Dock.add(dockPackages, app.packageName)) } },
        removeFromDock = { app ->
            if (tab == Tab.Home) leaveTile()
            scope.launch { prefs.setDock(Dock.remove(dockPackages, app.packageName)) }
        },
        move = { app, delta -> scope.launch { prefs.setDock(Dock.move(dockPackages, app.packageName, delta)) } },
        hide = { app ->
            leaveTile()
            scope.launch {
                prefs.setDock(Dock.remove(dockPackages, app.packageName))
                prefs.setHidden(hidden + app.packageName)
            }
        },
        showInfo = launcher::showInfo,
        uninstall = launcher::uninstall,
    )

    // Retour ramène à l'Accueil ; sur l'Accueil, il ne fait rien : c'est l'écran d'accueil du stick.
    BackHandler { focusTab(Tab.Home) }

    // Une version téléchargée et vérifiée la nuit est proposée dès que quelqu'un est devant la TV.
    val updateState by updates.state.collectAsStateWithLifecycle(initialValue = UpdateState())
    var updateOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(updateState) {
        val waiting = updateState.ready && updates.isNewer(updateState.available)
        if (waiting && System.currentTimeMillis() >= updateState.snoozedUntil) updateOpen = true
    }
    if (updateOpen) {
        UpdateScreen(updateState, updates, onClose = {
            updates.snooze()
            updateOpen = false
        })
        return
    }

    if (settingsOpen) {
        SettingsScreen(
            display = display,
            onDisplayChange = { scope.launch { prefs.setDisplay(it) } },
            links = SettingsLinks(
                accessibility = launcher::openAccessibilitySettings,
                androidSettings = launcher::openAndroidSettings,
                stockLauncher = { StockLauncher.open(context) },
            ),
            updates = updates,
            jellyfin = jellyfin,
            onOpenUpdate = { updateOpen = true },
            onClose = { settingsOpen = false },
        )
        return
    }
    // Fiche film ou série ouverte par-dessus les onglets ; au retour, sa carte reprend le focus.
    var destination by remember { mutableStateOf<MediaDestination?>(null) }
    var restoreFocusId by remember { mutableStateOf<String?>(null) }
    var filmSort by rememberSaveable { mutableStateOf(MovieSort.Recent) }
    fun openItem(item: MediaItem) {
        val target = MediaDestination.of(item) ?: return
        restoreFocusId = item.id
        destination = target
    }
    // La lecture dans Nimbe arrive avec le lecteur intégré.
    val onPlay: (MediaItem, Boolean) -> Unit = { _, _ ->
        Toast.makeText(context, "La lecture arrive dans la prochaine version de Nimbe", Toast.LENGTH_SHORT).show()
    }
    when (val target = destination) {
        is MediaDestination.Movie -> {
            FilmDetailScreen(target.item, onPlay = onPlay, onClose = { destination = null })
            return
        }
        is MediaDestination.Series -> {
            SeriesDetailScreen(target, onPlay = onPlay, onClose = { destination = null })
            return
        }
        null -> Unit
    }

    // À l'affichage, le focus va sur l'onglet ; au retour des réglages, sur la roue crantée ;
    // au retour d'une fiche, sur la carte d'origine (et sur l'onglet si elle n'est plus là).
    LaunchedEffect(Unit) {
        if (restoreFocusId != null) {
            delay(RESTORE_FOCUS_TIMEOUT_MS)
            if (restoreFocusId != null) {
                restoreFocusId = null
                focusTab(tab)
            }
        } else if (returnToSettingsButton) {
            returnToSettingsButton = false
            runCatching { settingsRequester.requestFocus() }
        } else {
            focusTab(tab)
        }
    }

    MenuHost(menu) {
        Column(Modifier.fillMaxSize().background(Nuit)) {
            Header(
                selected = tab,
                onSelect = { tab = it },
                requesters = requesters,
                connectivity = connectivity,
                onSettings = {
                    returnToSettingsButton = true
                    settingsOpen = true
                },
                settingsRequester = settingsRequester,
            )
            // En remontant depuis le contenu, le focus va sur l'onglet affiché, même depuis une carte
            // placée sous la roue crantée : sinon la recherche géométrique choisit l'élément le plus proche.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusProperties {
                        onExit = {
                            if (requestedFocusDirection == FocusDirection.Up) runCatching { requesters.getValue(tab).requestFocus() }
                        }
                    }
                    .focusGroup(),
            ) {
                when (tab) {
                    Tab.Home -> HomeScreen(
                        dock = dock,
                        onOpen = launcher::open,
                        onMenu = { app, back -> menu.open(appMenu(app, dockPackages, actions, back)) },
                        onAllApps = { focusTab(Tab.Apps) },
                        connectivity = connectivity,
                        onNetworkSettings = launcher::openNetworkSettings,
                        onOpenItem = ::openItem,
                        restoreFocusId = restoreFocusId,
                        onRestored = { restoreFocusId = null },
                    )
                    Tab.Apps -> AppsScreen(
                        apps = visibleApps,
                        recents = recents,
                        hiddenCount = apps?.count { it.packageName in hidden } ?: 0,
                        onOpen = launcher::open,
                        onMenu = { app, back -> menu.open(appMenu(app, dockPackages, actions, back)) },
                        offline = connectivity.offline,
                        onShowHidden = { back ->
                            val hiddenApps = apps.orEmpty().filter { it.packageName in hidden }
                            menu.open(
                                hiddenAppsMenu(
                                    hidden = hiddenApps,
                                    unhide = { app ->
                                        // La tuile « Masquées » disparaît avec la dernière.
                                        if (hidden.size == 1) leaveTile()
                                        scope.launch { prefs.setHidden(hidden - app.packageName) }
                                    },
                                    returnFocus = back,
                                ),
                            )
                        },
                    )
                    Tab.Tv -> ComingSoonScreen("TV en direct", "Les chaînes et le guide des programmes arrivent avec la gateway du Pi.")
                    Tab.Movies -> FilmsScreen(
                        sort = filmSort,
                        onSortChange = { filmSort = it },
                        onOpenItem = ::openItem,
                        restoreFocusId = restoreFocusId,
                        onRestored = { restoreFocusId = null },
                    )
                    Tab.Series -> SeriesScreen(
                        onOpenItem = ::openItem,
                        restoreFocusId = restoreFocusId,
                        onRestored = { restoreFocusId = null },
                    )
                    Tab.Music -> ComingSoonScreen("Musique", "Spotify arrive bientôt.")
                    Tab.Gallery -> ComingSoonScreen("Galerie", "Tes photos Nextcloud arrivent bientôt.")
                }
            }
        }
    }
}

/** Au-delà, la carte d'origine n'est plus affichée (liste rechargée) : le focus retourne sur l'onglet. */
private const val RESTORE_FOCUS_TIMEOUT_MS = 800L
