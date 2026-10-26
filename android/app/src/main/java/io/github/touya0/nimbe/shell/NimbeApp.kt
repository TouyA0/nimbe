package io.github.touya0.nimbe.shell

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
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.touya0.nimbe.core.network.ConnectivityMonitor
import io.github.touya0.nimbe.core.network.rememberConnectivity
import io.github.touya0.nimbe.core.storage.LauncherPrefs
import io.github.touya0.nimbe.feature.apps.AppActions
import io.github.touya0.nimbe.feature.apps.AppLauncher
import io.github.touya0.nimbe.feature.apps.AppsRepository
import io.github.touya0.nimbe.feature.apps.AppsScreen
import io.github.touya0.nimbe.feature.apps.Dock
import io.github.touya0.nimbe.feature.apps.appMenu
import io.github.touya0.nimbe.feature.apps.hiddenAppsMenu
import io.github.touya0.nimbe.feature.apps.rememberInstalledApps
import io.github.touya0.nimbe.feature.home.ComingSoonScreen
import io.github.touya0.nimbe.feature.home.HomeScreen
import io.github.touya0.nimbe.ui.components.MenuHost
import io.github.touya0.nimbe.ui.components.MenuState
import io.github.touya0.nimbe.ui.theme.Nuit
import kotlinx.coroutines.launch

@Composable
fun NimbeApp(
    repository: AppsRepository,
    prefs: LauncherPrefs,
    launcher: AppLauncher,
    connectivityMonitor: ConnectivityMonitor,
) {
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

    LaunchedEffect(Unit) { focusTab(tab) }

    MenuHost(menu) {
        Column(Modifier.fillMaxSize().background(Nuit)) {
            Header(selected = tab, onSelect = { tab = it }, requesters = requesters, connectivity = connectivity)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.Home -> HomeScreen(
                        dock = dock,
                        onOpen = launcher::open,
                        onMenu = { app, back -> menu.open(appMenu(app, dockPackages, actions, back)) },
                        onAllApps = { focusTab(Tab.Apps) },
                        connectivity = connectivity,
                        onNetworkSettings = launcher::openNetworkSettings,
                    )
                    Tab.Apps -> AppsScreen(
                        apps = visibleApps,
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
                    Tab.Movies -> ComingSoonScreen("Films", "Ta bibliothèque Jellyfin arrive bientôt.")
                    Tab.Series -> ComingSoonScreen("Séries", "Tes séries Jellyfin arrivent bientôt.")
                    Tab.Music -> ComingSoonScreen("Musique", "Spotify arrive bientôt.")
                    Tab.Gallery -> ComingSoonScreen("Galerie", "Tes photos Nextcloud arrivent bientôt.")
                }
            }
        }
    }
}
