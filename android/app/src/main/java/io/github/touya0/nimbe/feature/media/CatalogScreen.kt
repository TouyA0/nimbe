package io.github.touya0.nimbe.feature.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.ItemPage
import io.github.touya0.nimbe.data.jellyfin.JellyfinAuth
import io.github.touya0.nimbe.data.jellyfin.JellyfinLibrary
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.streaming.Category
import io.github.touya0.nimbe.data.streaming.StreamingCatalog
import io.github.touya0.nimbe.data.streaming.StreamingException
import io.github.touya0.nimbe.data.streaming.StreamingPage
import io.github.touya0.nimbe.data.streaming.StreamingService
import io.github.touya0.nimbe.data.streaming.StreamingSort
import io.github.touya0.nimbe.data.streaming.StreamingTitle
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.feature.streaming.LocalStreaming
import io.github.touya0.nimbe.feature.streaming.StreamingBackdrop
import io.github.touya0.nimbe.feature.streaming.StreamingHero
import io.github.touya0.nimbe.feature.streaming.StreamingPosterCard
import io.github.touya0.nimbe.feature.streaming.StreamingUi
import io.github.touya0.nimbe.feature.streaming.StreamingWideCard
import io.github.touya0.nimbe.feature.streaming.openedLabel
import io.github.touya0.nimbe.feature.streaming.rememberStreaming
import io.github.touya0.nimbe.ui.components.bleed
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.launch

/** Ce qui est affiché : tout ([ALL]), seulement Jellyfin ([JELLYFIN]), ou le code d'une plateforme. */
object CatalogSources {
    const val ALL = "all"
    const val JELLYFIN = "jellyfin"
}

/** Pages d'une grille de plateforme déjà chargées ; [next] null quand il n'y en a plus. */
data class StreamingGridPages(val items: List<StreamingTitle>, val next: String?)

/**
 * Filtres et pages d'un onglet Films ou Séries, gardés par l'appelant : en revenant d'une fiche,
 * on retrouve la même source, la même catégorie et les pages déjà chargées.
 */
@Stable
class CatalogState {
    /** null : le choix par défaut (Tout s'il y a des plateformes, sinon Jellyfin). */
    var source by mutableStateOf<String?>(null)
    var category by mutableStateOf<Category?>(null)
    var streamingSort by mutableStateOf(StreamingSort.Popular)
    val pages = mutableStateMapOf<String, StreamingGridPages>()
}

/** Ce qui est en haut de l'écran : un contenu Jellyfin ou un titre des plateformes, et son accroche. */
sealed interface CatalogFocus {
    val id: String

    data class Jellyfin(val item: MediaItem, val kicker: String?) : CatalogFocus {
        override val id get() = item.id
    }

    data class Streaming(val title: StreamingTitle, val kicker: String?) : CatalogFocus {
        override val id get() = title.id
    }
}

/**
 * La partie Jellyfin d'un onglet : titres et textes, liste (déjà triée et filtrée par catégorie),
 * accroche des cartes, et ses filtres propres (tri des films).
 */
class JellyfinSection(
    val rowTitle: String,
    val what: String,
    val emptyTitle: String,
    val emptyDetail: String,
    val pageKey: String,
    val loadPage: suspend JellyfinLibrary.(JellyfinAuth, Int, Int) -> ItemPage,
    val kicker: (MediaItem) -> String?,
    val sorts: (@Composable RowScope.() -> Unit)? = null,
)

/**
 * Onglet Films ou Séries : Jellyfin et les plateformes de streaming réunis.
 * En haut le titre focalisé ; puis les sources (Tout, Jellyfin, chaque plateforme) et les catégories.
 * « Tout » montre des rangées (Continuer à regarder, Jellyfin, chaque plateforme) ; une source seule, une grille.
 */
