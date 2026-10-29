package io.github.touya0.nimbe.feature.gallery

import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.nextcloud.GalleryItem
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Onglet Galerie : rubriques Pour toi, Chronologie, Albums, Vidéos et Partagées, comme dans la maquette.
 * Tout ce qui vient de l'index local ou du cache s'affiche tout de suite, et même serveur coupé.
 * [nav] : gardé par l'appelant, pour retrouver rubrique, album ouvert et position au retour du plein écran.
 */
@Composable
fun GalleryScreen(
    gallery: NextcloudGallery,
    profile: Profile?,
    onOpenSettings: () -> Unit,
    nav: GalleryNav,
    onOpen: (GalleryViewerRequest) -> Unit,
) {
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
    val counts by remember(profile.id) { gallery.counts(profile.id) }.collectAsStateWithLifecycle(initialValue = null)
    val syncedAt by remember(profile.id) { gallery.syncedAt(profile.id) }.collectAsStateWithLifecycle(initialValue = null)
    // Tout l'index, rangé hors du fil de l'interface : quelques milliers de photos, à chaque synchronisation.
    val all by remember(profile.id) { gallery.all(profile.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val timeline by remember(profile.id) {
        gallery.all(profile.id).map { GalleryTimeline.group(it) }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val userId by remember(profile.id) { gallery.link(profile.id).map { it?.userId } }.collectAsStateWithLifecycle(initialValue = null)
    val retry = { scope.launch { gallery.sync(profile.id, force = true) }; Unit }

    fun view(items: List<GalleryItem>, item: GalleryItem) {
        val id = userId ?: return
        onOpen(GalleryViewerRequest(auth, id, items, items.indexOf(item)))
    }

    // Jamais synchronisée : rien à montrer tant que la première lecture n'a pas abouti.
    if (syncedAt == null) {
        when {
            state.error != null -> StateMessage("Galerie injoignable", state.error, action = "Réessayer", onAction = retry)
            else -> StateMessage("Première synchronisation…", "Nimbe fait la liste de tes photos et vidéos. Ça peut prendre une minute.")
        }
        return
    }

    // Un album ou un souvenir ouvert remplace la rubrique ; Retour (géré par le shell) y revient.
    nav.open?.let { target ->
        OpenedScreen(gallery, profile.id, auth, target, nav, onView = ::view)
        return
    }

    Column(Modifier.fillMaxSize()) {
        GalleryHeader(profile, counts?.photos ?: 0, counts?.videos ?: 0, syncedAt, state.syncing, state.error, nav, retry)
        when (nav.section) {
            GallerySection.ForYou -> ForYouPage(gallery, profile.id, auth, all, nav)
            GallerySection.Timeline -> PhotoGrid(auth, timeline, nav.timeline, nav, empty = "Aucune photo pour l'instant", onView = ::view)
            GallerySection.Videos -> {
                val videos = remember(timeline) {
                    timeline.mapNotNull { section -> section.items.filter { it.isVideo }.takeIf { it.isNotEmpty() }?.let { MonthSection(section.month, it) } }
                }
                PhotoGrid(auth, videos, nav.videos, nav, empty = "Aucune vidéo dans ta galerie", onView = ::view)
            }
            GallerySection.Albums -> AlbumsPage(gallery, profile.id, auth, all, nav, shared = false)
            GallerySection.Shared -> AlbumsPage(gallery, profile.id, auth, all, nav, shared = true)
        }
    }
}

@Composable
private fun GalleryHeader(
    profile: Profile,
    photos: Int,
    videos: Int,
    syncedAt: Long?,
    syncing: Boolean,
    error: String?,
    nav: GalleryNav,
    onSync: () -> Unit,
) {
    Column(Modifier.padding(start = 56.dp, end = 56.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Galerie de ${profile.name}",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            GallerySection.entries.forEach { section ->
                FilterPill(section.label, selected = section == nav.section, onClick = { nav.section = section })
            }
            Spacer(Modifier.width(6.dp))
            PillButton("Synchroniser", onClick = onSync)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(GalleryText.summary(photos, videos, syncedAt, System.currentTimeMillis()), style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
            if (syncing) Spinner(Focus)
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Alerte) }
    }
}

/** « Pour toi » : souvenirs des années passées, ce mois-ci, ajouts récents, puis les albums (ou les dossiers). */
@Composable
private fun ForYouPage(gallery: NextcloudGallery, profileId: String, auth: NextcloudAuth, all: List<GalleryItem>, nav: GalleryNav) {
    val today = remember { LocalDate.now() }
    val memories = remember(all) {
        GalleryMemories.onThisDay(all, today) + listOfNotNull(GalleryMemories.thisMonth(all, today), GalleryMemories.recentlyAdded(all))
    }
    val albums = rememberAlbums(gallery, profileId, shared = false)
    val folders = remember(all) { GalleryMemories.folders(all) }
    if (all.isEmpty()) {
        StateMessage("Aucune photo pour l'instant", "Les photos et vidéos de ton compte Nextcloud apparaîtront ici.")
        return
    }
    val nextcloudAlbums = albums.value.orEmpty()
    LazyColumn(
        state = nav.forYou,
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "memories") {
            LazyRow(contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(memories, key = { it.kicker + it.title }) { memory ->
                    CollectionCard(
                        auth,
                        cover = memory.cover,
                        kicker = memory.kicker,
                        title = memory.title,
                        detail = GalleryText.monthCounts(memory.photos, memory.videos),
                        width = MEMORY_WIDTH,
                        onClick = { nav.open(GalleryOpen.Local(memory)) },
                    )
                }
            }
        }
        // Les albums Nextcloud s'ils existent, sinon les dossiers : la rangée n'est jamais vide.
        item(key = "albums-title") {
            if (nextcloudAlbums.isNotEmpty()) {
                SectionTitle("Albums", GalleryText.count(nextcloudAlbums.size, "album", "albums"))
            } else {
                SectionTitle("Dossiers", GalleryText.count(folders.size, "dossier", "dossiers"))
            }
        }
        item(key = "albums") {
            LazyRow(contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (nextcloudAlbums.isNotEmpty()) {
                    items(nextcloudAlbums.take(ROW_ALBUMS), key = { it.davPath }) { album ->
                        AlbumCard(auth, album, width = ALBUM_WIDTH, onClick = { nav.open(GalleryOpen.Album(album, shared = false)) })
                    }
                } else {
                    items(folders.take(ROW_ALBUMS), key = { it.kicker + "/" + it.title }) { folder ->
                        CollectionCard(
                            auth,
                            cover = folder.cover,
                            kicker = folder.kicker,
                            title = folder.title,
                            detail = GalleryText.monthCounts(folder.photos, folder.videos),
                            width = ALBUM_WIDTH,
                            onClick = { nav.open(GalleryOpen.Local(folder)) },
                        )
                    }
                }
            }
        }
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

/** Albums ou Partagées : une grille de couvertures. Sans album Nextcloud, la rubrique Albums montre les dossiers. */
@Composable
private fun AlbumsPage(gallery: NextcloudGallery, profileId: String, auth: NextcloudAuth, all: List<GalleryItem>, nav: GalleryNav, shared: Boolean) {
    var attempt by remember { mutableIntStateOf(0) }
    val load = rememberAlbums(gallery, profileId, shared, attempt)
    val albums = load.value
    val folders = remember(all, shared) { if (shared) emptyList() else GalleryMemories.folders(all) }
    when {
        albums == null && load.loading -> StateMessage("Chargement…", null)
        albums == null -> StateMessage("Nextcloud injoignable", load.error, action = "Réessayer", onAction = { attempt++ })
        albums.isEmpty() && shared -> StateMessage(
            "Aucun album partagé avec toi",
            "Quand quelqu'un te partagera un album dans l'appli Photos de Nextcloud, il apparaîtra ici automatiquement.",
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(ALBUM_COLUMNS),
            state = if (shared) nav.shared else nav.albums,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 12.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            load.error?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("$message · albums gardés sur la TV", style = MaterialTheme.typography.bodyMedium, color = Alerte)
                }
            }
            if (albums.isNotEmpty()) {
                items(albums, key = { it.davPath }) { album ->
                    AlbumCard(auth, album, width = null, onClick = { nav.open(GalleryOpen.Album(album, shared)) })
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Pas encore d'album dans l'appli Photos de Nextcloud : voici tes dossiers. Les albums que tu créeras apparaîtront ici.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LuneAttenuee,
                    )
                }
                items(folders, key = { it.kicker + "/" + it.title }) { folder ->
                    CollectionCard(
                        auth,
                        cover = folder.cover,
                        kicker = folder.kicker,
                        title = folder.title,
                        detail = GalleryText.monthCounts(folder.photos, folder.videos),
                        width = null,
                        onClick = { nav.open(GalleryOpen.Local(folder)) },
                    )
                }
            }
        }
    }
}

