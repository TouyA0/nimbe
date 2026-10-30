package io.github.touya0.nimbe.feature.streaming

import io.github.touya0.nimbe.feature.media.HeroWidth
import io.github.touya0.nimbe.feature.media.Kicker
import io.github.touya0.nimbe.feature.media.Overview
import io.github.touya0.nimbe.ui.components.ContentRow
import io.github.touya0.nimbe.ui.components.NimbeIcons
import io.github.touya0.nimbe.ui.components.PivotScroll
import io.github.touya0.nimbe.ui.components.ScreenMargin
import io.github.touya0.nimbe.ui.components.rowCardTop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.streaming.CastMember
import io.github.touya0.nimbe.data.streaming.JustWatchImages
import io.github.touya0.nimbe.data.streaming.StreamingCatalog
import io.github.touya0.nimbe.data.streaming.StreamingService
import io.github.touya0.nimbe.data.streaming.StreamingTitle
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.feature.apps.tileColor
import io.github.touya0.nimbe.feature.media.Chip
import io.github.touya0.nimbe.feature.media.Load
import io.github.touya0.nimbe.feature.media.SourceBadge
import io.github.touya0.nimbe.feature.media.titleStyle
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.bleed
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau

/**
 * Fiche d'un film ou d'une série des plateformes : affiche, résumé, où le regarder, équipe.
 * Elle s'affiche aussitôt avec ce que la liste connaissait, puis se complète.
 * « Lire sur Disney+ » ouvre l'appli sur le titre ; si le titre est aussi sur Jellyfin, c'est le premier bouton.
 */
@Composable
fun StreamingDetailScreen(initial: StreamingTitle, onOpenJellyfin: (MediaItem) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ui = LocalStreaming.current
    val services = ui?.services.orEmpty()
    val loader = rememberStreaming(StreamingCatalog.detailKey(initial.id, services), initial.id, services) { detail(initial.id, services) }
    val title = (loader.state as? Load.Ready)?.value ?: initial
    val jellyfin = ui?.inJellyfin(title)
    val offers = services.mapNotNull { title.offer(it) }
    val opened = ui?.history?.firstOrNull { it.titleId == title.id }

    val firstRequester = remember { FocusRequester() }
    var focusPlaced by remember { mutableStateOf(false) }
    // Le premier bouton prend le focus dès qu'il existe (les offres arrivent avec la fiche complète).
    LaunchedEffect(offers.isNotEmpty(), jellyfin != null, loader.state is Load.Ready) {
        if (!focusPlaced) focusPlaced = runCatching { firstRequester.requestFocus() }.isSuccess
    }

    Box(Modifier.fillMaxSize().background(Nuit)) {
        StreamingBackdrop(title.backdrop)
        PivotScroll(rowCardTop()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenMargin)) {
                Column(Modifier.padding(top = 64.dp).width(HeroWidth), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        offers.map { it.service }.distinct().forEach { ServiceBadge(it) }
                        if (jellyfin != null) SourceBadge("JELLYFIN")
                        Kicker(if (title.kind == TitleKind.Movie) "FILM" else "SÉRIE")
                    }
                    Text(title.title.uppercase(), style = titleStyle(title.title), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(StreamingText.meta(title), style = MaterialTheme.typography.bodyMedium)
                        StreamingText.rating(title.certification)?.let { Chip(it) }
                    }
                    title.overview?.let { Overview(it, lines = 4) }
                    opened?.streamingService?.let { service ->
                        Text(
                            "${openedLabel(opened.openedAt)} sur ${service.label} · ta progression est gardée par l'appli",
                            style = MaterialTheme.typography.labelMedium,
                            color = Focus,
                        )
                    }
                    Buttons(title, jellyfin, offers.map { it.service }, firstRequester, onOpenJellyfin)
                    Availability(loader.state, offers.isEmpty() && jellyfin == null, { loader.retry() })
                }
                Facts(title, offers.map { it.service })
                if (title.cast.isNotEmpty()) Cast(title.cast)
                Text(
                    "Disponibilités fournies par JustWatch · la lecture se fait dans l'appli de la plateforme, Retour ramène à Nimbe",
                    style = MaterialTheme.typography.labelMedium,
                    color = LuneAttenuee,
                    modifier = Modifier.padding(top = 20.dp, bottom = 32.dp),
                )
            }
        }
    }
}