@Composable
fun CatalogScreen(
    kind: TitleKind,
    title: String,
    state: CatalogState,
    jellyfin: JellyfinSection,
    onOpenItem: (MediaItem) -> Unit,
    onPlayItem: (MediaItem) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val streaming = LocalStreaming.current
    val jellyfinUi = LocalJellyfin.current
    val services = streaming?.services.orEmpty()
    val source = state.source?.takeIf { key ->
        key == CatalogSources.JELLYFIN || (key == CatalogSources.ALL && services.isNotEmpty()) || services.any { it.code == key }
    } ?: if (services.isEmpty()) CatalogSources.JELLYFIN else CatalogSources.ALL
    val service = services.firstOrNull { it.code == source }

    var focused by remember(source, state.category) { mutableStateOf<CatalogFocus?>(null) }
    var fallback by remember(source, state.category) { mutableStateOf<CatalogFocus?>(null) }
    var total by remember(source, state.category) { mutableStateOf<Int?>(null) }
    val hero = focused ?: fallback
    val onFocus: (CatalogFocus) -> Unit = { focused = it }
    val onFallback: (CatalogFocus) -> Unit = { if (fallback == null) fallback = it }

    val sourceRequester = remember { FocusRequester() }
    Box(
        modifier
            .fillMaxSize()
            .focusProperties {
                // En descendant des onglets, on arrive sur la source choisie, pas sur la catégorie la plus proche.
                onEnter = { if (requestedFocusDirection == FocusDirection.Down) runCatching { sourceRequester.requestFocus() } }
                // Gauche et droite restent dans l'écran : au bout d'une rangée, on ne change pas d'onglet par accident.
                onExit = {
                    if (requestedFocusDirection == FocusDirection.Left || requestedFocusDirection == FocusDirection.Right) cancelFocusChange()
                }
            }
            .focusGroup(),
    ) {
        when (hero) {
            is CatalogFocus.Jellyfin -> Backdrop(hero.item.backdrop)
            is CatalogFocus.Streaming -> StreamingBackdrop(hero.title.backdrop)
            null -> Unit
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
            // Résumé sur une ligne : la place va aux affiches, la fiche donne le reste.
            Box(Modifier.height(150.dp)) {
                when (hero) {
                    is CatalogFocus.Jellyfin -> Hero(hero.item, hero.kicker, overviewLines = 1)
                    is CatalogFocus.Streaming -> StreamingHero(hero.title, hero.kicker, overviewLines = 1)
                    null -> HeroPlaceholder(kind, hasServices = services.isNotEmpty(), canChoose = streaming?.onOpenSettings != null)
                }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                total?.takeIf { it > 0 }?.let { Text("$it", style = MaterialTheme.typography.titleSmall, color = LuneAttenuee) }
                if (jellyfinUi?.down == true && source != CatalogSources.ALL && service == null) {
                    Text("● Jellyfin injoignable · dernière liste reçue", style = MaterialTheme.typography.titleSmall, color = Alerte)
                }
                Spacer(Modifier.weight(1f))
                SourcePills(source, services, streaming, sourceRequester, onSelect = { state.source = it })
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryPills(state.category, onSelect = { state.category = it }, modifier = Modifier.weight(1f))
                Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        source == CatalogSources.JELLYFIN -> jellyfin.sorts?.invoke(this)
                        service != null -> StreamingSort.pills.forEach { option ->
                            FilterPill(option.label, selected = option == state.streamingSort, onClick = { state.streamingSort = option })
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    source == CatalogSources.JELLYFIN -> JellyfinGrid(
                        what = jellyfin.what,
                        emptyTitle = if (state.category != null) "Rien dans cette catégorie" else jellyfin.emptyTitle,
                        emptyDetail = if (state.category != null) {
                            "Aucun titre de ta bibliothèque Jellyfin n'est classé en « ${state.category?.label} »."
                        } else {
                            jellyfin.emptyDetail
                        },
                        pageKey = jellyfin.pageKey,
                        loadPage = jellyfin.loadPage,
                        onOpenItem = onOpenItem,
                        onFocus = { onFocus(CatalogFocus.Jellyfin(it, jellyfin.kicker(it))) },
                        onFallback = { onFallback(CatalogFocus.Jellyfin(it, jellyfin.kicker(it))) },
                        onTotal = { total = it },
                        restoreFocusId = restoreFocusId,
                        onRestored = onRestored,
                    )
                    service != null -> StreamingGrid(
                        kind = kind,
                        service = service,
                        state = state,
                        onFocus = onFocus,
                        onFallback = onFallback,
                        onTotal = { total = it },
                        restoreFocusId = restoreFocusId,
                        onRestored = onRestored,
                    )
                    else -> AllRows(
                        kind = kind,
                        state = state,
                        jellyfin = jellyfin,
                        services = services,
                        onOpenItem = onOpenItem,
                        onPlayItem = onPlayItem,
                        onFocus = onFocus,
                        onFallback = onFallback,
                        restoreFocusId = restoreFocusId,
                        onRestored = onRestored,
                    )
                }
            }
        }
    }
}

