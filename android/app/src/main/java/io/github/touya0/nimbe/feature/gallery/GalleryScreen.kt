package io.github.touya0.nimbe.feature.gallery

import io.github.touya0.nimbe.ui.components.NimbeIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import io.github.touya0.nimbe.data.nextcloud.GalleryPrefs
import io.github.touya0.nimbe.data.nextcloud.NextcloudAlbum
import io.github.touya0.nimbe.data.nextcloud.NextcloudAuth
import io.github.touya0.nimbe.data.nextcloud.NextcloudException
import io.github.touya0.nimbe.data.nextcloud.NextcloudGallery
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfileKind
import io.github.touya0.nimbe.feature.media.FilterPill
import io.github.touya0.nimbe.feature.media.StateMessage
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.Spinner
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Onglet Galerie : rubriques Pour toi, Chronologie, Albums, Vidéos et Partagées, comme dans la maquette.
 * Tout ce qui vient de l'index local ou du cache s'affiche tout de suite, et même serveur coupé.
 * [nav] : gardé par l'appelant, pour retrouver rubrique, dossier ouvert et position au retour du plein écran.
 */
@Composable
fun GalleryScreen(
    gallery: NextcloudGallery,
    profile: Profile?,
    onOpenSettings: () -> Unit,
    nav: GalleryNav,
    onOpen: (GalleryViewerRequest) -> Unit,
) {
    if (profile?.kind == ProfileKind.Child) {
        ChildGalleryContent(gallery, profile, nav, onOpen)
        return
    }
    if (profile == null || profile.kind != ProfileKind.Adult) {
        StateMessage("Pas de galerie dans ce profil", "Les photos Nextcloud sont personnelles : chaque adulte relie la sienne.")
        return
    }
    val linked by remember(profile.id) { gallery.linked(profile.id) }.collectAsStateWithLifecycle(initialValue = null)
    // Relu à chaque changement du lien : Result.success(null) veut dire « mot de passe illisible ».
    val auth by produceState<Result<NextcloudAuth?>?>(null, profile.id, linked) {
        value = if (linked == true) Result.success(gallery.auth(profile.id)) else null
    }
    when {
        linked == null || (linked == true && auth == null) -> StateMessage("Chargement…", null)
        linked == false || auth?.getOrNull() == null -> StateMessage(
            title = if (linked == false) "Ta galerie n'est pas reliée" else "Relie à nouveau ta galerie",
            detail = "Relie ton compte Nextcloud dans Réglages → Serveurs et comptes : un QR code, et c'est fait.",
            action = "Ouvrir les réglages",
            onAction = onOpenSettings,
        )
        else -> GalleryContent(gallery, profile, auth!!.getOrThrow()!!, nav, onOpen)
    }
}

