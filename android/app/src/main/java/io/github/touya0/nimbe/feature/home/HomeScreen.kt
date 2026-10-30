package io.github.touya0.nimbe.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import io.github.touya0.nimbe.feature.apps.ActionCard
import io.github.touya0.nimbe.feature.apps.AppCard
import io.github.touya0.nimbe.feature.media.FadingBackdrop
import io.github.touya0.nimbe.feature.media.FocusHero
import io.github.touya0.nimbe.feature.media.rememberWatchNext
import io.github.touya0.nimbe.feature.media.Kicker
import io.github.touya0.nimbe.shell.HeaderHeight
import io.github.touya0.nimbe.ui.components.ContentRow
import io.github.touya0.nimbe.ui.components.NimbeIcons
import io.github.touya0.nimbe.ui.components.PivotScroll
import io.github.touya0.nimbe.ui.components.RowMessage
import io.github.touya0.nimbe.ui.components.RowTitle
import io.github.touya0.nimbe.ui.components.ScreenMargin
import io.github.touya0.nimbe.ui.components.rowCardTop
import io.github.touya0.nimbe.ui.theme.LocalDisplay
import io.github.touya0.nimbe.ui.theme.Nuit
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.core.network.ConnectivityStatus
import io.github.touya0.nimbe.core.network.InternetApps
import io.github.touya0.nimbe.core.network.NetworkState
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.layout.HomeRow
import io.github.touya0.nimbe.feature.apps.ActionTile
import io.github.touya0.nimbe.feature.apps.AppTile
import io.github.touya0.nimbe.feature.apps.InstalledApp
import io.github.touya0.nimbe.feature.media.Backdrop
import io.github.touya0.nimbe.feature.media.CatalogFocus
import io.github.touya0.nimbe.feature.media.ContinueCard
import io.github.touya0.nimbe.feature.media.ContinueEntry
import io.github.touya0.nimbe.feature.media.focus
import io.github.touya0.nimbe.feature.media.id
import io.github.touya0.nimbe.feature.streaming.LocalStreaming
import io.github.touya0.nimbe.feature.streaming.StreamingBackdrop
import io.github.touya0.nimbe.feature.streaming.StreamingHero
import androidx.compose.runtime.LaunchedEffect
import io.github.touya0.nimbe.feature.media.Hero
import io.github.touya0.nimbe.feature.media.JellyfinUi
import io.github.touya0.nimbe.feature.media.Load
import io.github.touya0.nimbe.feature.media.LocalJellyfin
import io.github.touya0.nimbe.feature.media.unreachableDetail
import io.github.touya0.nimbe.feature.media.MediaText
import io.github.touya0.nimbe.feature.media.PosterCard
import io.github.touya0.nimbe.feature.media.ScrollToRestore
import io.github.touya0.nimbe.feature.media.rememberJellyfin
import io.github.touya0.nimbe.ui.components.KeyHints
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.bleed
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Panneau
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Accueil d'un profil Invité ou Enfant, en haut de l'écran tant qu'aucune carte n'a le focus. */
data class HomeWelcome(val kicker: String, val title: String, val text: String)

/**
 * Accueil : en haut le contenu focalisé, puis les rangées choisies par le profil, dans son ordre
 * ([rows] : Continuer à regarder, Mes applis, Ajouté récemment sur Jellyfin). Sans Jellyfin, il reste les applis.
 * « Continuer à regarder » reprend aussi les titres ouverts sur les plateformes (leur carte ouvre la fiche).
 * [dock] vaut null pendant le chargement. [restoreFocusId] : la carte à refocaliser au retour d'une fiche.
 * Une carte de « Continuer à regarder » lance la lecture ; une affiche ouvre la fiche.
 */
