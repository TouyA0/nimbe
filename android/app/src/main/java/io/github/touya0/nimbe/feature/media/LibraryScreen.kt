package io.github.touya0.nimbe.feature.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.touya0.nimbe.ui.components.NimbeIcon
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.ItemPage
import io.github.touya0.nimbe.data.jellyfin.JellyfinAuth
import io.github.touya0.nimbe.data.jellyfin.JellyfinException
import io.github.touya0.nimbe.data.jellyfin.JellyfinLibrary
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.ui.components.bleed
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 48

/**
 * Grille d'affiches d'une bibliothèque Jellyfin (films ou séries), chargée par pages de 48 :
 * la page suivante est demandée en approchant du bas. [pageKey] identifie la liste (tri et catégorie compris)
 * pour la mémoire des réponses ; [loadPage] la charge. [onFocus] nourrit le bandeau du haut ([onFallback] avant tout focus),
 * [onTotal] le compteur.
 */
@Composable
fun JellyfinGrid(
    what: String,
    emptyTitle: String,
    emptyDetail: String,
    pageKey: String,
    loadPage: suspend JellyfinLibrary.(JellyfinAuth, Int, Int) -> ItemPage,
    onOpenItem: (MediaItem) -> Unit,
    onFocus: (MediaItem) -> Unit,
    onFallback: (MediaItem) -> Unit,
    onTotal: (Int?) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = LocalJellyfin.current
    val first = rememberJellyfin("$pageKey:0:$PAGE_SIZE", pageKey) { loadPage(it, 0, PAGE_SIZE) }
    val more = remember(pageKey) { mutableStateListOf<MediaItem>() }
    var loadingMore by remember(pageKey) { mutableStateOf(false) }
    val items = first.value?.items.orEmpty() + more
    val total = first.value?.total
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    LaunchedEffect(total) { onTotal(total) }
    LaunchedEffect(items.firstOrNull()?.id) { items.firstOrNull()?.let(onFallback) }

    fun loadMore() {
        val library = ui?.library ?: return
        if (loadingMore || items.size >= (total ?: 0)) return
        loadingMore = true
        scope.launch {
            try {
                val auth = library.auth() ?: return@launch
                more += library.loadPage(auth, items.size, PAGE_SIZE).items.filter { new -> items.none { it.id == new.id } }
            } catch (_: JellyfinException) {
                // La page sera redemandée au prochain défilement.
            } finally {
                loadingMore = false
            }
        }
    }
    LaunchedEffect(gridState, items.size, total) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= items.size - 16) loadMore() }
    }
    ScrollToRestore(gridState, items, restoreFocusId)

    Box(modifier.fillMaxSize()) {
        LoadStates(first, what, takeFocus = false) {
            if (items.isEmpty()) {
                StateMessage(emptyTitle, emptyDetail)
            } else {
                PosterGrid(gridState) {
                    items(items, key = { it.id }) { item ->
                        PosterCard(
                            item = item,
                            onClick = { onOpenItem(item) },
                            width = GRID_POSTER_WIDTH,
                            onFocus = { onFocus(item) },
                            restoreFocus = item.id == restoreFocusId,
                            onRestored = onRestored,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pilule de filtre ou de saison : pleine quand elle est choisie, voile clair sinon. [trailing] signale
 * qu'elle ouvre un choix (▾). [label] remplace le texte pour les lecteurs d'écran.
 */
@Composable
fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    trailing: ImageVector? = null,
    label: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
            .focusRing(focused, NimbeFocus.PillRadius, scale = NimbeFocus.SMALL_SCALE),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Lune else Lune.copy(alpha = 0.12f),
            contentColor = if (selected) Nuit else Lune,
            focusedContainerColor = Lune,
            focusedContentColor = Nuit,
            pressedContainerColor = Lune,
            pressedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = if (trailing != null) 10.dp else 16.dp, top = 7.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            trailing?.let { NimbeIcon(it, size = 18.dp) }
        }
    }
}