@Composable
private fun GalleryContent(gallery: NextcloudGallery, profile: Profile, auth: NextcloudAuth, nav: GalleryNav, onOpen: (GalleryViewerRequest) -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile.id) { gallery.sync(profile.id) }
    val state by remember(profile.id) { gallery.state(profile.id) }.collectAsStateWithLifecycle()
    val syncedAt by remember(profile.id) { gallery.syncedAt(profile.id) }.collectAsStateWithLifecycle(initialValue = null)
    val prefs by remember(profile.id) { gallery.prefs.prefs(profile.id) }.collectAsStateWithLifecycle(initialValue = GalleryPrefs())
    // Tout l'index, filtré selon les réglages (vidéos, captures d'écran), puis rangé par mois hors du fil de l'interface.
    val index by remember(profile.id) { gallery.all(profile.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val all = remember(index, prefs) { prefs.filter(index) }
    val timeline by produceState(emptyList<MonthSection>(), all) { value = withContext(Dispatchers.Default) { GalleryTimeline.group(all) } }
    val root = remember(all) { GalleryFolders.root(all) }
    val userId by remember(profile.id) { gallery.link(profile.id).map { it?.userId } }.collectAsStateWithLifecycle(initialValue = null)
    val retry = { scope.launch { gallery.sync(profile.id, force = true) }; Unit }

    fun view(items: List<GalleryItem>, item: GalleryItem, slideshow: Boolean = false) {
        val id = userId ?: return
        onOpen(GalleryViewerRequest(auth, id, items, items.indexOf(item).coerceAtLeast(0), slideMs = prefs.slideSeconds * 1000L, startSlideshow = slideshow))
    }

    // Jamais synchronisée : rien à montrer tant que la première lecture n'a pas abouti.
    if (syncedAt == null) {
        when {
            state.error != null -> StateMessage("Galerie injoignable", state.error, action = "Réessayer", onAction = retry)
            else -> StateMessage("Première synchronisation…", "Nimbe fait la liste de tes photos et vidéos. Ça peut prendre une minute.")
        }
        return
    }

    // Un album, un souvenir ou un dossier ouvert remplace la rubrique ; Retour (géré par le shell) remonte d'un niveau.
    nav.opened?.let { level ->
        SideEffect { nav.backToTop = { false } }
        OpenedScreen(gallery, profile.id, auth, level, all, root, nav, onView = ::view)
        return
    }

    val sections = GallerySection.entries.filter { it != GallerySection.Videos || prefs.showVideos }
    // Vidéos masquées dans les réglages alors qu'on était sur cette rubrique : retour à la chronologie.
    val section = nav.section.takeIf { it in sections } ?: GallerySection.Timeline
    val selectedPill = remember { FocusRequester() }
    // Retour dans une liste qui a défilé : d'abord remonter en haut, focus sur la rubrique ; un second Retour quitte.
    SideEffect {
        nav.backToTop = backToTop@{
            val scrolled = when (section) {
                GallerySection.ForYou -> nav.forYou.firstVisibleItemIndex > 0
                GallerySection.Timeline -> nav.timeline.firstVisibleItemIndex > 0
                GallerySection.Videos -> nav.videos.firstVisibleItemIndex > 0
                GallerySection.Albums -> nav.albums.firstVisibleItemIndex > 0
                GallerySection.Shared -> nav.shared.firstVisibleItemIndex > 0
            }
            if (!scrolled) return@backToTop false
            runCatching { selectedPill.requestFocus() }
            scope.launch {
                when (section) {
                    GallerySection.ForYou -> nav.forYou.scrollToItem(0)
                    GallerySection.Timeline -> nav.timeline.scrollToItem(0)
                    GallerySection.Videos -> nav.videos.scrollToItem(0)
                    GallerySection.Albums -> nav.albums.scrollToItem(0)
                    GallerySection.Shared -> nav.shared.scrollToItem(0)
                }
            }
            true
        }
    }

    Column(Modifier.fillMaxSize()) {
        GalleryHeader(profile, all.count { !it.isVideo }, all.count { it.isVideo }, syncedAt, state.syncing, state.error, sections, section, selectedPill, nav, retry)
        when (section) {
            GallerySection.ForYou -> ForYouPage(gallery, profile.id, auth, all, root, prefs, nav, onView = { list, item -> view(list, item) })
            GallerySection.Timeline -> TimelinePage(auth, timeline, nav.timeline, nav, empty = "Aucune photo pour l'instant", onView = { list, item -> view(list, item) })
            GallerySection.Videos -> {
                val videos = remember(timeline) {
                    timeline.mapNotNull { month -> month.items.filter { it.isVideo }.takeIf { it.isNotEmpty() }?.let { month.copy(items = it) } }
                }
                TimelinePage(auth, videos, nav.videos, nav, empty = "Aucune vidéo dans ta galerie", onView = { list, item -> view(list, item) })
            }
            GallerySection.Albums -> AlbumsPage(gallery, profile.id, auth, all, root, nav, shared = false)
            GallerySection.Shared -> AlbumsPage(gallery, profile.id, auth, all, root, nav, shared = true)
        }
    }
}

/**
 * Titre et nombre de photos à gauche, rubriques à droite. Les rubriques changent dès qu'on les survole,
 * comme les onglets du haut ; en descendant depuis les onglets, le focus arrive sur la rubrique affichée.
 */
@Composable
private fun GalleryHeader(
    profile: Profile,
    photos: Int,
    videos: Int,
    syncedAt: Long?,
    syncing: Boolean,
    error: String?,
    sections: List<GallerySection>,
    selected: GallerySection,
    selectedPill: FocusRequester,
    nav: GalleryNav,
    onSync: () -> Unit,
) {
    Column(Modifier.padding(start = 56.dp, end = 56.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Galerie de ${profile.name}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(GalleryText.summary(photos, videos, syncedAt, System.currentTimeMillis()), style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
                    if (syncing) Spinner(Focus)
                }
            }
            // Un seul groupe de focus : en y entrant, on arrive sur la rubrique affichée, pas sur la plus proche.
            Row(Modifier.focusRestorer(selectedPill).focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                sections.forEach { section ->
                    FilterPill(
                        section.label,
                        selected = section == selected,
                        onClick = { nav.section = section },
                        onFocus = { nav.section = section },
                        modifier = if (section == selected) Modifier.focusRequester(selectedPill) else Modifier,
                    )
                }
                Spacer(Modifier.width(6.dp))
                PillButton("Synchroniser", onClick = onSync)
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Alerte) }
    }
}

