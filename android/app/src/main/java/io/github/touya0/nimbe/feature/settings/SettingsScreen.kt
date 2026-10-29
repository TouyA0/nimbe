package io.github.touya0.nimbe.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantAccount
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantLink
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.data.jellyfin.JellyfinLink
import io.github.touya0.nimbe.data.layout.LayoutRules
import io.github.touya0.nimbe.data.layout.ProfileLayout
import io.github.touya0.nimbe.data.layout.TabKeys
import io.github.touya0.nimbe.data.profiles.GuestMode
import io.github.touya0.nimbe.data.profiles.ProfileKind
import io.github.touya0.nimbe.data.profiles.ProfilesState
import io.github.touya0.nimbe.data.profiles.ProfilesStore
import io.github.touya0.nimbe.data.profiles.StartChoice
import io.github.touya0.nimbe.data.security.ParentPinStore
import io.github.touya0.nimbe.data.security.PinLockout
import io.github.touya0.nimbe.data.security.SecuritySettings
import io.github.touya0.nimbe.feature.homeassistant.HomeAssistantColor
import io.github.touya0.nimbe.feature.homeassistant.HomeAssistantConnectController
import io.github.touya0.nimbe.feature.homeassistant.HomeAssistantConnectScreen
import io.github.touya0.nimbe.feature.layout.HomePreview
import io.github.touya0.nimbe.feature.layout.LayoutEditor
import io.github.touya0.nimbe.feature.layout.homeRowLabel
import io.github.touya0.nimbe.feature.layout.tabLabel
import io.github.touya0.nimbe.feature.onboarding.JellyfinConnectScreen
import io.github.touya0.nimbe.feature.screensaver.ScreensaverSetting
import io.github.touya0.nimbe.feature.onboarding.ServiceInitial
import io.github.touya0.nimbe.feature.apps.InstalledApp
import io.github.touya0.nimbe.feature.profiles.GuestAppsScreen
import io.github.touya0.nimbe.feature.profiles.ProfilesManager
import io.github.touya0.nimbe.feature.security.PinChange
import io.github.touya0.nimbe.feature.security.PinChangeScreen
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.DisplaySettings
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import io.github.touya0.nimbe.update.CheckResult
import io.github.touya0.nimbe.update.Releases
import io.github.touya0.nimbe.update.UpdateChannel
import io.github.touya0.nimbe.update.UpdateController
import io.github.touya0.nimbe.update.UpdateInstaller
import io.github.touya0.nimbe.update.UpdateProgress
import io.github.touya0.nimbe.update.UpdateState
import io.github.touya0.nimbe.update.formatSize
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Les rubriques déjà construites ; les autres de la maquette arriveront avec leurs fonctions. */
enum class SettingsSection(val label: String) {
    Home("Accueil"),
    Tabs("Onglets"),
    Profiles("Profils"),
    Display("Affichage et accessibilité"),
    Screensaver("Écran de veille"),
    Servers("Serveurs et comptes"),
    Security("Confidentialité et sécurité"),
    Updates("Mises à jour"),
    About("À propos et diagnostic"),
}

/** Liens vers Android, fournis par l'activité. */
class SettingsLinks(
    val accessibility: () -> Unit,
    val androidSettings: () -> Unit,
    val screensaverSettings: () -> Unit,
    val displaySoundSettings: () -> Unit,
    val stockLauncher: () -> Unit,
)

