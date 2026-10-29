package io.github.touya0.nimbe.shell

import io.github.touya0.nimbe.NimbeApplication
import io.github.touya0.nimbe.feature.player.PlayRequest
import io.github.touya0.nimbe.feature.player.PlayerScreen
import io.github.touya0.nimbe.feature.player.ResumeDialog
import io.github.touya0.nimbe.feature.player.ResumeRule
import io.github.touya0.nimbe.feature.player.SleepTimer
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import java.time.ZoneId
import androidx.compose.runtime.CompositionLocalProvider
import io.github.touya0.nimbe.data.jellyfin.JellyfinHealth
import io.github.touya0.nimbe.data.jellyfin.JellyfinLibrary
import io.github.touya0.nimbe.data.jellyfin.JellyfinLink
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.jellyfin.MovieSort
import io.github.touya0.nimbe.feature.films.FilmDetailScreen
import io.github.touya0.nimbe.feature.films.FilmsScreen
import io.github.touya0.nimbe.feature.media.JellyfinUi
import io.github.touya0.nimbe.feature.media.LocalJellyfin
import io.github.touya0.nimbe.feature.media.MediaDestination
import io.github.touya0.nimbe.data.layout.LayoutRules
import io.github.touya0.nimbe.data.layout.LayoutStore
import io.github.touya0.nimbe.data.layout.ProfileLayout
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfilesState
import io.github.touya0.nimbe.data.profiles.ChildMode
import io.github.touya0.nimbe.data.profiles.GuestMode
import io.github.touya0.nimbe.data.profiles.ProfilesStore
import io.github.touya0.nimbe.data.profiles.ScreenTime
import io.github.touya0.nimbe.data.profiles.ScreenTimeStore
import io.github.touya0.nimbe.data.profiles.ScreenTimeVerdict
import io.github.touya0.nimbe.data.profiles.isChild
import io.github.touya0.nimbe.data.profiles.isGuest
import io.github.touya0.nimbe.data.profiles.isRestricted
import io.github.touya0.nimbe.data.profiles.leavingNeedsPin
import io.github.touya0.nimbe.data.security.ParentPinStore
import io.github.touya0.nimbe.feature.onboarding.FirstLaunchScreen
import io.github.touya0.nimbe.feature.onboarding.SetupStep
import io.github.touya0.nimbe.feature.profiles.TimeUpScreen
import io.github.touya0.nimbe.feature.profiles.WhoIsWatchingScreen
import io.github.touya0.nimbe.feature.security.PinPrompt
import io.github.touya0.nimbe.feature.onboarding.JellyfinConnectScreen
import io.github.touya0.nimbe.feature.series.SeriesDetailScreen
import io.github.touya0.nimbe.feature.series.SeriesScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.touya0.nimbe.core.network.ConnectivityMonitor
import io.github.touya0.nimbe.core.network.rememberConnectivity
import io.github.touya0.nimbe.core.storage.AppLaunchDao
import io.github.touya0.nimbe.core.storage.LauncherPrefs
import io.github.touya0.nimbe.crashguard.StockLauncher
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.data.nextcloud.NextcloudAccounts
import io.github.touya0.nimbe.data.nextcloud.NextcloudGallery
import io.github.touya0.nimbe.feature.gallery.GalleryHomeRow
import io.github.touya0.nimbe.feature.screensaver.ScreensaverHost
import io.github.touya0.nimbe.feature.gallery.GalleryNav
import io.github.touya0.nimbe.feature.gallery.GalleryScreen
import io.github.touya0.nimbe.feature.gallery.GalleryViewer
import io.github.touya0.nimbe.feature.gallery.GalleryViewerRequest
import io.github.touya0.nimbe.data.homeassistant.HaConnection
import io.github.touya0.nimbe.data.homeassistant.HaLive
import io.github.touya0.nimbe.data.homeassistant.HistoryPoint
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantAccount
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantMonitor
import io.github.touya0.nimbe.feature.screensaver.ScreensaverContent
import io.github.touya0.nimbe.feature.status.JellyfinWatch
import io.github.touya0.nimbe.feature.status.ServerStatus
import io.github.touya0.nimbe.feature.status.ServerStatusScreen
import io.github.touya0.nimbe.feature.apps.AppActions
import io.github.touya0.nimbe.feature.apps.AppLauncher
import io.github.touya0.nimbe.feature.apps.AppsRepository
import io.github.touya0.nimbe.feature.apps.AppsScreen
import io.github.touya0.nimbe.feature.apps.Dock
import io.github.touya0.nimbe.feature.apps.InstalledApp
import io.github.touya0.nimbe.feature.apps.Recents
import io.github.touya0.nimbe.feature.apps.appMenu
import io.github.touya0.nimbe.feature.apps.lockedAppMenu
import io.github.touya0.nimbe.feature.apps.hiddenAppsMenu
import io.github.touya0.nimbe.feature.apps.rememberInstalledApps
import io.github.touya0.nimbe.feature.home.ComingSoonScreen
import io.github.touya0.nimbe.feature.home.HomeScreen
import io.github.touya0.nimbe.feature.home.HomeWelcome
import io.github.touya0.nimbe.feature.layout.HomeEditScreen
import io.github.touya0.nimbe.feature.settings.SettingsLinks
import io.github.touya0.nimbe.feature.settings.SettingsScreen
import io.github.touya0.nimbe.feature.settings.SettingsSection
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
    homeAssistant: HomeAssistantAccount,
    homeAssistantMonitor: HomeAssistantMonitor,
    nextcloud: NextcloudAccounts,
    gallery: NextcloudGallery,
    profiles: ProfilesStore,
    parentPin: ParentPinStore,
    layouts: LayoutStore,
    screenTime: ScreenTimeStore,
    homePresses: Flow<Unit>,
    screenOn: Flow<Unit>,
) {
    val linked by remember(jellyfin) { jellyfin.link.map { Linked(it) } }.collectAsStateWithLifecycle(initialValue = null)
    var jellyfinConnectOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(homePresses) { homePresses.collect { jellyfinConnectOpen = false } }
    val health by library.health.collectAsStateWithLifecycle()
    val retryScope = rememberCoroutineScope()

    // Pendant une coupure, on redemande au serveur toutes les minutes ; « Réessayer » le fait tout de suite.
    var retryAt by remember { mutableStateOf<Long?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(health.reachable, linked?.link) {
        retryAt = null
        if (linked?.link == null) return@LaunchedEffect
        // Seulement quand Nimbe est à l'écran : pendant un film dans une autre appli, on laisse le Pi tranquille.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Jellyfin est vérifié directement toutes les 30 s, même quand tout va bien : couper Jellyfin
            // doit se voir sur le voyant en moins d'une minute, sans attendre qu'on ouvre Films.
            if (health.reachable) {
                while (true) {
                    delay(CHECK_INTERVAL_MS)
                    library.check()
                }
            }
            while (true) {
                retryAt = System.currentTimeMillis() + CHECK_INTERVAL_MS
                delay(CHECK_INTERVAL_MS)
                if (library.check()) break
            }
        }
    }
    val ha by homeAssistantMonitor.live.collectAsStateWithLifecycle()
    val piDown = ha.connection is HaConnection.Lost
    val piUp = ha.connection is HaConnection.Live
    val ui = remember(linked, health, retryAt, piDown, piUp) {
        JellyfinUi(
            library = library,
            link = linked?.link,
            linkKnown = linked != null,
            onConnect = { jellyfinConnectOpen = true },
            health = health,
            retryAt = retryAt,
            onRetry = {
                retryScope.launch { library.check() }
                homeAssistantMonitor.retryNow()
            },
            piDown = piDown,
            piUp = piUp,
        )
    }
    // Tant que le premier lancement n'est pas terminé, ou s'il n'y a plus aucun profil, Nimbe s'ouvre dessus ;
    // rien avant d'avoir lu les réglages.
    val setupDone by prefs.setupDone.collectAsStateWithLifecycle(initialValue = null)
    val profilesState by profiles.state.collectAsStateWithLifecycle(initialValue = null)
    val done = setupDone
    val state = profilesState
    if (done == null || state == null) {
        Box(Modifier.fillMaxSize().background(Nuit))
        return
    }
    // Une fois ouvert, l'assistant reste affiché jusqu'à « Continuer » : créer le premier profil ne le ferme pas.
    var inSetup by rememberSaveable { mutableStateOf(false) }
    val showSetup = inSetup || !done || state.profiles.isEmpty()
    LaunchedEffect(showSetup) { if (showSetup) inSetup = true }
    if (showSetup) {
        FirstLaunchScreen(
            jellyfin,
            homeAssistant,
            profiles,
            state,
            repository,
            prefs,
            launcher,
            updates,
            onFinish = {
                retryScope.launch {
                    prefs.setSetupDone(true)
                    inSetup = false
                }
            },
            startAt = if (done) SetupStep.Profiles else SetupStep.Server,
        )
        return
    }
    CompositionLocalProvider(LocalJellyfin provides ui) {
        if (jellyfinConnectOpen) {
            JellyfinConnectScreen(jellyfin, linked?.link?.serverUrl, onClose = { jellyfinConnectOpen = false })
        } else {
            NimbeShell(
                repository, prefs, launcher, connectivityMonitor, launches, updates, jellyfin, homeAssistant, nextcloud, gallery, ha,
                homeAssistantMonitor::history, profiles, state, parentPin, layouts, screenTime, homePresses, screenOn,
            )
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
    homeAssistant: HomeAssistantAccount,
    nextcloud: NextcloudAccounts,
    gallery: NextcloudGallery,
    ha: HaLive,
    loadHistory: suspend (String) -> List<HistoryPoint>,
    profiles: ProfilesStore,
    profilesState: ProfilesState,
    parentPin: ParentPinStore,
    layouts: LayoutStore,
    screenTime: ScreenTimeStore,
    homePresses: Flow<Unit>,
    screenOn: Flow<Unit>,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var settingsSection by rememberSaveable { mutableStateOf(SettingsSection.Display) }
    // Réglages protégés : le PIN parent est demandé avant de les ouvrir, une fois par visite.
    val security by parentPin.settings.collectAsStateWithLifecycle(initialValue = null)
    var settingsPinOpen by rememberSaveable { mutableStateOf(false) }
    fun openSettings(section: SettingsSection) {
        settingsSection = section
        // Tant que le réglage n'est pas lu, on le considère protégé : jamais d'ouverture sans le PIN par erreur.
        if (security?.settingsLocked != false) settingsPinOpen = true else settingsOpen = true
    }
    // « Qui regarde ? » au démarrage de Nimbe et quand la TV s'allume, si la TV est réglée pour demander.
    var whoOpen by rememberSaveable { mutableStateOf(profilesState.asksOnStart) }
    val currentProfiles by rememberUpdatedState(profilesState)
    LaunchedEffect(screenOn) {
        screenOn.collect { if (currentProfiles.asksOnStart) whoOpen = true }
    }
    var statusOpen by rememberSaveable { mutableStateOf(false) }
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
    // Profils Invité et Enfant : pas de réglages, « Mes applis » de la famille intouchable, rien dans les récentes.
    // L'invité ne voit pas les applis des comptes de la famille ; l'enfant ne voit que les applis autorisées.
    val active = profilesState.active
    val guest = active.isGuest
    val child = active.isChild
    val restricted = active.isRestricted
    val shownHidden = when {
        guest -> hidden + GuestMode.hiddenApps(active!!)
        child -> hidden + apps.orEmpty().map { it.packageName }.filterNot { it in ChildMode.allowedApps(active!!) }
        else -> hidden
    }
    val openApp: (InstalledApp) -> Unit = { launcher.open(it, record = !restricted) }
    val visibleApps = apps?.filterNot { it.packageName in shownHidden }
    val recentLaunches by remember(launches) { launches.recent(limit = 20) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val recents = if (restricted) emptyList() else apps?.let { Recents.resolve(recentLaunches.map { it.packageName }, it, hidden) }.orEmpty()
    val dock = when {
        apps == null || savedDock == null -> null
        // L'enfant a toutes ses applis autorisées sur l'accueil, peu importe le dock de la famille.
        child -> visibleApps
        else -> Dock.resolve(savedDock?.packages, apps, shownHidden)
    }
    // Toute modification part du dock affiché, pour que le dock par défaut devienne le point de départ.
    val dockPackages = dock.orEmpty().map { it.packageName }

    val scope = rememberCoroutineScope()

    // Mise en page du profil actif : celle par défaut le temps de la lire.
    val activeId = profilesState.active?.id.orEmpty()
    val savedLayout by remember(activeId) { layouts.layout(activeId) }.collectAsStateWithLifecycle(initialValue = null)
    val layout = when {
        guest -> LayoutRules.normalize(GuestMode.Layout)
        child -> LayoutRules.normalize(ChildMode.layout(active))
        else -> savedLayout ?: ProfileLayout.Default
    }
    val visibleTabs = layout.visibleTabs.mapNotNull(Tab::fromKey)
    fun saveLayout(changed: ProfileLayout) {
        scope.launch { layouts.save(activeId, changed) }
    }
    // Changer de profil ouvre son onglet de démarrage ; un onglet qui vient d'être masqué rend la main à l'Accueil.
    var startedFor by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(activeId, savedLayout != null) {
        val loaded = savedLayout ?: return@LaunchedEffect
        if (startedFor != activeId) {
            startedFor = activeId
            focusTab(Tab.fromKey(loaded.startTab) ?: Tab.Home)
        }
    }
    LaunchedEffect(visibleTabs) { if (tab !in visibleTabs) focusTab(Tab.Home) }
    var homeEditOpen by rememberSaveable { mutableStateOf(false) }
    var leavingTo by remember { mutableStateOf<Profile?>(null) }

    // Temps d'écran du profil Enfant, recalculé toutes les 15 s : au-delà, « Temps écoulé » passe devant tout.
    val usage by remember(activeId) { screenTime.usage(activeId) }.collectAsStateWithLifecycle(initialValue = null)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(child) {
        while (child) {
            now = System.currentTimeMillis()
            delay(SCREEN_TIME_TICK_MS)
        }
    }
    val screenVerdict = usage?.takeIf { child }?.let { ScreenTime.verdict(active!!.limits, it, now, ZoneId.systemDefault()) }
    var bonusPinOpen by remember { mutableStateOf(false) }
    val menu = remember { MenuState() }
    // Quand une action fait disparaître la tuile qui a le focus, on pose le focus sur l'onglet courant
    // avant d'enregistrer : sinon Android le donne au premier élément venu, l'onglet Accueil.
    fun leaveTile() {
        menu.dropReturnFocus()
        focusTab(tab)
    }
    val actions = AppActions(
        open = openApp,
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
    // État de l'onglet Galerie (rubrique, album ouvert, positions), un par profil : il survit au plein écran.
    val galleryNav = remember(activeId) { GalleryNav() }
    galleryNav.holdFocus = { runCatching { requesters.getValue(Tab.Gallery).requestFocus() } }
    // Retour : referme d'abord l'album ou le souvenir ouvert dans la Galerie, sinon revient à l'Accueil.
    BackHandler {
        if (tab == Tab.Gallery && galleryNav.open != null) galleryNav.close() else focusTab(Tab.Home)
    }
    // Accueil, depuis n'importe où dans Nimbe : réglages fermés, focus sur l'onglet Accueil.
    LaunchedEffect(homePresses) {
        homePresses.collect {
            settingsOpen = false
            settingsPinOpen = false
            statusOpen = false
            whoOpen = false
            homeEditOpen = false
            leavingTo = null
            returnToSettingsButton = false
            focusTab(Tab.Home)
        }
    }

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

    // Quitter le profil Enfant demande le PIN parent (tant que le réglage n'est pas lu, on le demande aussi).
    leavingTo?.let { target ->
        PinPrompt(
            parentPin,
            title = "Quitter le profil ${active?.name.orEmpty()}",
            subtitle = "Saisis le PIN parent pour passer au profil ${target.name}.",
            onSuccess = {
                scope.launch { profiles.setActive(target.id) }
                leavingTo = null
                whoOpen = false
            },
            onCancel = { leavingTo = null },
        )
        return
    }

    if (screenVerdict?.blocked == true && !whoOpen) {
        if (bonusPinOpen) {
            PinPrompt(
                parentPin,
                title = "Un peu de rab",
                subtitle = "Saisis le PIN parent pour ${ScreenTime.formatDuration(ScreenTime.BONUS_MS)} de plus.",
                onSuccess = {
                    val granted = System.currentTimeMillis()
                    scope.launch { screenTime.update(activeId) { ScreenTime.grantBonus(it, granted) } }
                    now = granted
                    bonusPinOpen = false
                },
                onCancel = { bonusPinOpen = false },
            )
        } else {
            TimeUpScreen(active!!, screenVerdict, onBonus = { bonusPinOpen = true }, onSwitchProfile = { whoOpen = true })
        }
        return
    }

    if (whoOpen) {
        WhoIsWatchingScreen(
            profilesState,
            onPick = { profile ->
                if (leavingNeedsPin(active, profile, pinSet = security?.pinSet != false)) {
                    leavingTo = profile
                } else {
                    scope.launch { profiles.setActive(profile.id) }
                    whoOpen = false
                }
            },
            onManage = if (restricted) null else {
                {
                    whoOpen = false
                    openSettings(SettingsSection.Profiles)
                }
            },
            onClose = { whoOpen = false },
        )
        return
    }

    if (homeEditOpen) {
        HomeEditScreen(
            layout = layout,
            profileName = profilesState.active?.name.orEmpty(),
            onChange = ::saveLayout,
            onClose = { homeEditOpen = false },
        )
        return
    }

    val jellyfinUi = LocalJellyfin.current
    val status = ServerStatus.verdict(connectivity.state, jellyfinDown = jellyfinUi?.down == true, ha = ha)
    if (statusOpen) {
        ServerStatusScreen(
            verdict = status,
            ha = ha,
            jellyfin = JellyfinWatch(linked = jellyfinUi?.link != null, health = jellyfinUi?.health ?: JellyfinHealth()),
            loadHistory = loadHistory,
            onRetry = { jellyfinUi?.onRetry?.invoke() },
            onClose = { statusOpen = false },
        )
        return
    }

    if (settingsPinOpen) {
        PinPrompt(
            parentPin,
            title = "Réglages protégés",
            subtitle = "Saisis le PIN parent pour ouvrir les réglages.",
            onSuccess = {
                settingsPinOpen = false
                settingsOpen = true
            },
            onCancel = { settingsPinOpen = false },
        )
        return
    }

    if (settingsOpen) {
        SettingsScreen(
            display = display,
            onDisplayChange = { scope.launch { prefs.setDisplay(it) } },
            links = SettingsLinks(
                accessibility = launcher::openAccessibilitySettings,
                androidSettings = launcher::openAndroidSettings,
                screensaverSettings = launcher::openScreensaverSettings,
                displaySoundSettings = launcher::openDisplaySoundSettings,
                stockLauncher = { StockLauncher.open(context) },
            ),
            updates = updates,
            jellyfin = jellyfin,
            homeAssistant = homeAssistant,
            nextcloud = nextcloud,
            gallery = gallery,
            profiles = profiles,
            profilesState = profilesState,
            parentPin = parentPin,
            layout = layout,
            onLayoutChange = ::saveLayout,
            onLayoutReset = { scope.launch { layouts.remove(activeId) } },
            onProfileDeleted = { id ->
                scope.launch {
                    layouts.remove(id)
                    screenTime.remove(id)
                    // Sa galerie aussi : un accès sans profil ne doit pas rester ouvert sur le serveur.
                    nextcloud.disconnect(id)
                    gallery.prefs.remove(id)
                }
            },
            installedApps = apps,
            onEditHome = {
                settingsOpen = false
                returnToSettingsButton = false
                // On modifie l'accueil : c'est lui qu'on retrouve en sortant.
                tab = Tab.Home
                homeEditOpen = true
            },
            section = settingsSection,
            onSectionChange = { settingsSection = it },
            screensaverPreview = {
                val saver by (context.applicationContext as NimbeApplication).screensaver.prefs.collectAsStateWithLifecycle(initialValue = null)
                saver?.let { ScreensaverHost(status, ha, it, profilesState.active, gallery) } ?: ScreensaverContent(status, ha)
            },
            onOpenUpdate = { updateOpen = true },
            onRestartSetup = {
                settingsOpen = false
                scope.launch { prefs.setSetupDone(false) }
            },
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
    // Photo en plein écran, par-dessus les onglets. La grille garde sa position (un état par profil)
    // et, au retour, la photo regardée en dernier reprend le focus.
    var galleryViewer by remember { mutableStateOf<GalleryViewerRequest?>(null) }
    // Lecture : par-dessus tout le reste. Une carte entamée demande d'abord « Reprendre ou recommencer ».
    var playing by remember { mutableStateOf<PlayRequest?>(null) }
    var askResume by remember { mutableStateOf<MediaItem?>(null) }
    // Gardés d'un épisode à l'autre : épisodes vus sans toucher la télécommande, minuteur de veille.
    var untouchedEpisodes by remember { mutableIntStateOf(0) }
    var sleepTimer by remember { mutableStateOf<SleepTimer?>(null) }
    // Accueil ferme aussi la fiche et le lecteur, qui enregistre la progression en partant.
    LaunchedEffect(homePresses) {
        homePresses.collect {
            playing = null
            askResume = null
            destination = null
            galleryViewer = null
            restoreFocusId = null
            untouchedEpisodes = 0
            sleepTimer = null
        }
    }
    fun requestPlay(item: MediaItem) {
        restoreFocusId = item.id
        if (ResumeRule.shouldAsk(item)) askResume = item else playing = ResumeRule.direct(item)
    }
    // Depuis une fiche, le bouton dit déjà s'il reprend ou repart du début.
    val onPlay: (MediaItem, Boolean) -> Unit = { item, fromStart ->
        playing = if (fromStart || !item.resumable) PlayRequest(item, 0) else ResumeRule.resume(item)
    }
    playing?.let { request ->
        // Une clé par contenu : passer à l'épisode suivant crée un nouveau lecteur.
        key(request) {
            PlayerScreen(
                request = request,
                account = jellyfin,
                reportScope = (context.applicationContext as NimbeApplication).scope,
                reportToServer = !guest,
                untouchedBefore = untouchedEpisodes,
                sleepTimer = sleepTimer,
                onSleepTimerChange = { sleepTimer = it },
                onNext = { next, untouched ->
                    untouchedEpisodes = untouched
                    restoreFocusId = next.item.id
                    playing = next
                },
                onClose = {
                    playing = null
                    untouchedEpisodes = 0
                    sleepTimer = null
                },
            )
        }
        return
    }
    askResume?.let { item ->
        ResumeDialog(
            item = item,
            onPlay = {
                askResume = null
                playing = it
            },
            onDismiss = { askResume = null },
        )
        return
    }
    galleryViewer?.let { request ->
        GalleryViewer(request, onClose = { last ->
            galleryNav.restoreFileId = last.fileId
            galleryViewer = null
        })
        return
    }
    when (val target = destination) {
        is MediaDestination.Movie -> {
            FilmDetailScreen(target.item, onPlay = onPlay, onClose = { destination = null })
            return
        }
        is MediaDestination.Series -> {
            SeriesDetailScreen(target, onPlay = onPlay, onRequestPlay = ::requestPlay, onClose = { destination = null })
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
                tabs = visibleTabs,
                selected = tab,
                onSelect = { tab = it },
                requesters = requesters,
                connectivity = connectivity,
                jellyfinDown = LocalJellyfin.current?.down == true,
                status = status,
                onStatus = { statusOpen = true },
                onSettings = if (restricted) null else {
                    {
                        returnToSettingsButton = true
                        openSettings(SettingsSection.Display)
                    }
                },
                settingsRequester = settingsRequester,
                profile = profilesState.active,
                onProfile = { whoOpen = true },
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
                        memories = { GalleryHomeRow(gallery, profilesState.active, onOpen = { galleryViewer = it }) },
                        rows = layout.visibleRows,
                        welcome = when {
                            guest -> HomeWelcome(
                                "PROFIL INVITÉ",
                                "Bienvenue, fais comme chez toi",
                                "Les films de la maison et la TV sont ouverts. Rien n'est enregistré : pas d'historique, " +
                                    "pas de galerie, pas de compte personnel.",
                            )
                            child -> HomeWelcome(
                                "PROFIL ENFANT",
                                "Coucou ${active?.name.orEmpty()} !",
                                ((screenVerdict as? ScreenTimeVerdict.Allowed)?.remainingMs?.let {
                                    "Encore ${ScreenTime.formatDuration(it)} de télé aujourd'hui. "
                                } ?: "") + "Tes films, tes séries et tes applis. Pour changer de profil, demande à un parent.",
                            )
                            else -> null
                        },
                        dock = dock,
                        onOpen = openApp,
                        onMenu = { app, back -> menu.open(if (restricted) lockedAppMenu(app, openApp, back) else appMenu(app, dockPackages, actions, back)) },
                        onAllApps = { focusTab(Tab.Apps) },
                        connectivity = connectivity,
                        onNetworkSettings = launcher::openNetworkSettings,
                        onOpenItem = ::openItem,
                        onPlayItem = ::requestPlay,
                        restoreFocusId = restoreFocusId,
                        onRestored = { restoreFocusId = null },
                    )
                    Tab.Apps -> AppsScreen(
                        apps = visibleApps,
                        recents = recents,
                        // L'invité et l'enfant ne voient pas la tuile « Masquées » : ils ne peuvent rien remettre.
                        hiddenCount = if (restricted) 0 else apps?.count { it.packageName in hidden } ?: 0,
                        onOpen = openApp,
                        onMenu = { app, back -> menu.open(if (restricted) lockedAppMenu(app, openApp, back) else appMenu(app, dockPackages, actions, back)) },
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
                    Tab.Gallery -> GalleryScreen(
                        gallery,
                        profilesState.active,
                        onOpenSettings = { openSettings(SettingsSection.Servers) },
                        nav = galleryNav,
                        onOpen = { galleryViewer = it },
                    )
                }
            }
        }
    }
}

/** Recalcul du temps d'écran restant du profil Enfant. */
private const val SCREEN_TIME_TICK_MS = 15_000L

/** Vérification directe de Jellyfin, et essai automatique pendant une coupure. */
private const val CHECK_INTERVAL_MS = 30_000L

/** Au-delà, la carte d'origine n'est plus affichée (liste rechargée) : le focus retourne sur l'onglet. */
private const val RESTORE_FOCUS_TIMEOUT_MS = 800L