/**
 * « Pour toi » : souvenirs, puis une carte par année (pour sauter loin d'un coup), les albums ou les dossiers,
 * et les dernières vidéos.
 */
@Composable
private fun ForYouPage(
    gallery: NextcloudGallery,
    profileId: String,
    auth: NextcloudAuth,
    all: List<GalleryItem>,
    root: String,
    prefs: GalleryPrefs,
    nav: GalleryNav,
    onView: (List<GalleryItem>, GalleryItem) -> Unit,
) {
    val today = remember { LocalDate.now() }
    // « Il y a N ans » peut être coupé dans les réglages ; ce mois-ci et les ajouts récents restent.
    val memories = remember(all, prefs.memories) {
        (if (prefs.memories) GalleryMemories.onThisDay(all, today) else emptyList()) +
            listOfNotNull(GalleryMemories.thisMonth(all, today), GalleryMemories.recentlyAdded(all))
    }
    val years = remember(all) { GalleryFolders.byYear(all) }
    val folders = remember(all, root) { GalleryFolders.children(all, root) }
    val videos = remember(all) { GalleryFolders.recentVideos(all) }
    val albums = rememberAlbums(gallery, profileId, shared = false)
    if (all.isEmpty()) {
        StateMessage("Aucune photo pour l'instant", "Les photos et vidéos de ton compte Nextcloud apparaîtront ici.")
        return
    }
    val nextcloudAlbums = albums.value.orEmpty()
    LazyColumn(
        state = nav.forYou,
        contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (memories.isNotEmpty()) {
            item(key = "memories") {
                CardRow(memories, key = { it.kicker + it.title }) { memory ->
                    CollectionCard(
                        auth,
                        cover = memory.cover,
                        kicker = memory.kicker,
                        title = memory.title,
                        detail = GalleryText.monthCounts(memory.photos, memory.videos),
                        width = MEMORY_WIDTH,
                        onClick = { nav.open(GalleryOpen.Local(memory)) },
                        restore = nav.restoreKey == GalleryNav.key(GalleryOpen.Local(memory)),
                        onRestored = { nav.restoreKey = null },
                    )
                }
            }
        }
        if (years.size > 1) {
            item(key = "years-title") { SectionTitle("Par année", GalleryText.count(years.size, "année", "années")) }
            item(key = "years") {
                CardRow(years, key = { it.title }) { year ->
                    CollectionCard(
                        auth,
                        cover = year.cover,
                        kicker = "",
                        title = year.title,
                        detail = GalleryText.monthCounts(year.photos, year.videos),
                        width = SMALL_WIDTH,
                        onClick = { nav.open(GalleryOpen.Local(year)) },
                        restore = nav.restoreKey == GalleryNav.key(GalleryOpen.Local(year)),
                        onRestored = { nav.restoreKey = null },
                    )
                }
            }
        }
        // Les albums Nextcloud s'ils existent, puis les dossiers : on entre dans un dossier comme sur l'ordinateur.
        if (nextcloudAlbums.isNotEmpty()) {
            item(key = "albums-title") { SectionTitle("Albums", GalleryText.count(nextcloudAlbums.size, "album", "albums")) }
            item(key = "albums") {
                CardRow(nextcloudAlbums.take(ROW_LIMIT), key = { it.davPath }) { album ->
                    AlbumCard(auth, album, width = SMALL_WIDTH, nav = nav, onClick = { nav.open(GalleryOpen.Album(album, shared = false)) })
                }
            }
        }
        if (folders.isNotEmpty()) {
            item(key = "folders-title") { SectionTitle("Dossiers", GalleryText.count(folders.size, "dossier", "dossiers")) }
            item(key = "folders") {
                CardRow(folders.take(ROW_LIMIT), key = { it.path }) { folder ->
                    FolderCard(auth, folder, width = SMALL_WIDTH, nav = nav, onClick = { nav.open(GalleryOpen.Folder(folder.path)) })
                }
            }
        }
        if (videos != null && prefs.showVideos) {
            item(key = "videos-title") { SectionTitle("Vidéos récentes", GalleryText.count(videos.items.size, "vidéo", "vidéos")) }
            item(key = "videos") {
                CardRow(videos.items, key = { it.fileId }) { video ->
                    PhotoCard(auth, video, restore = false, onRestored = {}, onClick = { onView(videos.items, video) }, modifier = Modifier.width(SMALL_WIDTH.dp))
                }
            }
        }
    }
}