@Composable
fun HomeScreen(
    rows: List<HomeRow>,
    /** Profils Invité et Enfant : le haut de l'écran les accueille, tant qu'aucune carte n'a le focus. */
    welcome: HomeWelcome?,
    dock: List<InstalledApp>?,
    onOpen: (InstalledApp) -> Unit,
    onMenu: (InstalledApp, FocusRequester) -> Unit,
    onAllApps: () -> Unit,
    /** Applis masquées pour ce profil : leurs contenus en cours non plus. */
    hiddenApps: Set<String> = emptySet(),
    connectivity: ConnectivityStatus,
    onNetworkSettings: () -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onPlayItem: (MediaItem) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
    /** Rangée « Souvenirs » de la galerie Nextcloud du profil ; elle ne dessine rien s'il n'y en a pas. */
    memories: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val jellyfin = LocalJellyfin.current
    val streaming = LocalStreaming.current
    val continueRow = rememberJellyfin("continue") { continueWatching(it) }
    val latestRow = rememberJellyfin("latest") { latest(it) }
    // Une rangée masquée ne nourrit pas non plus le haut de l'écran.
    // « Continuer à regarder » mêle les reprises Jellyfin, les titres ouverts sur les plateformes et Watch Next.
    val watchNext = rememberWatchNext(enabled = HomeRow.Continue in rows, hidden = hiddenApps)
    val resume = if (HomeRow.Continue in rows) {
        ContinueEntry.merge(continueRow.value.orEmpty(), streaming?.history.orEmpty(), sameAs = { streaming?.inJellyfin(it.toTitle()) }, watchNext = watchNext)
    } else {
        emptyList()
    }
    val latest = if (HomeRow.Latest in rows) latestRow.value.orEmpty() else emptyList()

    var focusedPick by remember { mutableStateOf<CatalogFocus?>(null) }
    // Avant tout focus sur une carte : la reprise la plus récente, sinon le dernier ajout.
    // Un contenu qui a quitté les rangées (fini pendant la lecture, par exemple) ne reste pas en haut.
    val shownIds = resume.map { it.id } + latest.map { it.id }
    val hero = focusedPick?.takeIf { pick -> pick.id in shownIds }
        ?: resume.firstOrNull()?.focus(streaming)
        ?: latest.firstOrNull()?.let { CatalogFocus.Jellyfin(it, MediaText.added(it.dateCreated)) }

    Box(modifier.fillMaxSize()) {
        if (!connectivity.offline && jellyfin?.down != true) {
            when (hero) {
                is CatalogFocus.Jellyfin -> Backdrop(hero.item.backdrop)
                is CatalogFocus.Streaming -> StreamingBackdrop(hero.title.backdrop)
                is CatalogFocus.WatchNext -> FadingBackdrop(hero.program.image)
                null -> Unit
            }
        }
        Column(Modifier.fillMaxSize().padding(top = HeaderHeight)) {
            Box(
                Modifier.padding(horizontal = ScreenMargin).padding(top = 10.dp).height(HERO_HEIGHT),
                contentAlignment = Alignment.TopStart,
            ) {
                when {
                    connectivity.offline -> OfflineBanner(connectivity, onNetworkSettings)
                    jellyfin?.down == true -> ServerDownBanner(jellyfin)
                    welcome != null && focusedPick == null -> Welcome(welcome)
                    else -> FocusHero(hero, overviewLines = 2) {
                        Welcome(HomeWelcome("NIMBE", "Bienvenue", "Tes films, tes séries, tes photos et tes applis, réunis ici."))
                    }
                }
            }

            // La rangée focalisée vient se poser juste sous le haut de l'écran, titre compris :
            // on garde l'œil au même endroit en descendant, comme sur les applis de streaming.
            PivotScroll(rowCardTop()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ScreenMargin),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rows.forEach { row ->
                        // Une clé par rangée : déplacée, elle garde sa position de défilement.
                        key(row.key) {
                            when (row) {
                                HomeRow.Continue -> {
                                    if (resume.isNotEmpty()) {
                                        val state = rememberLazyListState()
                                        LaunchedEffect(restoreFocusId, resume) {
                                            val index = resume.indexOfFirst { it.id == restoreFocusId }
                                            if (index >= 0) state.scrollToItem(index)
                                        }
                                        ContentRow("Continuer à regarder", state = state) {
                                            items(resume, key = { "continue:${it.key}" }) { entry ->
                                                ContinueCard(
                                                    entry = entry,
                                                    onPlayItem = onPlayItem,
                                                    onFocus = { focusedPick = it },
                                                    restoreFocusId = restoreFocusId,
                                                    onRestored = onRestored,
                                                )
                                            }
                                        }
                                    } else if (continueRow.state is Load.Failed && jellyfin?.down != true) {
                                        RowMessage("Jellyfin ne répond pas : tes films reviendront avec le serveur. Tes applis restent disponibles.")
                                    }
                                }
                                HomeRow.Apps -> {
                                    // La rangée n'apparaît qu'une fois la liste chargée : sinon, l'ajout des cartes devant
                                    // « Toutes les applis » décale la rangée et la première appli sort de l'écran.
                                    if (dock == null) {
                                        RowTitle("Mes applis")
                                        RowMessage("Chargement…")
                                    } else {
                                        ContentRow("Mes applis") {
                                            items(dock, key = { it.packageName }) { app ->
                                                AppCard(
                                                    app = app,
                                                    onClick = { onOpen(app) },
                                                    onMenu = { requester -> onMenu(app, requester) },
                                                    offline = connectivity.offline && InternetApps.needsInternet(app.packageName),
                                                    modifier = Modifier.width(APP_CARD_WIDTH).animateItem(),
                                                )
                                            }
                                            item(key = "toutes") {
                                                ActionCard(NimbeIcons.Apps, "Toutes les applis", onClick = onAllApps, modifier = Modifier.width(APP_CARD_WIDTH))
                                            }
                                        }
                                    }
                                }
                                HomeRow.Latest -> {
                                    if (latest.isNotEmpty()) {
                                        val state = rememberLazyListState()
                                        ScrollToRestore(state, latest, restoreFocusId)
                                        ContentRow("Ajouté récemment", state = state) {
                                            items(latest, key = { "latest:${it.id}" }) { item ->
                                                PosterCard(
                                                    item = item,
                                                    onClick = { onOpenItem(item) },
                                                    width = POSTER_WIDTH,
                                                    onFocus = { focusedPick = CatalogFocus.Jellyfin(item, MediaText.added(item.dateCreated)) },
                                                    restoreFocus = item.id == restoreFocusId && resume.none { it.id == restoreFocusId },
                                                    onRestored = onRestored,
                                                )
                                            }
                                        }
                                    }
                                }
                                HomeRow.Memories -> memories()
                            }
                        }
                    }
                    // De la place sous la dernière rangée, pour qu'elle puisse monter à son tour sous le haut de l'écran.
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
        BottomHints(Modifier.align(Alignment.BottomCenter))
    }
}

/** Rappel des touches en bas de l'écran, sur un voile : il ne prend pas de place aux rangées. */
@Composable
private fun BottomHints(modifier: Modifier = Modifier) {
    if (!LocalDisplay.current.keyHints) return
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Nuit.copy(alpha = 0.92f))))
            .padding(horizontal = ScreenMargin, vertical = 14.dp)
            .padding(top = 18.dp),
    ) {
        KeyHints("OK" to "ouvrir", "OK maintenu" to "options")
    }
}

