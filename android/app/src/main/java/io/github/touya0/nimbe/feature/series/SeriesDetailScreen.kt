package io.github.touya0.nimbe.feature.series

import io.github.touya0.nimbe.feature.media.HeroWidth
import io.github.touya0.nimbe.feature.media.Kicker
import io.github.touya0.nimbe.feature.media.Overview
import io.github.touya0.nimbe.ui.components.ContentRow
import io.github.touya0.nimbe.ui.components.NimbeIcons
import io.github.touya0.nimbe.ui.components.PivotScroll
import io.github.touya0.nimbe.ui.components.RowMessage
import io.github.touya0.nimbe.ui.components.RowTitle
import io.github.touya0.nimbe.ui.components.ScreenMargin
import io.github.touya0.nimbe.ui.components.rowCardTop
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.feature.streaming.AlsoOnButtons
import io.github.touya0.nimbe.feature.streaming.rememberAlsoOn
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.bleed
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit

/**
 * Page d'une série : résumé, « Reprendre » sur le prochain épisode, saisons et épisodes.
 * La saison affichée est celle demandée, sinon celle du prochain épisode, sinon la première.
 * « Reprendre » lance directement ([onPlay]) ; un épisode choisi passe par « Reprendre ou recommencer » ([onRequestPlay]).
 */
@Composable
fun SeriesDetailScreen(
    destination: MediaDestination.Series,
    onPlay: (MediaItem, Boolean) -> Unit,
    onRequestPlay: (MediaItem) -> Unit,
    onClose: () -> Unit,
) {
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
        LoadStates(seriesLoad, "cette série", takeFocus = true) { series ->
            // Saisons et épisodes viennent se poser sous le haut de l'écran quand on y descend.
            PivotScroll(rowCardTop()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenMargin)) {
                    Column(Modifier.padding(top = 64.dp).width(HeroWidth), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val unplayed = series.unplayedCount ?: 0
                        Kicker(
                            when {
                                unplayed == 1 -> "1 ÉPISODE À VOIR"
                                unplayed > 1 -> "$unplayed ÉPISODES À VOIR"
                                else -> "SÉRIE"
                            },
                        )
                        Text(series.name.uppercase(), style = titleStyle(series.name), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        MetaLine(series)
                        series.overview?.let { Overview(it, lines = 3) }
                        val alsoOn = rememberAlsoOn(series, TitleKind.Show)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (next != null) {
                                val code = next.episodeCode ?: next.name
                                PillButton(
                                    text = if (next.resumable) "Reprendre $code" else "Regarder $code",
                                    icon = NimbeIcons.Play,
                                    onClick = { onPlay(next, false) },
                                    modifier = Modifier.focusRequester(playRequester),
                                )
                            }
                            // La même série sur une plateforme du profil.
                            AlsoOnButtons(alsoOn)
                        }
                    }

                    // Les saisons, puis les épisodes de la saison choisie : une seule saison, pas de choix à faire.
                    if (seasons.size > 1) {
                        Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            seasons.forEachIndexed { index, season ->
                                FilterPill(
                                    text = season.name,
                                    selected = season.id == seasonId,
                                    onClick = { chosenSeason = season.id },
                                    modifier = if (index == 0) Modifier.focusRequester(seasonRequester) else Modifier,
                                )
                            }
                        }
                    }
                    val episodesTitle = seasons.firstOrNull { it.id == seasonId }?.name?.let { "Épisodes · $it" } ?: "Épisodes"
                    when {
                        episodesLoad.value == null -> Column(Modifier.padding(top = 20.dp)) {
                            RowTitle(episodesTitle)
                            RowMessage("Chargement des épisodes…")
                        }
                        episodes.isEmpty() -> Column(Modifier.padding(top = 20.dp)) {
                            RowTitle(episodesTitle)
                            RowMessage("Aucun épisode dans cette saison.")
                        }
                        else -> {
                            val state = rememberLazyListState()
                            ScrollToRestore(state, episodes, focusEpisode)
                            ContentRow(
                                episodesTitle,
                                modifier = Modifier.padding(top = 20.dp).then(if (seasons.size <= 1) Modifier.focusRequester(seasonRequester).focusGroup() else Modifier),
                                state = state,
                                spacing = 18.dp,
                                count = episodes.size,
                            ) {
                                items(episodes, key = { it.id }) { episode ->
                                    WideCard(
                                        item = episode,
                                        label = listOfNotNull(episode.episodeNumber?.let { "$it." }, episode.name).joinToString(" "),
                                        detail = MediaText.progressLine(episode) ?: episode.overview,
                                        onClick = { onRequestPlay(episode) },
                                        width = 224.dp,
                                        restoreFocus = episode.id == focusEpisode,
                                        onRestored = { focusEpisode = null },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}