/** Rangée horizontale de cartes, aux marges de la page. */
@Composable
private fun <T> CardRow(items: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) {
    LazyRow(
        modifier = Modifier.focusRestorer(),
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(items, key = key) { content(it) }
    }
}

/** Albums Nextcloud (ou partagés) : d'abord ceux gardés sur la TV, puis la réponse du serveur. */
private data class AlbumsLoad(val value: List<NextcloudAlbum>?, val error: String?, val loading: Boolean)

@Composable
private fun rememberAlbums(gallery: NextcloudGallery, profileId: String, shared: Boolean, attempt: Int = 0): AlbumsLoad {
    val load by produceState(AlbumsLoad(null, null, true), profileId, shared, attempt) {
        val cached = gallery.cachedAlbums(profileId, shared)
        value = AlbumsLoad(cached, null, true)
        value = try {
            AlbumsLoad(gallery.refreshAlbums(profileId, shared), null, false)
        } catch (e: NextcloudException) {
            AlbumsLoad(cached, e.message, false)
        }
    }
    return load
}

/**
 * Albums : les albums Nextcloud s'il y en a, puis les dossiers de premier niveau (on y entre ensuite niveau par niveau).
 * Partagées : les albums que d'autres t'ont partagés.
 */
@Composable
private fun AlbumsPage(gallery: NextcloudGallery, profileId: String, auth: NextcloudAuth, all: List<GalleryItem>, root: String, nav: GalleryNav, shared: Boolean) {
    var attempt by remember { mutableIntStateOf(0) }
    val load = rememberAlbums(gallery, profileId, shared, attempt)
    val albums = load.value
    val folders = remember(all, root, shared) { if (shared) emptyList() else GalleryFolders.children(all, root, byName = true) }
    when {
        albums == null && load.loading -> StateMessage("Chargement…", null)
        albums == null && folders.isEmpty() -> StateMessage("Nextcloud injoignable", load.error, action = "Réessayer", onAction = { attempt++ })
        albums.orEmpty().isEmpty() && shared -> StateMessage(
            "Aucun album partagé avec toi",
            "Quand quelqu'un te partagera un album dans l'appli Photos de Nextcloud, il apparaîtra ici automatiquement.",
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(ALBUM_COLUMNS),
            state = if (shared) nav.shared else nav.albums,
            modifier = Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 8.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            load.error?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("$message · albums gardés sur la TV", style = MaterialTheme.typography.bodyMedium, color = Alerte)
                }
            }
            val list = albums.orEmpty()
            if (list.isNotEmpty()) {
                if (!shared) item(span = { GridItemSpan(maxLineSpan) }) { GridTitle("Albums", GalleryText.count(list.size, "album", "albums")) }
                items(list, key = { it.davPath }) { album ->
                    AlbumCard(auth, album, width = null, nav = nav, onClick = { nav.open(GalleryOpen.Album(album, shared)) })
                }
            }
            if (folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GridTitle(
                        "Dossiers",
                        if (list.isEmpty()) "Pas encore d'album dans l'appli Photos de Nextcloud : voici tes dossiers" else GalleryText.count(folders.size, "dossier", "dossiers"),
                    )
                }
                items(folders, key = { it.path }) { folder ->
                    FolderCard(auth, folder, width = null, nav = nav, onClick = { nav.open(GalleryOpen.Folder(folder.path)) })
                }
            }
        }
    }
}