/** Tout, Jellyfin, puis une pilule par plateforme ; sans plateforme, un raccourci vers le réglage. */
@Composable
private fun SourcePills(
    source: String,
    services: List<StreamingService>,
    streaming: StreamingUi?,
    selectedRequester: FocusRequester,
    onSelect: (String) -> Unit,
) {
    fun Modifier.selected(key: String) = if (key == source) focusRequester(selectedRequester) else this
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (services.isNotEmpty()) {
            FilterPill("Tout", selected = source == CatalogSources.ALL, onClick = { onSelect(CatalogSources.ALL) }, modifier = Modifier.selected(CatalogSources.ALL))
        }
        FilterPill(
            "Jellyfin",
            selected = source == CatalogSources.JELLYFIN,
            onClick = { onSelect(CatalogSources.JELLYFIN) },
            modifier = Modifier.selected(CatalogSources.JELLYFIN),
        )
        services.forEach { service ->
            FilterPill(service.label, selected = source == service.code, onClick = { onSelect(service.code) }, modifier = Modifier.selected(service.code))
        }
        if (services.isEmpty()) streaming?.onOpenSettings?.let { open -> FilterPill("＋  Mes plateformes", selected = false, onClick = open) }
    }
}

/** Haut de l'onglet avant que la liste soit là, ou sans rien à montrer : ce que l'onglet réunit et comment l'enrichir. */
@Composable
private fun HeroPlaceholder(kind: TitleKind, hasServices: Boolean, canChoose: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
        Text(if (kind == TitleKind.Movie) "Tous tes films" else "Toutes tes séries", style = MaterialTheme.typography.displaySmall)
        Text(
            when {
                hasServices -> "Ta bibliothèque Jellyfin et tes plateformes de streaming, réunies et classées par catégorie."
                canChoose -> "Ta bibliothèque Jellyfin. Ajoute tes abonnements avec « ＋ Mes plateformes » : leurs ${kindWord(kind)} viendront s'y ajouter."
                else -> "La bibliothèque Jellyfin de la maison, classée par catégorie."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
            modifier = Modifier.width(620.dp),
        )
    }
}

/** « Toutes » puis les catégories ; la rangée défile à la télécommande. */
@Composable
private fun CategoryPills(selected: Category?, onSelect: (Category?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        // Décalée de la marge laissée à l'anneau de focus : « Toutes » s'aligne sur le titre de l'onglet.
        modifier = modifier.offset(x = (-8).dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "toutes") { FilterPill("Toutes", selected = selected == null, onClick = { onSelect(null) }) }
        items(Category.entries, key = { it.code }) { category ->
            FilterPill(category.label, selected = selected == category, onClick = { onSelect(if (selected == category) null else category) })
        }
    }
}

/**
 * Grille d'une plateforme, par pages de 40 gardées dans [state] : revenir d'une fiche retrouve
 * les pages déjà parcourues.
 */
