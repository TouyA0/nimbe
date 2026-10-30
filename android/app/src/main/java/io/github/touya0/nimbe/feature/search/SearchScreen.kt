package io.github.touya0.nimbe.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.ItemKind
import io.github.touya0.nimbe.data.nextcloud.NextcloudAuth
import io.github.touya0.nimbe.data.search.SearchEngine
import io.github.touya0.nimbe.data.search.SearchHit
import io.github.touya0.nimbe.data.search.SearchProvider
import io.github.touya0.nimbe.data.search.SearchResults
import io.github.touya0.nimbe.data.search.SearchSource
import io.github.touya0.nimbe.data.search.SearchText
import io.github.touya0.nimbe.data.search.SourceState
import io.github.touya0.nimbe.data.streaming.JustWatchImages
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.feature.apps.tileColor
import io.github.touya0.nimbe.feature.gallery.NextcloudPreview
import io.github.touya0.nimbe.feature.gallery.PREVIEW_HEIGHT_GRID
import io.github.touya0.nimbe.feature.gallery.PREVIEW_WIDTH
import io.github.touya0.nimbe.feature.media.JellyfinImage
import io.github.touya0.nimbe.feature.media.JellyfinPurple
import io.github.touya0.nimbe.feature.media.SourceBadge
import io.github.touya0.nimbe.feature.streaming.StreamingImage
import io.github.touya0.nimbe.ui.components.NimbeIcon
import io.github.touya0.nimbe.ui.components.NimbeIcons
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.Spinner
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Ce qui survit à l'ouverture d'une fiche depuis les résultats : le texte, la position, le dernier résultat ouvert. */
@Stable
class SearchSession {
    var query by mutableStateOf("")
    var results by mutableStateOf(SearchResults())
    val list = LazyListState()
    var restoreKey: String? = null
}

/** Ce que la TV peut faire d'un résultat de galerie : l'aperçu demande le compte du profil. */
class SearchActions(
    val providers: List<SearchProvider>,
    /** Recherches récentes ; null pour l'invité, qui n'en a pas. */
    val history: List<String>?,
    val galleryAuth: NextcloudAuth?,
    val onOpen: (SearchHit, List<SearchHit>) -> Unit,
    val onClearHistory: () -> Unit,
    val onClose: () -> Unit,
)