/**
 * Un souvenir, une année, un album ou un dossier ouvert : son titre, un diaporama de tout son contenu,
 * puis, pour un dossier, ses sous-dossiers avant ses photos.
 */
@Composable
private fun OpenedScreen(
    gallery: NextcloudGallery,
    profileId: String,
    auth: NextcloudAuth,
    level: OpenedLevel,
    all: List<GalleryItem>,
    root: String,
    nav: GalleryNav,
    onView: (List<GalleryItem>, GalleryItem, Boolean) -> Unit,
) {
    val target = level.target
    var attempt by remember { mutableIntStateOf(0) }
    // Un album vient du serveur (gardé ensuite sur la TV) ; le reste est déjà dans l'index.
    val load by produceState<Pair<List<GalleryItem>?, String?>>(Pair(null, null), target, attempt, all) {
        when (target) {
            is GalleryOpen.Local -> value = target.collection.items to null
            is GalleryOpen.Folder -> value = GalleryFolders.direct(all, target.path).sortedByDescending { it.takenAt } to null
            is GalleryOpen.Album -> {
                val cached = gallery.cachedAlbumItems(profileId, target.album)
                value = cached to null
                value = try {
                    gallery.refreshAlbumItems(profileId, target.album) to null
                } catch (e: NextcloudException) {
                    cached to e.message
                }
            }
        }
    }
    val (items, error) = load
    val subfolders = remember(target, all) { if (target is GalleryOpen.Folder) GalleryFolders.children(all, target.path, byName = true) else emptyList() }
    // Le diaporama d'un dossier passe par toutes ses photos, sous-dossiers compris.
    val everything = remember(target, all, items) {
        if (target is GalleryOpen.Folder) GalleryFolders.inside(all, target.path).sortedByDescending { it.takenAt } else items.orEmpty()
    }
    val sections = remember(items) { items?.let { GalleryTimeline.group(it) }.orEmpty() }
    val (kicker, title) = when (target) {
        is GalleryOpen.Local -> target.collection.kicker to target.collection.title
        is GalleryOpen.Album -> (target.album.owner?.let { "PARTAGÉ PAR ${it.uppercase()}" } ?: "ALBUM") to target.album.name
        is GalleryOpen.Folder -> GalleryText.breadcrumb(root, target.path) to target.path.substringAfterLast('/').ifEmpty { "Racine du compte" }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 56.dp, end = 56.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (kicker.isNotEmpty()) Text(kicker, style = MaterialTheme.typography.labelMedium, color = Focus, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Le focus arrive sur « Retour » à l'ouverture ; Bas mène au contenu.
            val back = remember { FocusRequester() }
            LaunchedEffect(target) { runCatching { back.requestFocus() } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            GalleryText.monthCounts(everything.count { !it.isVideo }, everything.count { it.isVideo }).ifEmpty { null },
                            if (subfolders.isNotEmpty()) GalleryText.count(subfolders.size, "sous-dossier", "sous-dossiers") else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = LuneAttenuee,
                    )
                }
                PillButton("‹ Retour", onClick = { nav.close() }, modifier = Modifier.focusRequester(back))
                val photos = everything.filter { !it.isVideo }
                if (photos.isNotEmpty()) PillButton("Diaporama", icon = NimbeIcons.Play, onClick = { onView(photos, photos.first(), true) })
            }
            error?.let { Text("$it · contenu gardé sur la TV", style = MaterialTheme.typography.labelMedium, color = Alerte) }
        }
        when {
            items == null && error == null -> StateMessage("Chargement…", null)
            items == null -> StateMessage("Nextcloud injoignable", error, action = "Réessayer", onAction = { attempt++ })
            else -> PhotoGrid(
                auth,
                sections,
                level.grid,
                nav,
                empty = "Ce dossier est vide",
                onView = { list, item -> onView(list, item, false) },
                folders = subfolders,
                onFolder = { nav.open(GalleryOpen.Folder(it.path)) },
            )
        }
    }
}

/**
 * Chronologie (ou vidéos) : une barre des années pour sauter loin d'un coup, puis la grille mois par mois.
 * L'année en cours de lecture est mise en avant pendant le défilement.
 */
