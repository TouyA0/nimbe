package io.github.touya0.nimbe.feature.home

import androidx.compose.foundation.background
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
import io.github.touya0.nimbe.feature.media.Hero
import io.github.touya0.nimbe.feature.media.JellyfinUi
import io.github.touya0.nimbe.feature.media.Load
import io.github.touya0.nimbe.feature.media.LocalJellyfin
import io.github.touya0.nimbe.feature.media.unreachableDetail
import io.github.touya0.nimbe.feature.media.MediaText
import io.github.touya0.nimbe.feature.media.PosterCard
import io.github.touya0.nimbe.feature.media.ScrollToRestore
import io.github.touya0.nimbe.feature.media.WideCard
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

/** Ce que montre le haut de l'accueil : un contenu et son accroche (« REPRENDRE », « AJOUTÉ HIER »). */
private data class HeroPick(val item: MediaItem, val kicker: String?)

/**
 * Accueil : en haut le contenu focalisé, puis les rangées choisies par le profil, dans son ordre
 * ([rows] : Continuer à regarder, Mes applis, Ajouté récemment sur Jellyfin). Sans Jellyfin, il reste les applis.
 * [dock] vaut null pendant le chargement. [restoreFocusId] : la carte à refocaliser au retour d'une fiche.
 * Une carte de « Continuer à regarder » lance la lecture ; une affiche ouvre la fiche.
 */
