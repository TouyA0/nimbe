package io.github.touya0.nimbe.feature.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.feature.media.Backdrop
import io.github.touya0.nimbe.feature.media.FilterPill
import io.github.touya0.nimbe.feature.media.Load
import io.github.touya0.nimbe.feature.media.LoadStates
import io.github.touya0.nimbe.feature.media.MediaDestination
import io.github.touya0.nimbe.feature.media.MediaText
import io.github.touya0.nimbe.feature.media.MetaLine
import io.github.touya0.nimbe.feature.media.ScrollToRestore
import io.github.touya0.nimbe.feature.media.SourceBadge
import io.github.touya0.nimbe.feature.media.WideCard
import io.github.touya0.nimbe.feature.media.rememberJellyfin
import io.github.touya0.nimbe.feature.media.titleStyle
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.bleed
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit

/**
 * Page d'une série : résumé, « Reprendre » sur le prochain épisode, saisons et épisodes.
 * La saison affichée est celle demandée, sinon celle du prochain épisode, sinon la première.
 */
@Composable
fun SeriesDetailScreen(destination: MediaDestination.Series, onPlay: (MediaItem, Boolean) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val id = destination.seriesId
    val seriesLoad = rememberJellyfin("item:$id", id) { item(it, id) }
    val seasonsLoad = rememberJellyfin("seasons:$id", id) { seasons(it, id) }
    val nextLoad = rememberJellyfin("nextup:$id", id) { nextUp(it, id) }
    val next = nextLoad.value?.firstOrNull()
    val seasons = seasonsLoad.value.orEmpty()
    var chosenSeason by remember { mutableStateOf(destination.seasonId) }
    val seasonId = chosenSeason ?: next?.seasonId ?: seasons.firstOrNull()?.id
    val episodesLoad = rememberJellyfin(seasonId?.let { "episodes:$it" }, seasonId) { auth ->
        if (seasonId == null) emptyList() else episodes(auth, id, seasonId)
    }
    val episodes = episodesLoad.value.orEmpty()

    // Le focus va sur l'épisode d'où l'on vient, sinon sur « Reprendre », sinon sur la première saison.
    var focusEpisode by remember { mutableStateOf(destination.focusEpisodeId) }
    val playRequester = remember { FocusRequester() }
    val seasonRequester = remember { FocusRequester() }
    var focusPlaced by remember { mutableStateOf(focusEpisode != null) }
    LaunchedEffect(seriesLoad.value != null, nextLoad.state, seasons.isNotEmpty()) {
        if (focusPlaced || seriesLoad.value == null || nextLoad.state is Load.Loading) return@LaunchedEffect
        if (next == null && seasons.isEmpty()) return@LaunchedEffect
        focusPlaced = runCatching { (if (next != null) playRequester else seasonRequester).requestFocus() }.isSuccess
    }

    Box(Modifier.fillMaxSize().background(Nuit)) {
        Backdrop(seriesLoad.value?.backdrop)
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
            Text("‹  Séries", style = MaterialTheme.typography.titleSmall, color = LuneAttenuee)
            LoadStates(seriesLoad, "cette série", takeFocus = true) { series ->
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SourceBadge("JELLYFIN")
                        val unplayed = series.unplayedCount ?: 0
                        if (unplayed > 0) {
                            Text(
                                if (unplayed == 1) "1 ÉPISODE À VOIR" else "$unplayed ÉPISODES À VOIR",
                                style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.5.sp),
                                color = Focus,
                            )
                        }
                    }
                    Text(series.name.uppercase(), style = titleStyle(series.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MetaLine(series)
                    series.overview?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(540.dp))
                    }
                    if (next != null) {
                        val code = next.episodeCode ?: next.name
                        PillButton(
                            text = if (next.resumable) "▶  Reprendre $code" else "▶  Regarder $code",
                            onClick = { onPlay(next, false) },
                            modifier = Modifier.padding(top = 4.dp).focusRequester(playRequester),
                        )
                    }
                }

                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    seasons.forEachIndexed { index, season ->
                        FilterPill(
                            text = season.name,
                            selected = season.id == seasonId,
                            onClick = { chosenSeason = season.id },
                            modifier = if (index == 0) Modifier.focusRequester(seasonRequester) else Modifier,
                        )
                    }
                }

                when {
                    episodesLoad.value == null -> Text(
                        "Chargement des épisodes…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LuneAttenuee,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                    episodes.isEmpty() -> Text(
                        "Aucun épisode dans cette saison.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LuneAttenuee,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                    else -> {
                        val state = rememberLazyListState()
                        ScrollToRestore(state, episodes, focusEpisode)
                        LazyRow(
                            state = state,
                            modifier = Modifier.bleed(48.dp).height(190.dp),
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(episodes, key = { it.id }) { episode ->
                                WideCard(
                                    item = episode,
                                    label = episode.name,
                                    detail = MediaText.progressLine(episode),
                                    badge = episode.episodeNumber?.let { "É$it" },
                                    onClick = { onPlay(episode, false) },
                                    width = 200.dp,
                                    restoreFocus = episode.id == focusEpisode,
                                    onRestored = { focusEpisode = null },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