@Composable
private fun TimelinePage(
    auth: NextcloudAuth,
    sections: List<MonthSection>,
    state: LazyGridState,
    nav: GalleryNav,
    empty: String,
    onView: (List<GalleryItem>, GalleryItem) -> Unit,
) {
    val years = remember(sections) { GalleryFolders.yearStarts(sections) }
    // Année du premier mois visible, d'après la position de la grille.
    val starts = remember(sections) {
        var position = 0
        sections.map { month -> (position to month.month.year).also { position += 1 + month.items.size } }
    }
    // Le premier élément entièrement visible : une rangée à moitié cachée en haut ne compte pas.
    val currentYear by remember(starts) {
        derivedStateOf {
            val shown = state.layoutInfo.visibleItemsInfo.firstOrNull { it.offset.y >= 0 }?.index ?: state.firstVisibleItemIndex
            starts.lastOrNull { it.first <= shown }?.second
        }
    }
    // La barre suit la lecture : l'année affichée reste visible, sans voler le focus.
    val bar = remember { androidx.compose.foundation.lazy.LazyListState() }
    LaunchedEffect(currentYear, years) {
        val index = years.indexOfFirst { it.first == currentYear }
        if (index >= 0 && bar.layoutInfo.visibleItemsInfo.none { it.index == index && it.offset >= 0 }) bar.animateScrollToItem(index)
    }
    Column(Modifier.fillMaxSize()) {
        if (years.size > 1) {
            LazyRow(
                state = bar,
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(years, key = { it.first }) { (year, firstMonth) ->
                    FilterPill(
                        "$year",
                        selected = year == currentYear,
                        // La grille défile jusqu'au premier mois de l'année, et sa première photo prend le focus.
                        onClick = { nav.restoreFileId = firstMonth.items.first().fileId },
                    )
                }
            }
        }
        PhotoGrid(auth, sections, state, nav, empty, onView)
    }
}

/**
 * Galerie d'un profil Enfant : seulement l'album ou le dossier choisi par un parent, lu avec la galerie
 * Nextcloud de ce parent. Pas de rubriques, pas de réglages : une grille et le plein écran.
 */
@Composable
private fun ChildGalleryContent(gallery: NextcloudGallery, child: Profile, nav: GalleryNav, onOpen: (GalleryViewerRequest) -> Unit) {
    val choice = child.gallery
    if (choice == null) {
        StateMessage("Pas encore de photos", "Un parent peut choisir un album pour toi dans les réglages.")
        return
    }
    val source = choice.sourceProfileId
    val link by remember(source) { gallery.link(source) }.collectAsStateWithLifecycle(initialValue = null)
    val auth by produceState<NextcloudAuth?>(null, source, link) { value = gallery.auth(source) }
    var attempt by remember { mutableIntStateOf(0) }
    val load by produceState<Pair<List<GalleryItem>?, String?>>(Pair(null, null), choice, attempt) {
        val album = choice.albumPath?.let { NextcloudAlbum(choice.name, it, 0, null) }
        val cached = if (album != null) gallery.cachedAlbumItems(source, album) else gallery.cachedFolderItems(source, choice.folder.orEmpty())
        value = cached to null
        value = try {
            (if (album != null) gallery.refreshAlbumItems(source, album) else gallery.refreshFolderItems(source, choice.folder.orEmpty())) to null
        } catch (e: NextcloudException) {
            cached to e.message
        }
    }
    val (items, error) = load
    val sections = remember(items) { items?.let { GalleryTimeline.group(it) }.orEmpty() }
    val current = auth
    val userId = link?.userId
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 56.dp, end = 56.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("LES PHOTOS DE LA FAMILLE", style = MaterialTheme.typography.labelMedium, color = Focus)
            Text(choice.name, style = MaterialTheme.typography.headlineSmall)
            items?.let { list ->
                Text(GalleryText.monthCounts(list.count { !it.isVideo }, list.count { it.isVideo }), style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
            }
        }
        when {
            current == null || userId == null -> StateMessage(
                "Les photos ne sont pas disponibles",
                "Demande à un parent de vérifier la galerie dans les réglages.",
            )
            items == null && error == null -> StateMessage("Chargement…", null)
            items == null -> StateMessage("Les photos ne répondent pas", error, action = "Réessayer", onAction = { attempt++ })
            else -> PhotoGrid(current, sections, nav.timeline, nav, empty = "Cet album est vide pour l'instant", onView = { list, item ->
                onOpen(GalleryViewerRequest(current, userId, list, list.indexOf(item)))
            })
        }
    }
}