@Composable
fun HomeScreen(
    rows: List<HomeRow>,
    /** Profil Invité : le haut de l'écran l'accueille et dit ce qui est ouvert, tant qu'aucune carte n'a le focus. */
    guest: Boolean,
    dock: List<InstalledApp>?,
    onOpen: (InstalledApp) -> Unit,
    onMenu: (InstalledApp, FocusRequester) -> Unit,
    onAllApps: () -> Unit,
    connectivity: ConnectivityStatus,
    onNetworkSettings: () -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onPlayItem: (MediaItem) -> Unit,
    restoreFocusId: String?,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jellyfin = LocalJellyfin.current
    val continueRow = rememberJellyfin("continue") { continueWatching(it) }
    val latestRow = rememberJellyfin("latest") { latest(it) }
    // Une rangée masquée ne nourrit pas non plus le haut de l'écran.
    val resume = if (HomeRow.Continue in rows) continueRow.value.orEmpty() else emptyList()
    val latest = if (HomeRow.Latest in rows) latestRow.value.orEmpty() else emptyList()

    var focusedPick by remember { mutableStateOf<HeroPick?>(null) }
    // Avant tout focus sur une carte : la reprise la plus récente, sinon le dernier ajout.
    // Un contenu qui a quitté les rangées (fini pendant la lecture, par exemple) ne reste pas en haut.
    val hero = focusedPick?.takeIf { pick -> (resume + latest).any { it.id == pick.item.id } }
        ?: resume.firstOrNull()?.let { HeroPick(it, "REPRENDRE LÀ OÙ TU T'ES ARRÊTÉ") }
        ?: latest.firstOrNull()?.let { HeroPick(it, MediaText.added(it.dateCreated)) }

    Box(modifier.fillMaxSize()) {
        if (!connectivity.offline && jellyfin?.down != true) Backdrop(hero?.item?.backdrop)
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
            Box(Modifier.height(176.dp), contentAlignment = Alignment.TopStart) {
                when {
                    connectivity.offline -> OfflineBanner(connectivity, onNetworkSettings)
                    jellyfin?.down == true -> ServerDownBanner(jellyfin)
                    guest && focusedPick == null -> GuestWelcome()
                    hero != null -> Hero(hero.item, hero.kicker, overviewLines = 2)
                    else -> Column {
                        Text("Bienvenue sur Nimbe", style = MaterialTheme.typography.displaySmall)
                        Text(
                            "Tes films, ta TV, ta musique et tes photos arriveront ici au fil des versions.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = LuneAttenuee,
                        )
                    }
                }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                rows.forEach { row ->
                    // Une clé par rangée : déplacée, elle garde sa position de défilement.
                    key(row.key) {
                        when (row) {
                            HomeRow.Continue -> {
                                if (resume.isNotEmpty()) {
                                    RowTitle("Continuer à regarder")
                                    val state = rememberLazyListState()
                                    ScrollToRestore(state, resume, restoreFocusId)
                                    LazyRow(
                                        state = state,
                                        modifier = Modifier.bleed(48.dp),
                                        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 48.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        items(resume, key = { "continue:${it.id}" }) { item ->
                                            WideCard(
                                                item = item,
                                                label = MediaText.cardLabel(item),
                                                onClick = { onPlayItem(item) },
                                                width = 196.dp,
                                                onFocus = { focusedPick = HeroPick(item, "REPRENDRE LÀ OÙ TU T'ES ARRÊTÉ") },
                                                restoreFocus = item.id == restoreFocusId,
                                                onRestored = onRestored,
                                            )
                                        }
                                    }
                                } else if (continueRow.state is Load.Failed && jellyfin?.down != true) {
                                    Text(
                                        "Jellyfin ne répond pas : tes films reviendront avec le serveur. Tes applis restent disponibles.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LuneAttenuee,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                            }
                            }
                            HomeRow.Apps -> {
                                RowTitle("Mes applis")
                                // La rangée n'apparaît qu'une fois la liste chargée : sinon, l'ajout des tuiles devant
                                // « Toutes les applis » décale la rangée et la première appli sort de l'écran.
                                if (dock == null) {
                                    Text(
                                        "Chargement…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = LuneAttenuee,
                                        modifier = Modifier.padding(vertical = 36.dp),
                                    )
                                } else LazyRow(
                                    modifier = Modifier.bleed(48.dp),
                                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 48.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(dock, key = { it.packageName }) { app ->
                                        AppTile(
                                            app = app,
                                            onClick = { onOpen(app) },
                                            onMenu = { requester -> onMenu(app, requester) },
                                            offline = connectivity.offline && InternetApps.needsInternet(app.packageName),
                                            modifier = Modifier.width(170.dp).animateItem(),
                                        )
                                    }
                                    item(key = "toutes") {
                                        ActionTile("▦", "Toutes les applis", onClick = { onAllApps() }, modifier = Modifier.width(170.dp))
                                    }
                            }
                            }
                            HomeRow.Latest -> {
                                if (latest.isNotEmpty()) {
                                    RowTitle("Ajouté récemment sur Jellyfin")
                                    val state = rememberLazyListState()
                                    ScrollToRestore(state, latest, restoreFocusId)
                                    LazyRow(
                                        state = state,
                                        modifier = Modifier.bleed(48.dp),
                                        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 48.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        items(latest, key = { "latest:${it.id}" }) { item ->
                                            PosterCard(
                                                item = item,
                                                onClick = { onOpenItem(item) },
                                                width = 104.dp,
                                                onFocus = { focusedPick = HeroPick(item, MediaText.added(item.dateCreated)) },
                                                restoreFocus = item.id == restoreFocusId && resume.none { it.id == restoreFocusId },
                                                onRestored = onRestored,
                                            )
                                        }
                                    }
                            }
            
                            }
                        }
                    }
                }
            }

            KeyHints("OK" to "ouvrir", "OK maintenu" to "options", modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun GuestWelcome() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("PROFIL INVITÉ", style = MaterialTheme.typography.titleSmall, color = Focus)
        Text("Bienvenue, fais comme chez toi", style = MaterialTheme.typography.displaySmall)
        Text(
            "Les films de la maison et la TV sont ouverts. Rien n'est enregistré : pas d'historique, " +
                "pas de galerie, pas de compte personnel.",
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
            modifier = Modifier.width(620.dp),
        )
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
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
                "Les applis du réseau de la maison restent disponibles. La TV, la musique et les plateformes " +
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