@Composable
fun SettingsScreen(
    display: DisplaySettings,
    onDisplayChange: (DisplaySettings) -> Unit,
    links: SettingsLinks,
    updates: UpdateController,
    jellyfin: JellyfinAccount,
    homeAssistant: HomeAssistantAccount,
    profiles: ProfilesStore,
    profilesState: ProfilesState,
    parentPin: ParentPinStore,
    /** Mise en page du profil actif (Accueil, Onglets), enregistrée à chaque changement. */
    layout: ProfileLayout,
    onLayoutChange: (ProfileLayout) -> Unit,
    onLayoutReset: () -> Unit,
    onProfileDeleted: (String) -> Unit,
    /** Applis du stick, pour choisir celles que l'invité ne voit pas ; null pendant le chargement. */
    installedApps: List<InstalledApp>?,
    /** Ouvre le mode « Modifier l'accueil » sur l'accueil lui-même. */
    onEditHome: () -> Unit,
    /** Rubrique affichée, gardée par l'appelant pour ouvrir les réglages au bon endroit (« Gérer les profils »). */
    section: SettingsSection,
    onSectionChange: (SettingsSection) -> Unit,
    /** L'écran de veille en plein écran, tel qu'Android l'affichera. */
    screensaverPreview: @Composable () -> Unit,
    onOpenUpdate: () -> Unit,
    /** Rouvre l'assistant du premier lancement ; les services reliés le restent. */
    onRestartSetup: () -> Unit,
    onClose: () -> Unit,
) {
    val updateState by updates.state.collectAsStateWithLifecycle(initialValue = UpdateState())
    val sectionRequesters = remember { SettingsSection.entries.associateWith { FocusRequester() } }

    // Ouvert par-dessus les réglages : en revenant, on retrouve la même rubrique.
    var jellyfinConnectOpen by rememberSaveable { mutableStateOf(false) }
    val jellyfinLink by remember(jellyfin) { jellyfin.link.map { Linked(it) } }.collectAsStateWithLifecycle(initialValue = null)
    if (jellyfinConnectOpen) {
        JellyfinConnectScreen(jellyfin, jellyfinLink?.link?.serverUrl, onClose = { jellyfinConnectOpen = false })
        return
    }
    // Créer, changer ou retirer le PIN parent : en plein écran, par-dessus la rubrique.
    var pinChange by remember { mutableStateOf<PinChange?>(null) }
    pinChange?.let { change ->
        PinChangeScreen(parentPin, change, onDone = { pinChange = null }, onCancel = { pinChange = null })
        return
    }
    val security by parentPin.settings.collectAsStateWithLifecycle(initialValue = null)
    // Applis masquées pour l'invité : en plein écran, par-dessus Profils.
    var guestAppsOpen by remember { mutableStateOf(false) }
    val guestProfile = profilesState.profiles.firstOrNull { it.kind == ProfileKind.Guest }
    if (guestAppsOpen && guestProfile != null) {
        val scope = rememberCoroutineScope()
        GuestAppsScreen(
            apps = installedApps,
            hidden = GuestMode.hiddenApps(guestProfile),
            onChange = { scope.launch { profiles.save(guestProfile.copy(hiddenApps = it)) } },
            onReset = { scope.launch { profiles.save(guestProfile.copy(hiddenApps = null)) } },
            onClose = { guestAppsOpen = false },
        )
        return
    }
    var previewOpen by remember { mutableStateOf(false) }
    if (previewOpen) {
        ScreensaverPreview(screensaverPreview, onClose = { previewOpen = false })
        return
    }
    var homeAssistantConnectOpen by rememberSaveable { mutableStateOf(false) }
    val homeAssistantLink by remember(homeAssistant) { homeAssistant.link.map { HaLinked(it) } }
        .collectAsStateWithLifecycle(initialValue = null)
    if (homeAssistantConnectOpen) {
        HomeAssistantConnectScreen(
            homeAssistant,
            fallbackAddress = homeAssistantGuess(jellyfinLink?.link?.serverUrl),
            onClose = { homeAssistantConnectOpen = false },
        )
        return
    }

    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { runCatching { sectionRequesters.getValue(section).requestFocus() } }

    // Recalculé à chaque changement de rubrique, et après « Vider le cache ».
    var aboutVersion by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val about by produceState<AboutInfo?>(null, aboutVersion, section) { value = loadAbout(context) }
    // Au retour des réglages Android (TalkBack, par exemple), on relit l'état du stick.
    LifecycleResumeEffect(Unit) {
        aboutVersion++
        onPauseOrDispose {}
    }

    Row(Modifier.fillMaxSize().background(Nuit)) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(Panneau).padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NavItem("‹  Retour à l'accueil", selected = false, onClick = onClose)
            Text("Réglages", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(vertical = 12.dp))
            Column(Modifier.focusRestorer(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsSection.entries.forEach { item ->
                    NavItem(
                        label = item.label,
                        selected = item == section,
                        onClick = { onSectionChange(item) },
                        onFocus = { onSectionChange(item) },
                        badge = item == SettingsSection.Updates && updates.isNewer(updateState.available),
                        modifier = Modifier.focusRequester(sectionRequesters.getValue(item)),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            about?.let { Text("Nimbe ${it.version}", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee) }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (section) {
                SettingsSection.Home -> HomeLayoutPage(layout, profilesState.active?.name.orEmpty(), onLayoutChange, onLayoutReset, onEditHome)
                SettingsSection.Tabs -> TabsPage(layout, profilesState.active?.name.orEmpty(), onLayoutChange, onLayoutReset)
                SettingsSection.Profiles -> ProfilesPage(profiles, profilesState, installedApps, onProfileDeleted, onGuestApps = { guestAppsOpen = true })
                SettingsSection.Display -> DisplayPage(display, onDisplayChange, about, links)
                SettingsSection.Screensaver -> ScreensaverPage(links, onPreview = { previewOpen = true })
                SettingsSection.Servers -> ServersPage(
                    jellyfin,
                    jellyfinLink,
                    onConnectJellyfin = { jellyfinConnectOpen = true },
                    homeAssistant,
                    homeAssistantLink,
                    onConnectHomeAssistant = { homeAssistantConnectOpen = true },
                )
                SettingsSection.Security -> SecurityPage(parentPin, security, profilesState, onChange = { pinChange = it })
                SettingsSection.Updates -> UpdatesPage(updateState, updates, onOpenUpdate)
                SettingsSection.About -> AboutPage(about, links, onCacheCleared = { aboutVersion++ }, onRestartSetup = onRestartSetup)
            }
        }
    }
}

@Composable
private fun HomeLayoutPage(
    layout: ProfileLayout,
    profileName: String,
    onChange: (ProfileLayout) -> Unit,
    onReset: () -> Unit,
    onEditHome: () -> Unit,
) {
    PageTitle("Accueil", "Les rangées de l'accueil et leur ordre. Pour le profil $profileName.")
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        LayoutEditor(
            items = layout.homeRows,
            label = ::homeRowLabel,
            onChange = { onChange(layout.copy(homeRows = it)) },
            modifier = Modifier.weight(1f),
        )
        HomePreview(layout)
    }
    SettingsGroupTitle("Autres façons")
    LinkRow("Modifier directement sur l'accueil", "Les rangées en grand, avec l'aperçu à côté", value = "Ouvrir", onClick = onEditHome)
    LinkRow("Rétablir par défaut", "Toutes les rangées, dans l'ordre d'origine", value = "Rétablir", onClick = onReset)
}

@Composable
private fun TabsPage(layout: ProfileLayout, profileName: String, onChange: (ProfileLayout) -> Unit, onReset: () -> Unit) {
    PageTitle("Onglets", "Les onglets de l'en-tête et leur ordre. Pour le profil $profileName.")
    LayoutEditor(
        items = layout.tabs,
        label = ::tabLabel,
        onChange = { onChange(layout.copy(tabs = it)) },
        hideProblem = LayoutRules::hideTabProblem,
        fixed = { it == TabKeys.HOME },
    )
    SettingsGroupTitle("Au démarrage")
    LinkRow(
        "Onglet au démarrage",
        "Celui qui s'ouvre quand ce profil est choisi · OK pour changer",
        value = tabLabel(layout.startTab),
        onClick = {
            val visible = layout.visibleTabs
            val next = visible[(visible.indexOf(layout.startTab) + 1).mod(visible.size)]
            onChange(layout.copy(startTab = next))
        },
    )
    LinkRow("Rétablir par défaut", "Tous les onglets, dans l'ordre d'origine, Accueil au démarrage", value = "Rétablir", onClick = onReset)
}

@Composable
private fun ProfilesPage(
    store: ProfilesStore,
    state: ProfilesState,
    installedApps: List<InstalledApp>?,
    onDeleted: (String) -> Unit,
    onGuestApps: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    PageTitle(
        "Profils",
        "Chaque profil a son accueil et ses réglages. Jellyfin et Home Assistant sont communs à toute la maison.",
    )
    ProfilesManager(
        profiles = state.profiles,
        onSave = { scope.launch { store.save(it) } },
        onDelete = {
            scope.launch { store.delete(it.id) }
            onDeleted(it.id)
        },
    ) {
        state.profiles.firstOrNull { it.kind == ProfileKind.Guest }?.let { guest ->
            SettingsGroupTitle("Profil ${guest.name}")
            // Seules comptent les applis installées : la liste par défaut en cite d'autres.
            val count = GuestMode.hiddenApps(guest).count { id -> installedApps?.any { it.packageName == id } == true }
            LinkRow(
                "Applis masquées pour l'invité",
                if (guest.hiddenApps == null) {
                    "Comptes de la famille (Netflix, Disney+, Play Store…) et réglages Android"
                } else {
                    "Choisies à la main"
                },
                value = if (count == 0) "Aucune" else "$count masquée(s)",
                onClick = onGuestApps,
            )
        }
        SettingsGroupTitle("Pour toute la TV")
        LinkRow(
            "Quand la TV s'allume",
            if (state.profiles.size > 1) "OK pour changer" else "Sans effet tant qu'il n'y a qu'un profil",
            value = state.onStart.label,
            onClick = {
                val next = if (state.onStart == StartChoice.Ask) StartChoice.ResumeLast else StartChoice.Ask
                scope.launch { store.setOnStart(next) }
            },
        )
    }
}

@Composable
private fun SecurityPage(store: ParentPinStore, settings: SecuritySettings?, profiles: ProfilesState, onChange: (PinChange) -> Unit) {
    val scope = rememberCoroutineScope()
    PageTitle("Confidentialité et sécurité", "Qui peut changer les réglages de la TV. Pour toute la TV.")
    if (settings == null) return
    SettingsGroupTitle("Verrouillage")
    // Un profil Enfant sans PIN parent n'est protégé par rien : on le dit en premier.
    if (!settings.pinSet && profiles.profiles.any { it.kind == ProfileKind.Child }) {
        SettingRow(
            title = "Un profil Enfant existe sans PIN parent",
            detail = "Sans PIN, rien n'empêche d'en sortir ni d'ouvrir les réglages · OK pour en créer un",
            onClick = { onChange(PinChange.Create) },
            trailing = { Text("À créer", style = MaterialTheme.typography.titleSmall, color = Focus) },
        )
    }
    LinkRow(
        "Code PIN parent",
        "Quatre chiffres, pour les réglages et la sortie du profil Enfant",
        value = if (settings.pinSet) "Défini · changer" else "Créer",
        onClick = { onChange(if (settings.pinSet) PinChange.Change else PinChange.Create) },
    )
    if (settings.pinSet) {
        ToggleRow("Protéger les réglages", "Le PIN parent est demandé en entrant ici", settings.protectSettings) {
            scope.launch { store.setProtectSettings(it) }
        }
        LinkRow("Retirer le PIN parent", "Le code actuel est demandé", value = "Retirer", onClick = { onChange(PinChange.Remove) })
    }
    SettingRow(
        title = "Après ${PinLockout.MAX_FAILURES} codes faux",
        detail = "Saisie bloquée, puis le délai double à chaque nouveau blocage",
        onClick = {},
        trailing = { Value(PinLockout.formatWait(PinLockout.FIRST_LOCK_MS)) },
    )
}

@Composable
private fun DisplayPage(display: DisplaySettings, onChange: (DisplaySettings) -> Unit, about: AboutInfo?, links: SettingsLinks) {
    PageTitle("Affichage et accessibilité", "Lire Nimbe facilement de loin. Pour toute la TV.")
    SettingsGroupTitle("Lisibilité")
    TextSizeRow(display.textSize) { onChange(display.copy(textSize = it)) }
    ToggleRow("Contraste élevé", "Textes en blanc plein, focus blanc épais", display.highContrast) {
        onChange(display.copy(highContrast = it))
    }
    ToggleRow("Réduire les animations", "Pas d'agrandissement au focus", display.reduceMotion) {
        onChange(display.copy(reduceMotion = it))
    }
    ToggleRow("Rappels de touches OK", "En bas des écrans", display.keyHints) {
        onChange(display.copy(keyHints = it))
    }
    LinkRow(
        "Lecteur d'écran (TalkBack)",
        "Chaque élément est lu à voix haute · réglé dans Android",
        value = if (about?.talkBackOn == true) "Activé" else "Désactivé",
        onClick = links.accessibility,
    )
    // Android 12+ garde les réglages CEC pour lui : on explique ce qu'ils font et on ouvre le bon écran.
    SettingsGroupTitle("Commande de la TV (HDMI-CEC)")
    LinkRow(
        "Allumer, éteindre et régler le volume de la TV",
        "Le stick allume la TV et passe sur son entrée, l'éteint quand il se met en veille, et pilote le volume. " +
            "Réglé dans Android : Affichage et son → HDMI-CEC",
        value = "Ouvrir",
        onClick = links.displaySoundSettings,
    )
}

@Composable
private fun ScreensaverPage(links: SettingsLinks, onPreview: () -> Unit) {
    val context = LocalContext.current
    // Relu au retour des réglages Android, où l'écran de veille se choisit.
    var active by remember { mutableStateOf(ScreensaverSetting.isActive(context)) }
    LifecycleResumeEffect(Unit) {
        active = ScreensaverSetting.isActive(context)
        onPauseOrDispose {}
    }
    PageTitle("Écran de veille", "Ce qui s'affiche quand personne ne touche à la télécommande. Pour toute la TV.")
    SettingsGroupTitle("Heure et serveur")
    LinkRow(
        "Voir en plein écran",
        "Fond noir, heure, date et météo, état du serveur · n'importe quelle touche pour revenir",
        value = "Aperçu",
        onClick = onPreview,
    )
    LinkRow(
        "Nimbe comme écran de veille",
        when (active) {
            true -> "Android l'affiche après l'inactivité"
            else -> "Sur Google TV, à choisir une fois depuis le PC : adb shell settings put secure " +
                "screensaver_components ${ScreensaverSetting.component(context)}"
        },
        value = when (active) {
            true -> "Activé"
            false -> "Désactivé"
            null -> "Inconnu"
        },
        onClick = links.screensaverSettings,
    )
    SettingsGroupTitle("Bientôt")
    SettingRow("Photos", "Tes albums Nextcloud en plein écran, avec l'heure par-dessus · arrive avec la Galerie", onClick = {})
}

/** L'écran de veille dans Nimbe, comme Android l'affichera : n'importe quelle touche le ferme. */
@Composable
private fun ScreensaverPreview(content: @Composable () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) onClose()
                true
            },
    ) { content() }
}