/** Un souvenir, un dossier ou un album ouvert : son titre, puis ses photos mois par mois. */
@Composable
private fun OpenedScreen(
    gallery: NextcloudGallery,
    profileId: String,
    auth: NextcloudAuth,
    target: GalleryOpen,
    nav: GalleryNav,
    onView: (List<GalleryItem>, GalleryItem) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    // Un album vient du serveur (gardé ensuite sur la TV) ; un souvenir ou un dossier est déjà là.
    val load by produceState<Pair<List<GalleryItem>?, String?>>(Pair(null, null), target, attempt) {
        when (target) {
            is GalleryOpen.Local -> value = target.collection.items to null
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
    val sections = remember(items) { items?.let { GalleryTimeline.group(it) }.orEmpty() }
    val (kicker, title) = when (target) {
        is GalleryOpen.Local -> target.collection.kicker to target.collection.title
        is GalleryOpen.Album -> (target.album.owner?.let { "PARTAGÉ PAR ${it.uppercase()}" } ?: "ALBUM") to target.album.name
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 56.dp, end = 56.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (kicker.isNotEmpty()) Text(kicker, style = MaterialTheme.typography.labelMedium, color = Focus)
            // Le focus arrive sur « Retour » à l'ouverture ; Bas mène aux photos.
            val back = remember { FocusRequester() }
            LaunchedEffect(target) { runCatching { back.requestFocus() } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                PillButton("‹ Retour", onClick = { nav.close() }, modifier = Modifier.focusRequester(back))
            }
            items?.let { list ->
                Text(GalleryText.monthCounts(list.count { !it.isVideo }, list.count { it.isVideo }), style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
            }
            error?.let { Text("$it · contenu gardé sur la TV", style = MaterialTheme.typography.bodyMedium, color = Alerte) }
        }
        when {
            items == null && error == null -> StateMessage("Chargement…", null)
            items == null -> StateMessage("Nextcloud injoignable", error, action = "Réessayer", onAction = { attempt++ })
            else -> PhotoGrid(auth, sections, nav.opened, nav, empty = "Cet album est vide", onView = onView)
        }
    }
}

/** Photos mois par mois ; OK ouvre le plein écran sur la suite affichée. */
@Composable
private fun PhotoGrid(
    auth: NextcloudAuth,
    sections: List<MonthSection>,
    state: LazyGridState,
    nav: GalleryNav,
    empty: String,
    onView: (List<GalleryItem>, GalleryItem) -> Unit,
) {
    if (sections.isEmpty()) {
        StateMessage(empty, null)
        return
    }
    val flat = remember(sections) { sections.flatMap { it.items } }
    // Retour du plein écran : on fait défiler jusqu'à la photo vue en dernier, qui reprend le focus.
    LaunchedEffect(nav.restoreFileId, sections) {
        val target = nav.restoreFileId ?: return@LaunchedEffect
        val position = GalleryTimeline.gridIndex(sections, target)
        // gridIndex compte un titre en tête de grille : il n'y en a pas ici.
        if (position == null) nav.restoreFileId = null else state.scrollToItem(position - 1)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 8.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        monthSections(auth, sections, nav) { onView(flat, it) }
    }
}

private fun LazyGridScope.monthSections(auth: NextcloudAuth, sections: List<MonthSection>, nav: GalleryNav, onClick: (GalleryItem) -> Unit) {
    sections.forEach { section ->
        item(key = "m:${section.month}", span = { GridItemSpan(maxLineSpan) }, contentType = "month") {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                Text(GalleryText.month(section.month), style = MaterialTheme.typography.headlineSmall)
                Text(GalleryText.monthCounts(section.photos, section.videos), style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
            }
        }
        items(section.items, key = { it.fileId }, contentType = { "photo" }) { item ->
            PhotoCard(auth, item, restore = item.fileId == nav.restoreFileId, onRestored = { nav.restoreFileId = null }, onClick = { onClick(item) })
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.padding(horizontal = 56.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
    }
}

/** Carte d'album Nextcloud : la dernière photo ajoutée en couverture. */
@Composable
private fun AlbumCard(auth: NextcloudAuth, album: NextcloudAlbum, width: Int?, onClick: () -> Unit) {
    val cover = album.coverFileId?.let { GalleryItem("", it, album.name, "image/jpeg", 0, 0, 0, 0, hasPreview = true) }
    CollectionCard(
        auth,
        cover = cover,
        kicker = album.owner?.let { "PARTAGÉ PAR ${it.uppercase()}" } ?: "ALBUM",
        title = album.name,
        detail = GalleryText.count(album.count, "élément", "éléments"),
        width = width,
        onClick = onClick,
    )
}

/** Couverture avec, par-dessus, la petite ligne en capitales, le titre et le nombre de photos. */
@Composable
internal fun CollectionCard(auth: NextcloudAuth, cover: GalleryItem?, kicker: String, title: String, detail: String, width: Int?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = (if (width != null) Modifier.width(width.dp) else Modifier.fillMaxWidth())
            .aspectRatio(16f / 9f)
            .onFocusChanged { focused = it.isFocused }
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (kicker.isNotEmpty()) Text(kicker, style = MaterialTheme.typography.labelSmall, color = Focus, maxLines = 1)
                Text(title, style = MaterialTheme.typography.titleMedium, color = Lune, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PhotoCard(auth: NextcloudAuth, item: GalleryItem, restore: Boolean, onRestored: () -> Unit, onClick: () -> Unit) {
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
        modifier = Modifier
            .focusRequester(requester)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = PanneauClair, focusedContainerColor = PanneauClair),
        scale = ClickableSurfaceDefaults.scale(focusedScale = NimbeFocus.SCALE),
    ) {
        Box(Modifier.fillMaxSize()) {
            NextcloudPreview(auth, item, PREVIEW_WIDTH, PREVIEW_HEIGHT, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item.isVideo) {
                Text(
                    "▶",
                    style = MaterialTheme.typography.titleSmall,
                    color = Lune,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .background(Nuit.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private const val COLUMNS = 5
private const val ALBUM_COLUMNS = 4
private const val MEMORY_WIDTH = 400
private const val ALBUM_WIDTH = 280

/** Albums montrés dans la rangée de « Pour toi » ; les autres sont dans la rubrique Albums. */
private const val ROW_ALBUMS = 12

/** Taille des aperçus demandés pour les cartes, en pixels : celle du dossier technique, gardée en cache par Nextcloud. */
private const val PREVIEW_WIDTH = 480
private const val PREVIEW_HEIGHT = 270