/**
 * Boutons : Jellyfin d'abord s'il a le titre, puis chaque plateforme du profil. Quand la plateforme
 * a une fiche distincte de la lecture (Disney+), on propose aussi « Fiche dans Disney+ ».
 */
@Composable
private fun Buttons(
    title: StreamingTitle,
    jellyfin: MediaItem?,
    services: List<StreamingService>,
    firstRequester: FocusRequester,
    onOpenJellyfin: (MediaItem) -> Unit,
) {
    val ui = LocalStreaming.current
    // Passe à la ligne quand un titre est sur plusieurs plateformes, plutôt que d'écraser les boutons.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        var first = true
        fun Modifier.firstFocus(): Modifier = if (first) {
            first = false
            focusRequester(firstRequester)
        } else {
            this
        }
        if (jellyfin != null) {
            PillButton("Lire sur Jellyfin", icon = NimbeIcons.Play, onClick = { onOpenJellyfin(jellyfin) }, modifier = Modifier.firstFocus())
        }
        services.forEach { service ->
            val verb = if (ui?.history?.any { it.titleId == title.id && it.service == service.code } == true) "Reprendre" else "Lire"
            PillButton("$verb sur ${service.label}", icon = NimbeIcons.Play, onClick = { ui?.onLaunch?.invoke(title, service, false) }, modifier = Modifier.firstFocus())
            if (title.offer(service)?.hasSeparatePage == true) {
                PillButton("Fiche dans ${service.label}", icon = NimbeIcons.OpenInNew, onClick = { ui?.onLaunch?.invoke(title, service, true) })
            }
        }
    }
}

/** Ce qui manque : vérification en cours, pas d'internet, ou plus disponible. */
@Composable
private fun Availability(state: Load<StreamingTitle?>, nothing: Boolean, onRetry: () -> Unit) {
    when {
        state is Load.Loading && nothing -> Text("Recherche des plateformes…", style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
        state is Load.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Impossible de vérifier où le regarder : le stick n'arrive pas à joindre JustWatch.",
                style = MaterialTheme.typography.bodyMedium,
                color = LuneAttenuee,
            )
            PillButton("Réessayer", onClick = onRetry)
        }
        nothing -> Text(
            "Ce titre n'est plus proposé par tes plateformes.",
            style = MaterialTheme.typography.bodyMedium,
            color = LuneAttenuee,
        )
    }
}

@Composable
private fun Facts(title: StreamingTitle, services: List<StreamingService>) {
    Row(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Fact("Réalisation", title.directors.joinToString(", ").ifEmpty { "Inconnue" })
        Fact("Genres", title.categoryLabels.joinToString(", ").ifEmpty { "Non précisés" })
        Fact("Classification", StreamingText.rating(title.certification) ?: "Non précisée")
        Fact("Disponible sur", services.joinToString(", ") { it.label }.ifEmpty { "Aucune de tes plateformes" })
    }
}

@Composable
private fun RowScope.Fact(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Les acteurs, focalisables pour pouvoir faire défiler la rangée à la télécommande. */
@Composable
private fun Cast(cast: List<CastMember>) {
    ContentRow("Distribution", modifier = Modifier.padding(top = 12.dp), spacing = 18.dp) {
        itemsIndexed(cast, key = { index, person -> "$index:${person.name}" }) { _, person ->
            var focused by remember { mutableStateOf(false) }
            val role = person.role?.removeSuffix(" (voice)")?.takeIf { it.isNotBlank() }
            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = {},
                    modifier = Modifier
                        .size(72.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .focusRing(focused, 36.dp)
                        .semantics { contentDescription = listOfNotNull(person.name, role).joinToString(", ") },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = tileColor(person.name),
                        focusedContainerColor = tileColor(person.name),
                        contentColor = Lune,
                        focusedContentColor = Lune,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Box(Modifier.fillMaxSize().border(1.dp, Lune.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(initials(person.name), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(person.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                role?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

private fun initials(name: String): String = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