@Composable
private fun StreamingGrid(
    kind: TitleKind,
    service: StreamingService,
    state: CatalogState,
    onFocus: (CatalogFocus) -> Unit,
    onFallback: (CatalogFocus) -> Unit,
    onTotal: (Int?) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
) {
    val ui = LocalStreaming.current ?: return
    val category = state.category
    val sort = state.streamingSort
    val key = StreamingCatalog.pageKey(kind, listOf(service), category, sort, ui.kids, after = null)
    val first = rememberStreaming(key, key) { catalog(kind, listOf(service), category, sort, ui.kids) }
    val firstPage = first.value
    val pages = state.pages[key]
    val items = pages?.items ?: firstPage?.items.orEmpty()
    val next = if (pages != null) pages.next else firstPage?.next
    var loadingMore by remember(key) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    LaunchedEffect(firstPage?.total) { onTotal(firstPage?.total) }
    LaunchedEffect(items.firstOrNull()?.id) { items.firstOrNull()?.let { onFallback(CatalogFocus.Streaming(it, null)) } }

    fun loadMore() {
        val cursor = next ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            try {
                val page = ui.catalog.catalog(kind, listOf(service), category, sort, ui.kids, after = cursor)
                state.pages[key] = StreamingGridPages((items + page.items).distinctBy { it.id }, page.next)
            } catch (_: StreamingException) {
                // La page sera redemandée au prochain défilement.
            } finally {
                loadingMore = false
            }
        }
    }
    LaunchedEffect(gridState, items.size, next) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= items.size - 16) loadMore() }
    }
    LaunchedEffect(restoreFocusId, items) {
        val index = items.indexOfFirst { it.id == restoreFocusId }
        if (index >= 0) gridState.scrollToItem(index)
    }

    when (first.state) {
        Load.Loading -> StateMessage("Chargement…", null)
        is Load.Failed -> StateMessage(
            "Catalogue ${service.label} indisponible",
            "Le stick n'arrive pas à joindre JustWatch, qui donne le catalogue des plateformes. Vérifie la connexion internet : " +
                "Jellyfin et tes applis marchent normalement.",
            action = "Réessayer",
            onAction = first.retry,
        )
        else -> if (items.isEmpty()) {
            StateMessage(
                "Rien dans cette catégorie",
                if (category != null) "${service.label} ne propose pas de ${kindWord(kind)} « ${category.label} » en ce moment." else "Aucun titre trouvé sur ${service.label}.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                state = gridState,
                modifier = Modifier.bleed(48.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items, key = { it.id }) { title ->
                    StreamingPosterCard(
                        title = title,
                        onClick = { ui.onOpenTitle(title) },
                        width = 96.dp,
                        // Toute la grille vient de la même plateforme : pas de badge sur chaque affiche.
                        service = null,
                        inJellyfin = ui.inJellyfin(title) != null,
                        onFocus = { onFocus(CatalogFocus.Streaming(title, null)) },
                        restoreFocus = title.id == restoreFocusId,
                        onRestored = onRestored,
                    )
                }
            }
        }
    }
}

/**
 * « Tout » : Continuer à regarder (sans catégorie), ta bibliothèque Jellyfin, puis les titres populaires
 * de chaque plateforme, et les tendances de toutes. Chaque rangée finit par « Tout voir », qui ouvre la grille de sa source.
 */
