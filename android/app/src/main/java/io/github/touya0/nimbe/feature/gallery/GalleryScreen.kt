package io.github.touya0.nimbe.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import io.github.touya0.nimbe.data.nextcloud.NextcloudAuth
import io.github.touya0.nimbe.data.nextcloud.NextcloudGallery
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfileKind
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

/**
 * Onglet Galerie : la chronologie du profil, mois par mois, en grille. Lue dans l'index local :
 * elle s'affiche tout de suite, et même serveur coupé. [onOpenSettings] : relier la galerie du profil.
 */
@Composable
fun GalleryScreen(
    gallery: NextcloudGallery,
    profile: Profile?,
    onOpenSettings: () -> Unit,
    /** Gardé par l'appelant : en revenant du plein écran, la grille est au même endroit. */
    gridState: LazyGridState,
    /** Photo regardée en dernier en plein écran : elle reprend le focus au retour. */
    restoreFileId: Long?,
    onRestored: () -> Unit,
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
        else -> GalleryContent(gallery, profile, auth!!.getOrThrow()!!, gridState, restoreFileId, onRestored, onOpen)
    }
}

@Composable
private fun GalleryContent(
    gallery: NextcloudGallery,
    profile: Profile,
    auth: NextcloudAuth,
    gridState: LazyGridState,
    restoreFileId: Long?,
    onRestored: () -> Unit,
    onOpen: (GalleryViewerRequest) -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile.id) { gallery.sync(profile.id) }
    val state by remember(profile.id) { gallery.state(profile.id) }.collectAsStateWithLifecycle()
    val counts by remember(profile.id) { gallery.counts(profile.id) }.collectAsStateWithLifecycle(initialValue = null)
    val syncedAt by remember(profile.id) { gallery.syncedAt(profile.id) }.collectAsStateWithLifecycle(initialValue = null)
    // Rangement par mois hors du fil de l'interface : quelques milliers de photos, à chaque synchronisation.
    val sections by remember(profile.id) {
        gallery.all(profile.id).map { GalleryTimeline.group(it) }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val userId by remember(profile.id) { gallery.link(profile.id).map { it?.userId } }.collectAsStateWithLifecycle(initialValue = null)
    // La visionneuse parcourt la même suite que la grille, mois après mois.
    val flat = remember(sections) { sections.flatMap { it.items } }
    fun open(item: GalleryItem) {
        val id = userId ?: return
        onOpen(GalleryViewerRequest(auth, id, flat, flat.indexOf(item)))
    }
    // Retour du plein écran : on fait défiler jusqu'à la photo vue en dernier, qui reprend le focus.
    LaunchedEffect(restoreFileId, sections) {
        val target = restoreFileId ?: return@LaunchedEffect
        if (sections.isEmpty()) return@LaunchedEffect
        val position = GalleryTimeline.gridIndex(sections, target)
        if (position == null) onRestored() else gridState.scrollToItem(position)
    }
    val retry = { scope.launch { gallery.sync(profile.id, force = true) }; Unit }

    // Jamais synchronisée : rien à montrer tant que la première lecture n'a pas abouti.
    if (syncedAt == null) {
        when {
            state.error != null -> StateMessage("Galerie injoignable", state.error, action = "Réessayer", onAction = retry)
            else -> StateMessage("Première synchronisation…", "Nimbe fait la liste de tes photos et vidéos. Ça peut prendre une minute.")
        }
        return
    }
    val total = counts?.let { it.photos + it.videos } ?: 0
    if (total == 0 && !state.syncing) {
        StateMessage("Aucune photo pour l'instant", "Les photos et vidéos de ton compte Nextcloud apparaîtront ici.", action = "Synchroniser", onAction = retry)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Galerie de ${profile.name}", style = MaterialTheme.typography.displaySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            GalleryText.summary(counts?.photos ?: 0, counts?.videos ?: 0, syncedAt, System.currentTimeMillis()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = LuneAttenuee,
                        )
                        if (state.syncing) Spinner(Focus)
                    }
                    state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Alerte) }
                }
                PillButton("Synchroniser", onClick = retry)
            }
        }
        sections.forEach { section ->
            item(key = "m:${section.month}", span = { GridItemSpan(maxLineSpan) }, contentType = "month") {
                Row(
                    Modifier.padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(GalleryText.month(section.month), style = MaterialTheme.typography.headlineSmall)
                    Text(GalleryText.monthCounts(section.photos, section.videos), style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
                }
            }
            items(section.items, key = { it.fileId }, contentType = { "photo" }) { item ->
                PhotoCard(auth, item, restore = item.fileId == restoreFileId, onRestored = onRestored, onClick = { open(item) })
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

/** Taille des aperçus demandés pour la grille, en pixels : celle du dossier technique, gardée en cache par Nextcloud. */
private const val PREVIEW_WIDTH = 480
private const val PREVIEW_HEIGHT = 270