private val HERO_HEIGHT = 176.dp
private val POSTER_WIDTH = 112.dp
private val APP_CARD_WIDTH = 176.dp

@Composable
private fun Welcome(welcome: HomeWelcome) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        Kicker(welcome.kicker)
        Text(welcome.title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 40.sp))
        Text(
            welcome.text,
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
            modifier = Modifier.width(620.dp),
        )
    }
}

/** Haut de l'accueil quand Jellyfin ne répond plus : ce qui est en pause, ce qui marche, le prochain essai. */
@Composable
private fun ServerDownBanner(jellyfin: JellyfinUi) {
    val color = if (jellyfin.piUp) Focus else Alerte
    Column(
        Modifier
            .width(620.dp)
            .background(Panneau.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("!", style = MaterialTheme.typography.titleMedium, color = color)
            }
            Text(if (jellyfin.piDown) "Ton Raspberry Pi ne répond pas" else "Jellyfin ne répond pas", style = MaterialTheme.typography.headlineSmall)
        }
        Text(unreachableDetail(jellyfin, short = true), style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee, maxLines = 2)
        PillButton("Réessayer maintenant", onClick = jellyfin.onRetry)
    }
}

/** Haut de l'accueil pendant une coupure : ce qui marche encore, et depuis quand ça ne marche plus. */
@Composable
private fun OfflineBanner(connectivity: ConnectivityStatus, onNetworkSettings: () -> Unit) {
    val since = connectivity.since?.let { " depuis ${formatHour(it)}" }.orEmpty()
    val noNetwork = connectivity.state == NetworkState.NoNetwork
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (noNetwork) "Le stick n'est plus connecté au réseau$since" else "La box n'a plus internet$since",
            style = MaterialTheme.typography.titleSmall,
            color = Focus,
        )
        Text(
            text = if (noNetwork) "Plus de connexion" else "Ce qui est chez toi marche toujours",
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = if (noNetwork) {
                "Vérifie le Wi-Fi ou le câble du stick. Tes applis restent accessibles, " +
                    "mais la plupart ont besoin du réseau."
            } else {
                "Les applis du réseau de la maison restent disponibles. La TV et les plateformes " +
                    "comme Netflix ou YouTube reviendront avec la connexion."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
            modifier = Modifier.width(620.dp),
        )
        if (noNetwork) {
            PillButton("Ouvrir les réglages réseau", onClick = onNetworkSettings, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private val HourFormat = DateTimeFormatter.ofPattern("H 'h' mm")

private fun formatHour(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(HourFormat)