@Composable
private fun AllRows(
    kind: TitleKind,
    state: CatalogState,
    jellyfin: JellyfinSection,
    services: List<StreamingService>,
    onOpenItem: (MediaItem) -> Unit,
    onPlayItem: (MediaItem) -> Unit,
    onFocus: (CatalogFocus) -> Unit,
    onFallback: (CatalogFocus) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
) {
    val ui = LocalStreaming.current ?: return
    val jellyfinUi = LocalJellyfin.current
    val category = state.category
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
        if (category == null) ContinueRow(kind, onPlayItem, onFocus, onFallback, restoreFocusId, onRestored)

        if (jellyfinUi?.link != null) {
            val rowKey = "${jellyfin.pageKey}:0:$ROW_SIZE"
            val loader = rememberJellyfin(rowKey, rowKey) { jellyfin.loadPage(this, it, 0, ROW_SIZE) }
            val items = loader.value?.items.orEmpty()
            LaunchedEffect(items.firstOrNull()?.id) { items.firstOrNull()?.let { onFallback(CatalogFocus.Jellyfin(it, jellyfin.kicker(it))) } }
            CatalogRow(
                title = jellyfin.rowTitle,
                message = when {
                    loader.state is Load.Loading -> "Chargement…"
                    loader.state is Load.Failed -> "Jellyfin ne répond pas : ta bibliothèque reviendra avec le serveur."
                    items.isEmpty() -> if (category != null) "Rien en « ${category.label} » sur Jellyfin." else jellyfin.emptyDetail
                    else -> null
                },
                keys = items.map { it.id },
                restoreFocusId = restoreFocusId,
            ) {
                items(items, key = { "jellyfin:${it.id}" }) { item ->
                    PosterCard(
                        item = item,
                        onClick = { onOpenItem(item) },
                        width = POSTER_WIDTH,
                        onFocus = { onFocus(CatalogFocus.Jellyfin(item, jellyfin.kicker(item))) },
                        restoreFocus = item.id == restoreFocusId,
                        onRestored = onRestored,
                    )
                }
                item(key = "jellyfin:all") { SeeAllCard("Tout Jellyfin", onClick = { state.source = CatalogSources.JELLYFIN }) }
            }
        }

        services.forEach { service ->
            StreamingRow(
                title = "Populaires sur ${service.label}",
                load = { catalog(kind, listOf(service), category, StreamingSort.Popular, ui.kids, first = ROW_SIZE) },
                key = StreamingCatalog.pageKey(kind, listOf(service), category, StreamingSort.Popular, ui.kids, null, ROW_SIZE),
                service = service,
                emptyText = "Rien en « ${category?.label} » sur ${service.label}.",
                onSeeAll = { state.source = service.code },
                onFocus = onFocus,
                onFallback = onFallback,
                restoreFocusId = restoreFocusId,
                onRestored = onRestored,
            )
        }
        if (services.size > 1) {
            StreamingRow(
                title = "Tendances sur tes plateformes",
                load = { catalog(kind, services, category, StreamingSort.Trending, ui.kids, first = ROW_SIZE) },
                key = StreamingCatalog.pageKey(kind, services, category, StreamingSort.Trending, ui.kids, null, ROW_SIZE),
                service = null,
                emptyText = "Rien de tendance dans cette catégorie en ce moment.",
                onSeeAll = null,
                onFocus = onFocus,
                onFallback = onFallback,
                restoreFocusId = restoreFocusId,
                onRestored = onRestored,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** « Continuer à regarder » de l'onglet : reprises Jellyfin et titres ouverts sur les plateformes, de ce type seulement. */
@Composable
private fun ContinueRow(
    kind: TitleKind,
    onPlayItem: (MediaItem) -> Unit,
    onFocus: (CatalogFocus) -> Unit,
    onFallback: (CatalogFocus) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
) {
    val ui = LocalStreaming.current ?: return
    val jellyfin = rememberJellyfin("continue") { continueWatching(it) }
    val entries = ContinueEntry.merge(jellyfin.value.orEmpty(), ui.history, kind, sameAs = { ui.inJellyfin(it.toTitle()) })
    if (entries.isEmpty()) return
    LaunchedEffect(entries.first().key) { onFallback(entries.first().focus(ui)) }
    CatalogRow(title = "Continuer à regarder", message = null, keys = entries.map { it.id }, restoreFocusId = restoreFocusId) {
        items(entries, key = { it.key }) { entry -> ContinueCard(entry, onPlayItem, onFocus, restoreFocusId, onRestored) }
    }
}

/** Carte de « Continuer à regarder », commune à l'accueil et aux onglets. */
@Composable
fun ContinueCard(
    entry: ContinueEntry,
    onPlayItem: (MediaItem) -> Unit,
    onFocus: (CatalogFocus) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
    width: Dp = 196.dp,
) {
    val ui = LocalStreaming.current
    when (entry) {
        is ContinueEntry.Jellyfin -> WideCard(
            item = entry.item,
            label = MediaText.cardLabel(entry.item),
            detail = MediaText.progressLine(entry.item),
            onClick = { onPlayItem(entry.item) },
            width = width,
            onFocus = { onFocus(CatalogFocus.Jellyfin(entry.item, CONTINUE_KICKER)) },
            restoreFocus = entry.item.id == restoreFocusId,
            onRestored = onRestored,
        )
        is ContinueEntry.Streaming -> {
            val service = entry.open.streamingService ?: return
            val title = entry.open.toTitle()
            StreamingWideCard(
                title = title,
                service = service,
                detail = openedLabel(entry.open.openedAt) + " sur " + service.label,
                onClick = { ui?.onOpenTitle?.invoke(title) },
                width = width,
                onFocus = { onFocus(CatalogFocus.Streaming(title, "REPRENDRE SUR ${service.label.uppercase()}")) },
                onMenu = ui?.let { streaming -> { requester -> streaming.onMenu(entry.open, requester) } },
                restoreFocus = title.id == restoreFocusId,
                onRestored = onRestored,
            )
        }
    }
}

const val CONTINUE_KICKER = "REPRENDRE LÀ OÙ TU T'ES ARRÊTÉ"

/** Le bandeau du haut pour une carte de « Continuer à regarder ». */
fun ContinueEntry.focus(ui: StreamingUi?): CatalogFocus = when (this) {
    is ContinueEntry.Jellyfin -> CatalogFocus.Jellyfin(item, CONTINUE_KICKER)
    is ContinueEntry.Streaming -> CatalogFocus.Streaming(open.toTitle(), "REPRENDRE SUR ${open.streamingService?.label?.uppercase().orEmpty()}")
}

/** Identifiant de la carte, celui que l'écran cherche pour lui rendre le focus au retour d'une fiche. */
val ContinueEntry.id: String
    get() = when (this) {
        is ContinueEntry.Jellyfin -> item.id
        is ContinueEntry.Streaming -> open.titleId
    }

/** Rangée d'une plateforme (ou de toutes) : titres populaires ou tendances. */
@Composable
private fun StreamingRow(
    title: String,
    load: suspend StreamingCatalog.() -> StreamingPage,
    key: String,
    service: StreamingService?,
    emptyText: String,
    onSeeAll: (() -> Unit)?,
    onFocus: (CatalogFocus) -> Unit,
    onFallback: (CatalogFocus) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
) {
    val ui = LocalStreaming.current ?: return
    val loader = rememberStreaming(key, key) { load() }
    val items = loader.value?.items.orEmpty()
    LaunchedEffect(items.firstOrNull()?.id) { items.firstOrNull()?.let { onFallback(CatalogFocus.Streaming(it, null)) } }
    CatalogRow(
        title = title,
        message = when {
            loader.state is Load.Loading -> "Chargement…"
            loader.state is Load.Failed -> "Catalogue indisponible : le stick n'arrive pas à joindre JustWatch."
            items.isEmpty() -> emptyText
            else -> null
        },
        keys = items.map { it.id },
        restoreFocusId = restoreFocusId,
    ) {
        items(items, key = { "${service?.code}:${it.id}" }) { item ->
            StreamingPosterCard(
                title = item,
                onClick = { ui.onOpenTitle(item) },
                width = POSTER_WIDTH,
                // Le badge ne sert que dans la rangée qui mêle les plateformes.
                service = if (service == null) item.services.firstOrNull { it in ui.services } else null,
                inJellyfin = ui.inJellyfin(item) != null,
                onFocus = { onFocus(CatalogFocus.Streaming(item, null)) },
                restoreFocus = item.id == restoreFocusId,
                onRestored = onRestored,
            )
        }
        onSeeAll?.let { open -> item(key = "${service?.code}:all") { SeeAllCard("Tout ${service?.label.orEmpty()}", onClick = open) } }
    }
}

/**
 * Une rangée titrée : soit ses cartes, soit une phrase (chargement, vide, erreur) à la même place,
 * pour que l'écran ne saute pas. Au retour d'une fiche, la rangée défile jusqu'à la carte d'origine.
 */
@Composable
private fun CatalogRow(
    title: String,
    message: String?,
    keys: List<String>,
    restoreFocusId: String?,
    content: LazyListScope.() -> Unit,
) {
    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
    if (message != null) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(restoreFocusId, keys) {
        val index = keys.indexOf(restoreFocusId)
        if (index >= 0) listState.scrollToItem(index)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.bleed(48.dp),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Dernière carte d'une rangée : ouvre la grille complète de sa source. */
@Composable
private fun SeeAllCard(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(POSTER_WIDTH)
            .aspectRatio(2f / 3f)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, 10.dp)
            .semantics { contentDescription = "$label, voir la liste complète" },
        shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Panneau,
            contentColor = Lune,
            focusedContainerColor = PanneauClair,
            focusedContentColor = Lune,
            pressedContainerColor = PanneauClair,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("›", style = MaterialTheme.typography.displaySmall)
            Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
    }
}

private fun kindWord(kind: TitleKind) = if (kind == TitleKind.Movie) "films" else "séries"

private val POSTER_WIDTH = 104.dp
private const val ROW_SIZE = 20