/** Enveloppe pour distinguer « pas encore lu » (null) de « pas connecté ». */
private data class Linked(val link: JellyfinLink?)

private data class HaLinked(val link: HomeAssistantLink?)

/** Home Assistant tourne en général sur le même Pi que Jellyfin : on propose son adresse, port 8123. */
private fun homeAssistantGuess(jellyfinUrl: String?): String {
    val host = jellyfinUrl?.let { runCatching { URI(it).host }.getOrNull() } ?: return ""
    return "http://$host:${HomeAssistantConnectController.HOME_ASSISTANT_PORT}"
}

@Composable
private fun ServersPage(
    jellyfin: JellyfinAccount,
    linked: Linked?,
    onConnectJellyfin: () -> Unit,
    homeAssistant: HomeAssistantAccount,
    haLinked: HaLinked?,
    onConnectHomeAssistant: () -> Unit,
) {
    PageTitle("Serveurs et comptes", "Chaque accès est un jeton révocable. Aucun mot de passe n'est enregistré sur la TV.")
    SettingsGroupTitle("Sur ton réseau")
    if (linked == null || haLinked == null) {
        Text("Chargement…", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
        return
    }
    JellyfinRows(jellyfin, linked.link, onConnectJellyfin)
    HomeAssistantRows(homeAssistant, haLinked.link, onConnectHomeAssistant)
}

@Composable
private fun JellyfinRows(jellyfin: JellyfinAccount, link: JellyfinLink?, onConnectJellyfin: () -> Unit) {
    val scope = rememberCoroutineScope()
    val jellyfinRow = remember { FocusRequester() }
    if (link == null) {
        SettingRow(
            title = "Jellyfin",
            detail = "Films et séries · pas encore connecté",
            onClick = onConnectJellyfin,
            modifier = Modifier.focusRequester(jellyfinRow),
            leading = { ServiceInitial() },
            trailing = {
                Status("Non connecté", LuneAttenuee)
                Value("Connecter")
            },
        )
        return
    }

    // Vérifié à l'ouverture de la page : le serveur répond-il encore ?
    val reachable by produceState<Boolean?>(null, link.serverUrl) {
        value = runCatching { jellyfin.api.publicInfo(link.serverUrl) }.isSuccess
    }
    SettingRow(
        title = "Jellyfin",
        detail = "${link.serverUrl.substringAfter("://")} · compte ${link.userName} par Quick Connect",
        onClick = onConnectJellyfin,
        modifier = Modifier.focusRequester(jellyfinRow),
        leading = { ServiceInitial() },
        trailing = {
            when (reachable) {
                null -> Status("Vérification…", LuneAttenuee)
                true -> Status("Connecté", EnLigne)
                false -> Status("Injoignable", Alerte)
            }
            Value("Changer")
        },
    )
    // Deux appuis sur OK : un appui de trop ne doit pas couper l'accès.
    var confirm by remember { mutableStateOf(false) }
    LinkRow(
        title = "Déconnecter Jellyfin",
        detail = "Retire l'accès de cette TV, sur le serveur aussi",
        value = if (confirm) "OK pour confirmer" else "Déconnecter",
        onClick = {
            if (!confirm) {
                confirm = true
            } else {
                runCatching { jellyfinRow.requestFocus() }
                scope.launch { jellyfin.disconnect() }
            }
        },
    )
}

@Composable
private fun HomeAssistantRows(homeAssistant: HomeAssistantAccount, link: HomeAssistantLink?, onConnect: () -> Unit) {
    val scope = rememberCoroutineScope()
    val row = remember { FocusRequester() }
    // Après « Déconnecter », la ligne disparaît : le focus revient sur « Home Assistant ».
    var refocus by remember { mutableStateOf(false) }
    if (link == null) {
        LaunchedEffect(Unit) {
            if (refocus) runCatching { row.requestFocus() }
            refocus = false
        }
        SettingRow(
            title = "Home Assistant",
            detail = "État du Pi et des services · pas encore connecté",
            onClick = onConnect,
            modifier = Modifier.focusRequester(row),
            leading = { ServiceInitial("H", HomeAssistantColor) },
            trailing = {
                Status("Non connecté", LuneAttenuee)
                Value("Connecter")
            },
        )
        return
    }

    // Vérifié à l'ouverture de la page : le token est-il encore accepté ?
    val reachable by produceState<Boolean?>(null, link.serverUrl) {
        val auth = homeAssistant.auth()
        value = auth != null && runCatching { homeAssistant.api.session(auth.server, auth.token) { } }.isSuccess
    }
    val account = if (link.isAdmin) "compte « ${link.userName} » administrateur" else "compte « ${link.userName} » sans droits admin"
    SettingRow(
        title = "Home Assistant",
        detail = "${link.serverUrl.substringAfter("://")} · $account · ${link.sensors.size} capteurs suivis",
        onClick = onConnect,
        modifier = Modifier.focusRequester(row),
        leading = { ServiceInitial("H", HomeAssistantColor) },
        trailing = {
            when (reachable) {
                null -> Status("Vérification…", LuneAttenuee)
                true -> Status("Connecté", EnLigne)
                false -> Status("Injoignable", Alerte)
            }
            Value("Capteurs")
        },
    )
    var confirm by remember { mutableStateOf(false) }
    LinkRow(
        title = "Déconnecter Home Assistant",
        detail = "Oublie le token sur cette TV, et le révoque dans Home Assistant si le Pi répond",
        value = if (confirm) "OK pour confirmer" else "Déconnecter",
        onClick = {
            if (!confirm) {
                confirm = true
            } else {
                runCatching { row.requestFocus() }
                refocus = true
                scope.launch { homeAssistant.disconnect() }
            }
        },
    )
}

@Composable
private fun RowScope.Status(text: String, color: Color) {
    Text("● $text", style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun AboutPage(about: AboutInfo?, links: SettingsLinks, onCacheCleared: () -> Unit, onRestartSetup: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    PageTitle("À propos et diagnostic", "Version de Nimbe, état du stick et aide au dépannage.")
    if (about == null) {
        Text("Chargement…", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
        return
    }
    SettingsGroupTitle("Ce stick")
    LinkRow("Nimbe ${about.version}", about.device, value = about.android, onClick = links.androidSettings)
    LinkRow(
        "Touche Accueil",
        if (about.homeKeyOn) "Appui court : Nimbe · appui long : accueil Google TV" else "Ouvre Google TV · à activer une fois depuis le PC",
        value = if (about.homeKeyOn) "Activée" else "Désactivée",
        onClick = links.accessibility,
    )
    SettingRow(
        title = "Stockage",
        detail = "Cache de Nimbe ${formatBytes(about.cacheBytes)} · libre ${formatBytes(about.freeBytes)} " +
            "sur ${formatBytes(about.totalBytes)}",
        onClick = { scope.launch { clearCache(context); onCacheCleared() } },
        trailing = { Text("Vider le cache", style = MaterialTheme.typography.titleSmall) },
    )

    SettingsGroupTitle("Dépannage")
    SettingRow(
        title = "Journal des plantages",
        detail = about.lastError?.let { "Dernier le ${formatDate(it.at)} : ${it.summary}" } ?: "Aucun plantage enregistré",
        onClick = {},
    )
    LinkRow("Accueil d'origine du stick", "Toujours installé, c'est le secours", value = "Ouvrir", onClick = links.stockLauncher)
    LinkRow("Réglages Android", "Réseau, son, applis, mises à jour du stick", value = "Ouvrir", onClick = links.androidSettings)
    LinkRow(
        "Premier lancement",
        "Refaire l'assistant de départ · les services déjà reliés le restent",
        value = "Recommencer",
        onClick = onRestartSetup,
    )
}

@Composable
private fun UpdatesPage(state: UpdateState, updates: UpdateController, onOpenUpdate: () -> Unit) {
    PageTitle("Mises à jour", "Nimbe se met à jour tout seul depuis son dépôt GitHub, après avoir vérifié la signature.")
    val available = state.available?.takeIf(updates::isNewer)
    val progress = updates.progress

    val failure = state.failure
    if (failure != null) {
        LinkRow(
            title = "La version ${failure.versionName} n'a pas été installée",
            detail = failure.reason,
            value = "Voir",
            onClick = onOpenUpdate,
        )
    } else if (available != null) {
        LinkRow(
            title = "Version ${available.versionName} disponible · ${formatSize(available.size)}",
            detail = listOfNotNull(
                available.notes.firstOrNull(),
                if (state.ready) "Signature vérifiée, prête à installer" else "Pas encore téléchargée",
            ).joinToString(" · "),
            value = "Voir et installer",
            onClick = onOpenUpdate,
        )
    }

    SettingsGroupTitle("Comment mettre à jour")
    LinkRow(
        "Canal",
        "Bêta : nouveautés plus tôt, un peu moins stable",
        value = state.channel.label,
        onClick = { updates.setChannel(if (state.channel == UpdateChannel.Stable) UpdateChannel.Beta else UpdateChannel.Stable) },
    )
    if (UpdateInstaller.canInstallUnattended) {
        SettingRow("Installation", "La nuit entre 3 h et 4 h, seulement si rien ne joue", onClick = {}, trailing = { Value("Automatique") })
    } else {
        SettingRow(
            "Installation",
            "Téléchargée la nuit, puis proposée à l'écran : cette version d'Android demande un OK",
            onClick = {},
            trailing = { Value("Avec confirmation") },
        )
    }
    if (!updates.canInstall()) {
        LinkRow(
            "Autoriser Nimbe à installer",
            "Nécessaire une fois pour les mises à jour · réglé dans Android",
            value = "À accorder",
            onClick = updates::openInstallPermission,
        )
    }
    SettingRow(
        title = "Vérifier maintenant",
        detail = checkDetail(state, updates.checkResult, progress),
        onClick = updates::checkNow,
        trailing = { Value(if (progress == UpdateProgress.Checking) "Vérification…" else "Vérifier") },
    )
    SettingRow(
        "Vérification de la signature",
        "Toujours active, ne peut pas être désactivée",
        onClick = {},
        trailing = { Value("Obligatoire") },
    )

    SettingsGroupTitle("Version installée")
    SettingRow(
        title = "Nimbe ${updates.installed.versionName}",
        detail = "Installée le ${formatDate(updates.installedAt)} · github.com/${Releases.REPO}",
        onClick = {},
    )
}

@Composable
private fun RowScope.Value(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

private fun checkDetail(state: UpdateState, result: CheckResult?, progress: UpdateProgress?): String = when {
    progress == UpdateProgress.Checking -> "Recherche d'une nouvelle version sur GitHub…"
    result == CheckResult.UpToDate -> "Nimbe est à jour"
    result is CheckResult.Available -> "Version ${result.manifest.versionName} trouvée"
    result is CheckResult.Failed -> "${result.message}, réessaie plus tard"
    state.lastCheckAt == null -> "Pas encore vérifié · chaque nuit vers 3 h"
    state.lastCheckError != null -> "${state.lastCheckError} le ${formatDate(state.lastCheckAt)}"
    else -> "Dernière vérification le ${formatDate(state.lastCheckAt)}"
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.displaySmall)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun NavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    badge: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Lune else Color.Transparent,
            contentColor = if (selected) Nuit else Lune,
            focusedContainerColor = if (selected) Lune else PanneauClair,
            focusedContentColor = if (selected) Nuit else Lune,
            pressedContainerColor = Lune,
            pressedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            // Pastille « 1 » : une mise à jour attend.
            if (badge) {
                Box(Modifier.size(20.dp).background(Focus, CircleShape), contentAlignment = Alignment.Center) {
                    Text("1", style = MaterialTheme.typography.labelMedium, color = Nuit)
                }
            }
        }
    }
}

private val DateFormat = DateTimeFormatter.ofPattern("d MMMM 'à' HH:mm", Locale.FRENCH)

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateFormat)
