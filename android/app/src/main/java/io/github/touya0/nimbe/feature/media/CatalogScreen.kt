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
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import io.github.touya0.nimbe.shell.HeaderHeight
import io.github.touya0.nimbe.ui.components.ContentRow
import io.github.touya0.nimbe.ui.components.LocalMenu
import io.github.touya0.nimbe.ui.components.MenuOption
import io.github.touya0.nimbe.ui.components.MenuRequest
import io.github.touya0.nimbe.ui.components.MenuState
import io.github.touya0.nimbe.ui.components.NimbeIcon
import io.github.touya0.nimbe.ui.components.NimbeIcons
import io.github.touya0.nimbe.ui.components.PivotScroll
import io.github.touya0.nimbe.ui.components.RowCardPadding
import io.github.touya0.nimbe.ui.components.RowMessage
import io.github.touya0.nimbe.ui.components.RowTitle
import io.github.touya0.nimbe.ui.components.ScreenMargin
import io.github.touya0.nimbe.ui.components.rowCardTop
import io.github.touya0.nimbe.ui.theme.Nuit
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
import androidx.compose.runtime.produceState
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
import io.github.touya0.nimbe.data.watchnext.WatchNextProgram
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
import kotlinx.coroutines.delay
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

    /** La rangée de la carte focalisée : un film peut être dans plusieurs, le retour d'une fiche retrouve la bonne. */
    var focusedRow by mutableStateOf<String?>(null)
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

    data class WatchNext(val program: WatchNextProgram) : CatalogFocus {
        override val id get() = "watchnext:${program.id}"
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
    val sort: SortChoice? = null,
    /** Rangées de la page d'ouverture ; sans elles, une seule rangée [rowTitle]. */
    val shelves: List<JellyfinShelf> = emptyList(),
)

/** Une rangée Jellyfin de la page d'ouverture, avec sa clé de mémoire et sa liste. */
class JellyfinShelf(val title: String, val key: String, val loadPage: suspend JellyfinLibrary.(JellyfinAuth, Int, Int) -> ItemPage)