/**
 * Photos mois par mois (et, pour un dossier, ses sous-dossiers d'abord) ; OK ouvre le plein écran sur la suite
 * affichée. En revenant du plein écran ou en choisissant une année, la grille défile jusqu'à la photo voulue.
 */
@Composable
private fun PhotoGrid(
    auth: NextcloudAuth,
    sections: List<MonthSection>,
    state: LazyGridState,
    nav: GalleryNav,
    empty: String,
    onView: (List<GalleryItem>, GalleryItem) -> Unit,
    folders: List<GalleryFolder> = emptyList(),
    onFolder: (GalleryFolder) -> Unit = {},
) {
    if (sections.isEmpty() && folders.isEmpty()) {
        StateMessage(empty, null)
        return
    }
    val flat = remember(sections) { sections.flatMap { it.items } }
    // Éléments placés avant les mois : titre et cartes des sous-dossiers, puis titre « Photos ».
    val leading = if (folders.isEmpty()) 0 else 1 + folders.size + if (sections.isNotEmpty()) 1 else 0
    LaunchedEffect(nav.restoreFileId, sections) {
        val target = nav.restoreFileId ?: return@LaunchedEffect
        val position = GalleryTimeline.gridIndex(sections, target)
        // gridIndex compte un titre en tête de grille (position 0) : remplacé ici par les éléments de tête.
        if (position == null) nav.restoreFileId = null else state.scrollToItem(position - 1 + leading)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = state,
        modifier = Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 6.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (folders.isNotEmpty()) {
            item(key = "folders-title", span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
                GridTitle("Dossiers", GalleryText.count(folders.size, "dossier", "dossiers"))
            }
            items(folders, key = { "f:" + it.path }, span = { GridItemSpan(FOLDER_SPAN) }, contentType = { "folder" }) { folder ->
                FolderCard(auth, folder, width = null, nav = nav, onClick = { onFolder(folder) })
            }
            if (sections.isNotEmpty()) {
                item(key = "photos-title", span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
                    GridTitle("Photos de ce dossier", GalleryText.monthCounts(flat.count { !it.isVideo }, flat.count { it.isVideo }))
                }
            }
        }
        monthSections(auth, sections, nav) { onView(flat, it) }
    }
}

private fun LazyGridScope.monthSections(auth: NextcloudAuth, sections: List<MonthSection>, nav: GalleryNav, onClick: (GalleryItem) -> Unit) {
    sections.forEach { section ->
        item(key = "m:${section.month}:${section.yearOnly}", span = { GridItemSpan(maxLineSpan) }, contentType = "month") {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                Text(GalleryText.sectionTitle(section), style = MaterialTheme.typography.titleLarge)
                Text(GalleryText.monthCounts(section.photos, section.videos), style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
            }
        }
        items(section.items, key = { it.fileId }, contentType = { "photo" }) { item ->
            PhotoCard(auth, item, restore = item.fileId == nav.restoreFileId, onRestored = { nav.restoreFileId = null }, onClick = { onClick(item) })
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.padding(start = 56.dp, end = 56.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
    }
}

@Composable
private fun GridTitle(title: String, detail: String) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
    }
}

/** Carte d'album Nextcloud : la dernière photo ajoutée en couverture. */
@Composable
private fun AlbumCard(auth: NextcloudAuth, album: NextcloudAlbum, width: Int?, nav: GalleryNav, onClick: () -> Unit) {
    val cover = album.coverFileId?.let { GalleryItem("", it, album.name, "image/jpeg", 0, 0, 0, 0, hasPreview = true) }
    CollectionCard(
        auth,
        cover = cover,
        kicker = album.owner?.let { "PARTAGÉ PAR ${it.uppercase()}" } ?: "ALBUM",
        title = album.name,
        detail = GalleryText.count(album.count, "élément", "éléments"),
        width = width,
        onClick = onClick,
        restore = nav.restoreKey == GalleryNav.key(GalleryOpen.Album(album, album.owner != null)),
        onRestored = { nav.restoreKey = null },
    )
}