/** Recherche globale : clavier et recherches récentes à gauche, résultats groupés à droite. */
@Composable
fun SearchScreen(session: SearchSession, actions: SearchActions) {
    BackHandler(onBack = actions.onClose)
    var dictating by remember { mutableStateOf(false) }
    val providers by rememberUpdatedState(actions.providers)

    // 300 ms après la dernière touche ; une nouvelle touche annule la recherche en cours.
    LaunchedEffect(session) {
        snapshotFlow { session.query }.collectLatest { query ->
            if (query.isBlank()) {
                session.results = SearchResults()
                return@collectLatest
            }
            if (session.results.query != query) {
                delay(DEBOUNCE_MS)
                SearchEngine.search(query.trim(), providers).collect { session.results = it.copy(query = query) }
            }
        }
    }
    if (dictating) {
        DictationScreen(
            onResult = {
                session.query = it
                dictating = false
            },
            onClose = { dictating = false },
        )
        return
    }

    val firstKey = remember { FocusRequester() }
    // Au retour d'une fiche, le résultat ouvert reprend le focus (ResultCard) ; sinon le clavier.
    LaunchedEffect(Unit) { if (session.restoreKey == null) runCatching { firstKey.requestFocus() } }
    LaunchedEffect(Unit) {
        if (session.restoreKey != null) {
            delay(RESTORE_TIMEOUT_MS)
            if (session.restoreKey != null) {
                session.restoreKey = null
                runCatching { firstKey.requestFocus() }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Nuit).padding(horizontal = 48.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        QueryBar(session.query)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.width(380.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SearchKeyboard(
                    firstKey,
                    onType = { session.query += it },
                    onDelete = { session.query = session.query.dropLast(1) },
                    onClear = { session.query = "" },
                    onDictate = { dictating = true },
                )
                actions.history?.takeIf { it.isNotEmpty() }?.let { history ->
                    RecentSearches(history, onPick = { session.query = it }, onClear = actions.onClearHistory)
                }
            }
            Results(session, actions, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QueryBar(query: String) {
    Row(
        Modifier.fillMaxWidth().background(Panneau, NimbeFocus.PillShape).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NimbeIcon(NimbeIcons.Search, size = 24.dp)
        Text(
            query.ifEmpty { "Rechercher partout" },
            style = MaterialTheme.typography.headlineSmall,
            color = if (query.isEmpty()) LuneAttenuee else Lune,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("Films, séries, plateformes, galerie, applis", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RecentSearches(history: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recherches récentes", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            history.forEach { PillButton(it, { onPick(it) }) }
            PillButton("Effacer", onClear)
        }
    }
}

@Composable
private fun Results(session: SearchSession, actions: SearchActions, modifier: Modifier) {
    val results = session.results
    when {
        session.query.isBlank() -> Hint("Tape ou dicte ce que tu cherches : un film, une série, une appli, une photo.", modifier)
        results.sources.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
        results.empty -> Hint(
            if (results.failed.isEmpty()) {
                "Rien trouvé pour « ${session.query} »."
            } else {
                "Rien trouvé pour « ${session.query} ». ${results.failed.joinToString(", ") { it.label }} : pas de réponse."
            },
            modifier,
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxHeight(),
            state = session.list,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
        ) {
            results.best?.let { best ->
                item(key = "best") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Meilleur résultat", style = MaterialTheme.typography.titleMedium)
                        ResultCard(best, session, actions, large = true, group = listOf(best))
                    }
                }
            }
            SearchSource.entries.forEach { source ->
                val hits = results.hits(source)
                val state = results.sources[source]
                if (hits.isNotEmpty()) {
                    item(key = source.name) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(source.label + " · " + hits.size, style = MaterialTheme.typography.titleMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 6.dp)) {
                                items(hits, key = { it.key }) { hit -> ResultCard(hit, session, actions, large = false, group = hits) }
                            }
                        }
                    }
                } else if (state is SourceState.Failed) {
                    item(key = source.name) {
                        Text("${source.label} : pas de réponse", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
                    }
                } else if (state is SourceState.Loading) {
                    item(key = source.name) {
                        Text("${source.label} : recherche…", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee, modifier = Modifier.width(480.dp))
    }
}

private fun detail(hit: SearchHit): String = when (hit) {
    is SearchHit.Media -> when (hit.item.kind) {
        ItemKind.Movie -> listOfNotNull("Film", hit.item.year?.toString()).joinToString(" · ")
        ItemKind.Series -> listOfNotNull("Série", hit.item.year?.toString()).joinToString(" · ")
        ItemKind.Episode -> listOfNotNull(hit.item.seriesName, hit.item.episodeCode).joinToString(" · ")
        else -> "Jellyfin"
    }
    is SearchHit.Streaming -> listOfNotNull(
        if (hit.value.kind == TitleKind.Movie) "Film" else "Série",
        hit.value.year?.toString(),
        hit.value.services.firstOrNull()?.label,
    ).joinToString(" · ")
    is SearchHit.App -> "Appli"
    is SearchHit.Photo -> if (hit.item.isVideo) "Vidéo" else "Photo"
}

private fun badge(hit: SearchHit): String = when (hit) {
    is SearchHit.Media -> "JELLYFIN"
    is SearchHit.Streaming -> hit.value.services.firstOrNull()?.badge ?: "PLATEFORME"
    is SearchHit.App -> "APPLI"
    is SearchHit.Photo -> "GALERIE"
}

@Composable
private fun ResultCard(hit: SearchHit, session: SearchSession, actions: SearchActions, large: Boolean, group: List<SearchHit>) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (session.restoreKey == hit.key) {
        LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
            session.restoreKey = null
        }
    }
    val imageSize: Dp = if (large) 96.dp else 56.dp
    Surface(
        onClick = {
            session.restoreKey = hit.key
            actions.onOpen(hit, group)
        },
        modifier = Modifier
            .then(if (large) Modifier.fillMaxWidth() else Modifier.width(280.dp))
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Panneau,
            contentColor = Lune,
            focusedContainerColor = PanneauClair,
            focusedContentColor = Lune,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(imageSize).clip(RoundedCornerShape(8.dp)).background(PanneauClair), contentAlignment = Alignment.Center) {
                HitImage(hit, imageSize, actions.galleryAuth)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (large) SourceBadge(badge(hit), color = JellyfinPurple)
                Text(
                    highlighted(hit.title, session.query),
                    style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
                    maxLines = if (large) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(detail(hit), style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun highlighted(title: String, query: String) = buildAnnotatedString {
    val range = SearchText.highlight(title, query)
    if (range == null) {
        append(title)
    } else {
        append(title.substring(0, range.first))
        withStyle(SpanStyle(color = Focus)) { append(title.substring(range.first, range.last + 1)) }
        append(title.substring(range.last + 1))
    }
}

@Composable
private fun HitImage(hit: SearchHit, size: Dp, galleryAuth: NextcloudAuth?) {
    val fill = Modifier.fillMaxSize()
    when (hit) {
        is SearchHit.Media -> JellyfinImage(hit.item.primary ?: hit.item.thumb ?: hit.item.seriesPrimary, width = size, height = size, modifier = fill)
        is SearchHit.Streaming -> StreamingImage(JustWatchImages.poster(hit.value.poster), fill)
        is SearchHit.App -> {
            val image = hit.app.icon ?: hit.app.banner
            if (image != null) {
                Image(image, contentDescription = null, modifier = fill.padding(6.dp), contentScale = ContentScale.Fit)
            } else {
                Box(fill.background(tileColor(hit.app.packageName)), contentAlignment = Alignment.Center) {
                    Text(hit.app.label.take(1), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        is SearchHit.Photo -> galleryAuth?.let { NextcloudPreview(it, hit.item, PREVIEW_WIDTH, PREVIEW_HEIGHT_GRID, fill, showNameWithoutPreview = false) }
    }
}

private const val DEBOUNCE_MS = 300L
private const val RESTORE_TIMEOUT_MS = 800L