/** Un tri proposé dans la barre de filtres : ses libellés, celui choisi, et quoi faire d'un nouveau choix. */
class SortChoice(val labels: List<String>, val selected: Int, val onSelect: (Int) -> Unit)

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
    // Sans plateforme, la page d'ouverture existe quand même si Jellyfin a de quoi remplir ses rangées.
    val hasOverview = streaming != null && (services.isNotEmpty() || (jellyfin.shelves.isNotEmpty() && jellyfinUi?.link != null))
    val source = state.source?.takeIf { key ->
        key == CatalogSources.JELLYFIN || (key == CatalogSources.ALL && hasOverview) || services.any { it.code == key }
    } ?: if (hasOverview) CatalogSources.ALL else CatalogSources.JELLYFIN
    val service = services.firstOrNull { it.code == source }

    var focused by remember(source, state.category) { mutableStateOf<CatalogFocus?>(null) }
    var fallback by remember(source, state.category) { mutableStateOf<CatalogFocus?>(null) }
    var total by remember(source, state.category) { mutableStateOf<Int?>(null) }
    val hero = focused ?: fallback
    val onFocus: (CatalogFocus) -> Unit = { focused = it }
    val onFallback: (CatalogFocus) -> Unit = { if (fallback == null) fallback = it }

    val sourceRequester = remember { FocusRequester() }
    val menu = LocalMenu.current
    val sort = when {
        source == CatalogSources.JELLYFIN -> jellyfin.sort
        service != null -> SortChoice(StreamingSort.pills.map { it.label }, StreamingSort.pills.indexOf(state.streamingSort)) {
            state.streamingSort = StreamingSort.pills[it]
        }
        else -> null
    }
    Box(modifier.fillMaxSize()) {
        when (hero) {
            // L'image s'efface avant les affiches : elles ne se détachent bien que sur le fond uni.
            is CatalogFocus.Jellyfin -> Backdrop(hero.item.backdrop, compact = true)
            is CatalogFocus.Streaming -> StreamingBackdrop(hero.title.backdrop, compact = true)
            is CatalogFocus.WatchNext -> FadingBackdrop(hero.program.image, compact = true)
            null -> Unit
        }
        // La zone de focus commence sous l'en-tête : Bas, depuis un onglet, la trouve en dessous de lui.
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = HeaderHeight)
                .focusProperties {
                    // En descendant des onglets, on arrive sur la source choisie, pas sur le filtre le plus proche.
                    onEnter = { if (requestedFocusDirection == FocusDirection.Down) runCatching { sourceRequester.requestFocus() } }
                    // Gauche et droite restent dans l'écran : au bout d'une rangée, on ne change pas d'onglet par accident.
                    onExit = {
                        if (requestedFocusDirection == FocusDirection.Left || requestedFocusDirection == FocusDirection.Right) cancelFocusChange()
                    }
                }
                .focusGroup(),
        ) {
            // Résumé sur deux lignes : la place va aux affiches, la fiche donne le reste.
            Box(Modifier.padding(horizontal = ScreenMargin).padding(top = 10.dp).height(CATALOG_HERO_HEIGHT)) {
                FocusHero(hero, overviewLines = 2, compact = true) {
                    HeroPlaceholder(kind, hasServices = services.isNotEmpty(), canChoose = streaming?.onOpenSettings != null)
                }
            }
            // Une seule barre de filtres : les sources à gauche, la catégorie et le tri à droite, en menus.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ScreenMargin).padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SourcePills(source, services, streaming, hasOverview, if (jellyfin.shelves.isEmpty()) "Tout" else "À la une", sourceRequester, onSelect = { state.source = it })
                Spacer(Modifier.weight(1f))
                // Pendant une coupure, l'en-tête et les cartes grisées le disent déjà : pas de troisième rappel ici.
                total?.takeIf { it > 0 }?.let {
                    Text("$it ${kindWord(kind)}", style = MaterialTheme.typography.titleSmall, color = LuneAttenuee)
                }
                FilterPill(
                    text = state.category?.label ?: "Catégories",
                    selected = state.category != null,
                    trailing = NimbeIcons.ExpandMore,
                    onClick = { menu?.let { openCategories(it, kind, state) } },
                    label = "Catégorie : ${state.category?.label ?: "toutes"}",
                )
                sort?.let { choice ->
                    FilterPill(
                        text = choice.labels[choice.selected],
                        selected = false,
                        trailing = NimbeIcons.ExpandMore,
                        onClick = { menu?.let { openSort(it, choice) } },
                        label = "Trier par : ${choice.labels[choice.selected]}",
                    )
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

/** Choix de la catégorie, dans le panneau de droite : la catégorie en cours est cochée et a le focus. */
private fun openCategories(menu: MenuState, kind: TitleKind, state: CatalogState) {
    val options = listOf(MenuOption("", "Toutes les catégories", selected = state.category == null) { state.category = null }) +
        Category.entries.map { category ->
            MenuOption("", category.label, selected = state.category == category) { state.category = category }
        }
    menu.open(MenuRequest(subject = if (kind == TitleKind.Movie) "Films" else "Séries", options = options, title = "Catégorie"))
}

private fun openSort(menu: MenuState, choice: SortChoice) {
    val options = choice.labels.mapIndexed { index, label -> MenuOption("", label, selected = index == choice.selected) { choice.onSelect(index) } }
    menu.open(MenuRequest(subject = "Ordre des titres", options = options, title = "Trier par"))
}

/** Tout, Jellyfin, puis une pilule par plateforme ; sans plateforme, un raccourci vers le réglage. */
@Composable
private fun SourcePills(
    source: String,
    services: List<StreamingService>,
    streaming: StreamingUi?,
    hasOverview: Boolean,
    overviewLabel: String,
    selectedRequester: FocusRequester,
    onSelect: (String) -> Unit,
) {
    fun Modifier.selected(key: String) = if (key == source) focusRequester(selectedRequester) else this
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasOverview) {
            FilterPill(overviewLabel, selected = source == CatalogSources.ALL, onClick = { onSelect(CatalogSources.ALL) }, modifier = Modifier.selected(CatalogSources.ALL))
        }
        // Jellyfin seul, sans plateforme à ajouter : rien à choisir, pas de pilule.
        if (services.isNotEmpty() || streaming?.onOpenSettings != null) FilterPill(
            "Jellyfin",
            selected = source == CatalogSources.JELLYFIN,
            onClick = { onSelect(CatalogSources.JELLYFIN) },
            modifier = Modifier.selected(CatalogSources.JELLYFIN),
        )
        services.forEach { service ->
            FilterPill(service.label, selected = source == service.code, onClick = { onSelect(service.code) }, modifier = Modifier.selected(service.code))
        }
        if (services.isEmpty()) streaming?.onOpenSettings?.let { open -> FilterPill("Mes plateformes", selected = false, onClick = open, trailing = NimbeIcons.Add) }
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
        Load.Loading -> PosterGridSkeleton()
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
            PosterGrid(gridState) {
                items(items, key = { it.id }) { title ->
                    StreamingPosterCard(
                        title = title,
                        onClick = { ui.onOpenTitle(title) },
                        width = GRID_POSTER_WIDTH,
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
    // Les rangées arrivent une à une : d'un coup, elles figent le stick plus d'une seconde, et l'appui suivant
    // sur la télécommande, mis en attente, saute alors l'onglet. Au retour d'une fiche, la carte d'origine doit exister tout de suite.
    val revealed by produceState(if (restoreFocusId != null) Int.MAX_VALUE else 0) {
        while (value < MAX_ROWS) {
            delay(ROW_REVEAL_MS)
            value++
        }
    }
    PivotScroll(rowCardTop()) { Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenMargin).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Compté ici : ce bloc se recompose seul à chaque nouvelle rangée.
        var rowIndex = 0
        if (category == null && rowIndex++ < revealed) ContinueRow(kind, onPlayItem, onFocus, onFallback, state, restoreFocusId, onRestored)

        if (jellyfinUi?.link != null) {
            if (category == null && jellyfin.shelves.isNotEmpty()) {
                jellyfin.shelves.forEachIndexed { index, shelf ->
                    if (rowIndex++ < revealed) JellyfinRow(
                        title = shelf.title,
                        rowKey = shelf.key,
                        load = shelf.loadPage,
                        // Seule la première parle du serveur et a une carte « Tout voir » : le reste s'efface s'il n'a rien à montrer.
                        primary = index == 0,
                        jellyfin = jellyfin,
                        state = state,
                        category = category,
                        onOpenItem = onOpenItem,
                        onFocus = onFocus,
                        onFallback = onFallback,
                        restoreFocusId = restoreFocusId,
                        onRestored = onRestored,
                    )
                }
            } else if (rowIndex++ < revealed) {
                JellyfinRow(
                    title = jellyfin.rowTitle,
                    rowKey = jellyfin.pageKey,
                    load = jellyfin.loadPage,
                    primary = true,
                    jellyfin = jellyfin,
                    state = state,
                    category = category,
                    onOpenItem = onOpenItem,
                    onFocus = onFocus,
                    onFallback = onFallback,
                    restoreFocusId = restoreFocusId,
                    onRestored = onRestored,
                )
            }
        }

        services.forEach { service ->
            if (rowIndex++ < revealed) StreamingRow(
                state = state,
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
        if (services.size > 1 && rowIndex++ < revealed) {
            StreamingRow(
                state = state,
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
        // De la place sous la dernière rangée, pour qu'elle puisse monter à son tour.
        Spacer(Modifier.height(96.dp))
    } }
}

/**
 * Rangée Jellyfin de « Tout » ou « À la une ». [primary] : elle dit ce qui manque et finit par « Tout Jellyfin ».
 * Les autres n'apparaissent que si elles ont assez de titres pour valoir une rangée.
 */
@Composable
private fun JellyfinRow(
    title: String,
    rowKey: String,
    load: suspend JellyfinLibrary.(JellyfinAuth, Int, Int) -> ItemPage,
    primary: Boolean,
    jellyfin: JellyfinSection,
    state: CatalogState,
    category: Category?,
    onOpenItem: (MediaItem) -> Unit,
    onFocus: (CatalogFocus) -> Unit,
    onFallback: (CatalogFocus) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
) {
    val cacheKey = "$rowKey:0:$ROW_SIZE"
    val loader = rememberJellyfin(cacheKey, cacheKey) { load(this, it, 0, ROW_SIZE) }
    val items = loader.value?.items.orEmpty()
    if (!primary && loader.state !is Load.Loading && items.size < SHELF_MIN_ITEMS) return
    if (primary) LaunchedEffect(items.firstOrNull()?.id) { items.firstOrNull()?.let { onFallback(CatalogFocus.Jellyfin(it, jellyfin.kicker(it))) } }
    val restore = restoreFocusId?.takeIf { state.focusedRow == rowKey }
    CatalogRow(
        title = title,
        loading = loader.state is Load.Loading,
        message = when {
            loader.state is Load.Failed -> "Jellyfin ne répond pas : ta bibliothèque reviendra avec le serveur."
            items.isEmpty() -> if (category != null) "Rien en « ${category.label} » sur Jellyfin." else jellyfin.emptyDetail
            else -> null
        },
        keys = items.map { it.id },
        restoreFocusId = restore,
    ) {
        items(items, key = { "$rowKey:${it.id}" }) { item ->
            PosterCard(
                item = item,
                onClick = { onOpenItem(item) },
                width = POSTER_WIDTH,
                onFocus = {
                    state.focusedRow = rowKey
                    onFocus(CatalogFocus.Jellyfin(item, jellyfin.kicker(item)))
                },
                restoreFocus = item.id == restore,
                onRestored = onRestored,
            )
        }
        if (primary) item(key = "$rowKey:all") { SeeAllCard("Tout Jellyfin", onClick = { state.source = CatalogSources.JELLYFIN }) }
    }
}

/** « Continuer à regarder » de l'onglet : reprises Jellyfin et titres ouverts sur les plateformes, de ce type seulement. */
@Composable
private fun ContinueRow(
    kind: TitleKind,
    onPlayItem: (MediaItem) -> Unit,
    onFocus: (CatalogFocus) -> Unit,
    onFallback: (CatalogFocus) -> Unit,
    state: CatalogState,
    restoreFocusId: String?,
    onRestored: () -> Unit,
) {
    val ui = LocalStreaming.current ?: return
    val jellyfin = rememberJellyfin("continue") { continueWatching(it) }
    val entries = ContinueEntry.merge(jellyfin.value.orEmpty(), ui.history, kind, sameAs = { ui.inJellyfin(it.toTitle()) })
    if (entries.isEmpty()) return
    LaunchedEffect(entries.first().key) { onFallback(entries.first().focus(ui)) }
    val restore = restoreFocusId?.takeIf { state.focusedRow == CONTINUE_ROW }
    val focusHere: (CatalogFocus) -> Unit = {
        state.focusedRow = CONTINUE_ROW
        onFocus(it)
    }
    CatalogRow(title = "Continuer à regarder", message = null, keys = entries.map { it.id }, restoreFocusId = restore) {
        items(entries, key = { it.key }) { entry -> ContinueCard(entry, onPlayItem, focusHere, restore, onRestored, width = CONTINUE_WIDTH) }
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
    /** Options d'une carte Jellyfin (OK maintenu) ; sans lui, pas de menu. */
    onJellyfinMenu: ((MediaItem, FocusRequester) -> Unit)? = null,
    onWatchNextMenu: ((WatchNextProgram, FocusRequester) -> Unit)? = null,
    /** Plus d'internet : les plateformes sont grisées, leur lien ne mènerait à rien. */
    offline: Boolean = false,
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
            onMenu = onJellyfinMenu?.let { menu -> { requester -> menu(entry.item, requester) } },
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
                dimmed = offline,
            )
        }
        is ContinueEntry.WatchNext -> WatchNextCard(
            entry.program,
            onFocus = { onFocus(CatalogFocus.WatchNext(entry.program)) },
            width = width,
            dimmed = offline,
            onMenu = onWatchNextMenu?.let { menu -> { requester -> menu(entry.program, requester) } },
        )
    }
}

const val CONTINUE_KICKER = "REPRENDRE LÀ OÙ TU T'ES ARRÊTÉ"

/** Le bandeau du haut pour une carte de « Continuer à regarder ». */
fun ContinueEntry.focus(ui: StreamingUi?): CatalogFocus = when (this) {
    is ContinueEntry.Jellyfin -> CatalogFocus.Jellyfin(item, CONTINUE_KICKER)
    is ContinueEntry.Streaming -> CatalogFocus.Streaming(open.toTitle(), "REPRENDRE SUR ${open.streamingService?.label?.uppercase().orEmpty()}")
    is ContinueEntry.WatchNext -> CatalogFocus.WatchNext(program)
}

/** Identifiant de la carte, celui que l'écran cherche pour lui rendre le focus au retour d'une fiche. */
val ContinueEntry.id: String
    get() = when (this) {
        is ContinueEntry.Jellyfin -> item.id
        is ContinueEntry.Streaming -> open.titleId
        is ContinueEntry.WatchNext -> "watchnext:${program.id}"
    }

/** Rangée d'une plateforme (ou de toutes) : titres populaires ou tendances. */
@Composable
private fun StreamingRow(
    state: CatalogState,
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
    val restore = restoreFocusId?.takeIf { state.focusedRow == key }
    CatalogRow(
        title = title,
        loading = loader.state is Load.Loading,
        message = when {
            loader.state is Load.Failed -> "Catalogue indisponible : le stick n'arrive pas à joindre JustWatch."
            items.isEmpty() -> emptyText
            else -> null
        },
        keys = items.map { it.id },
        restoreFocusId = restore,
    ) {
        items(items, key = { "${service?.code}:${it.id}" }) { item ->
            StreamingPosterCard(
                title = item,
                onClick = { ui.onOpenTitle(item) },
                width = POSTER_WIDTH,
                // Le badge ne sert que dans la rangée qui mêle les plateformes.
                service = if (service == null) item.services.firstOrNull { it in ui.services } else null,
                inJellyfin = ui.inJellyfin(item) != null,
                onFocus = {
                    state.focusedRow = key
                    onFocus(CatalogFocus.Streaming(item, null))
                },
                restoreFocus = item.id == restore,
                onRestored = onRestored,
            )
        }
        onSeeAll?.let { open -> item(key = "${service?.code}:all") { SeeAllCard("Tout ${service?.label.orEmpty()}", onClick = open) } }
    }
}

/**
 * Une rangée titrée : soit ses cartes, soit leur forme grise pendant le chargement, soit une phrase (vide, erreur)
 * à la même place, pour que l'écran ne saute pas. Au retour d'une fiche, la rangée défile jusqu'à la carte d'origine.
 */
@Composable
private fun CatalogRow(
    title: String,
    message: String?,
    keys: List<String>,
    restoreFocusId: String?,
    loading: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    if (loading) {
        Column {
            RowTitle(title)
            Row(Modifier.padding(vertical = RowCardPadding), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(SKELETON_POSTERS) { SkeletonBlock(Modifier.width(POSTER_WIDTH).aspectRatio(2f / 3f), radius = 10.dp) }
            }
        }
        return
    }
    if (message != null) {
        Column {
            RowTitle(title)
            RowMessage(message)
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(restoreFocusId, keys) {
        val index = keys.indexOf(restoreFocusId)
        if (index >= 0) listState.scrollToItem(index)
    }
    ContentRow(title, state = listState, content = content)
}

/** La forme de [PosterGrid] pendant son chargement. */
@Composable
fun PosterGridSkeleton() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScreenMargin).padding(top = RowCardPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(GRID_COLUMNS) { SkeletonBlock(Modifier.weight(1f).aspectRatio(2f / 3f), radius = 10.dp) }
    }
}

/**
 * Grille d'affiches des onglets Films et Séries : six colonnes, assez grandes pour se lire de loin.
 * La ligne focalisée se cale en haut de la grille et la suivante dépasse dessous, pour qu'on sache qu'il y a une suite.
 */
@Composable
fun PosterGrid(state: LazyGridState, content: LazyGridScope.() -> Unit) {
    PivotScroll(RowCardPadding) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = state,
            contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = RowCardPadding, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
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
            containerColor = Lune.copy(alpha = 0.08f),
            contentColor = Lune,
            focusedContainerColor = Lune,
            focusedContentColor = Nuit,
            pressedContainerColor = Lune,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NimbeIcon(NimbeIcons.ChevronRight, size = 32.dp)
            Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
    }
}

private fun kindWord(kind: TitleKind) = if (kind == TitleKind.Movie) "films" else "séries"

private val POSTER_WIDTH = 112.dp
private const val GRID_COLUMNS = 6
val GRID_POSTER_WIDTH = 116.dp
private val CATALOG_HERO_HEIGHT = 156.dp
private const val ROW_SIZE = 20
private const val SHELF_MIN_ITEMS = 4
private const val MAX_ROWS = 20
private const val ROW_REVEAL_MS = 120L
private val CONTINUE_WIDTH = 164.dp
private const val SKELETON_POSTERS = 7
private const val CONTINUE_ROW = "continue"