/** Carte de dossier : la photo la plus récente en couverture, et tout ce qu'il contient, sous-dossiers compris. */
@Composable
private fun FolderCard(auth: NextcloudAuth, folder: GalleryFolder, width: Int?, nav: GalleryNav, onClick: () -> Unit) {
    CollectionCard(
        auth,
        cover = folder.cover,
        kicker = "DOSSIER",
        title = folder.name,
        detail = GalleryText.monthCounts(folder.photos, folder.videos),
        width = width,
        onClick = onClick,
        restore = nav.restoreKey == GalleryNav.key(GalleryOpen.Folder(folder.path)),
        onRestored = { nav.restoreKey = null },
    )
}

/** Couverture avec, par-dessus, la petite ligne en capitales, le titre et le nombre de photos. */
@Composable
internal fun CollectionCard(
    auth: NextcloudAuth,
    cover: GalleryItem?,
    kicker: String,
    title: String,
    detail: String,
    width: Int?,
    onClick: () -> Unit,
    /** Revenue de ce qu'elle ouvrait : elle reprend le focus. */
    restore: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (restore) {
        LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
            onRestored()
        }
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(if (width != null) Modifier.width(width.dp) else Modifier.fillMaxWidth())
            .focusRequester(requester)
            .aspectRatio(16f / 9f)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusRing(focused, NimbeFocus.CardRadius),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = PanneauClair, focusedContainerColor = PanneauClair),
        scale = ClickableSurfaceDefaults.scale(focusedScale = NimbeFocus.SCALE),
    ) {
        Box(Modifier.fillMaxSize()) {
            cover?.let {
                NextcloudPreview(
                    auth,
                    it,
                    PREVIEW_WIDTH,
                    PREVIEW_HEIGHT,
                    contentScale = ContentScale.Crop,
                    showNameWithoutPreview = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Nuit.copy(alpha = 0.85f))))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (kicker.isNotEmpty()) Text(kicker, style = MaterialTheme.typography.labelSmall, color = Focus, maxLines = 1)
                Text(title, style = MaterialTheme.typography.titleMedium, color = Lune, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = LuneAttenuee, maxLines = 1)
            }
        }
    }
}

/**
 * Case de la grille, au format 4:3 des appareils photo (moins recadrée qu'en 16:9). Au focus, la date
 * s'affiche en bas ; une vidéo porte toujours son ▶. Sans aperçu (encore en préparation sur le serveur),
 * « Vidéo » ou la date plutôt qu'un nom de fichier.
 */
@Composable
private fun PhotoCard(
    auth: NextcloudAuth,
    item: GalleryItem,
    restore: Boolean,
    onRestored: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (restore) {
        LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
            onRestored()
        }
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRequester(requester)
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = PanneauClair, focusedContainerColor = PanneauClair),
        scale = ClickableSurfaceDefaults.scale(focusedScale = NimbeFocus.SCALE),
    ) {
        Box(Modifier.fillMaxSize()) {
            NextcloudPreview(
                auth,
                item,
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT_GRID,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fallback = {
                    Text(
                        if (item.isVideo) "Vidéo" else "Aperçu en préparation",
                        style = MaterialTheme.typography.labelMedium,
                        color = LuneAttenuee,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp),
                    )
                },
            )
            if (focused) {
                Text(
                    GalleryText.shortDate(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = Lune,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Nuit.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (item.isVideo) {
                Text(
                    "▶",
                    style = MaterialTheme.typography.labelMedium,
                    color = Lune,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Nuit.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

private const val COLUMNS = 6
private const val ALBUM_COLUMNS = 4

/** Un sous-dossier occupe deux cases : trois par ligne au-dessus des photos. */
private const val FOLDER_SPAN = 2
private const val MEMORY_WIDTH = 400
private const val SMALL_WIDTH = 260

/** Cartes montrées dans une rangée de « Pour toi » ; les autres sont dans la rubrique Albums. */
private const val ROW_LIMIT = 15

/** Taille des aperçus demandés, en pixels. Les cartes en 16:9, les cases de la grille en 4:3. */
internal const val PREVIEW_WIDTH = 480
private const val PREVIEW_HEIGHT = 270
internal const val PREVIEW_HEIGHT_GRID = 360
