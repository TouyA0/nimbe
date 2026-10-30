package io.github.touya0.nimbe.feature.films

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.ItemDetail
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.feature.apps.tileColor
import io.github.touya0.nimbe.feature.media.Backdrop
import io.github.touya0.nimbe.feature.media.BackdropDimmer
import io.github.touya0.nimbe.feature.media.Chip
import io.github.touya0.nimbe.feature.media.HeroWidth
import io.github.touya0.nimbe.feature.media.JellyfinImage
import io.github.touya0.nimbe.feature.media.Kicker
import io.github.touya0.nimbe.feature.media.MediaText
import io.github.touya0.nimbe.feature.media.MetaLine
import io.github.touya0.nimbe.feature.media.Overview
import io.github.touya0.nimbe.feature.media.PosterCard
import io.github.touya0.nimbe.feature.media.ProgressBar
import io.github.touya0.nimbe.feature.media.rememberJellyfin
import io.github.touya0.nimbe.feature.media.titleStyle
import io.github.touya0.nimbe.feature.streaming.AlsoOnButtons
import io.github.touya0.nimbe.feature.streaming.rememberAlsoOn
import io.github.touya0.nimbe.ui.components.ContentRow
import io.github.touya0.nimbe.ui.components.NimbeIcons
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.PivotScroll
import io.github.touya0.nimbe.ui.components.ScreenMargin
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.components.rowCardTop
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Fiche d'un film : titre dessiné s'il existe, résumé, reprise, langues, fichier, distribution et films proches.
 * Elle s'affiche aussitôt avec ce que la liste connaissait, puis se complète.
 * [onPlay] reçoit le film et true pour repartir du début. [onSetPlayed] null : le profil ne change rien sur le serveur.
 */
@Composable
fun FilmDetailScreen(
    item: MediaItem,
    onPlay: (MediaItem, Boolean) -> Unit,
    onOpenSimilar: (MediaItem) -> Unit,
    onSetPlayed: ((MediaItem, Boolean) -> Unit)?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val loader = rememberJellyfin("detail:${item.id}", item.id) { detail(it, item.id) }
    val detail = loader.value
    val film = detail?.item ?: item
    val alsoOn = rememberAlsoOn(film, TitleKind.Movie)
    val similar = rememberJellyfin("similar:${item.id}", item.id) { similar(it, item.id) }.value.orEmpty()
    // Le bouton répond tout de suite ; la liste, elle, relira le serveur au retour.
    var played by remember(film.id, film.played) { mutableStateOf(film.played) }
    val playRequester = remember(item.id) { FocusRequester() }
    LaunchedEffect(item.id) { runCatching { playRequester.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Nuit)) {
        val scroll = rememberScrollState()
        Backdrop(film.backdrop)
        BackdropDimmer(scroll)
        // Les rangées du bas viennent se poser sous les boutons quand on y descend.
        var topFocused by remember { mutableStateOf(false) }
        PivotScroll(rowCardTop(), scrollToStart = { if (topFocused) scroll.value.toFloat() else null }) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = ScreenMargin)) {
                Column(Modifier.padding(top = 48.dp).width(DetailWidth).onFocusChanged { topFocused = it.hasFocus }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilmTitle(film)
                    // Une accroche ne se coupe pas : trop longue pour sa ligne, elle n'apparaît pas.
                    film.tagline?.takeIf { it.length <= TAGLINE_MAX_LENGTH }?.let { Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 1) }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        MetaLine(film)
                        detail?.quality?.let { Chip(it) }
                        if (detail?.hdr == true) Chip("HDR")
                        detail?.surround?.let { Chip(it) }
                    }
                    film.overview?.let { Overview(it, lines = 4) }
                    val resumable = film.resumable
                    if (resumable) Resume(film) else EndsAt(film)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        PillButton(
                            text = if (resumable) "Reprendre" else "Lire",
                            icon = NimbeIcons.Play,
                            onClick = { onPlay(film, false) },
                            modifier = Modifier.focusRequester(playRequester),
                        )
                        if (resumable) PillButton("Depuis le début", icon = NimbeIcons.Restart, onClick = { onPlay(film, true) })
                        onSetPlayed?.let { set ->
                            PillButton(
                                text = if (played) "Marquer comme non vu" else "Marquer comme vu",
                                icon = if (played) NimbeIcons.Close else NimbeIcons.Check,
                                onClick = {
                                    played = !played
                                    set(film, played)
                                },
                            )
                        }
                        // Le même film sur une plateforme du profil : utile quand le Pi est coupé.
                        AlsoOnButtons(alsoOn)
                    }
                }
                if (detail != null) Facts(detail)
                if (detail != null && detail.people.any { it.type == "Actor" }) Cast(detail)
                if (similar.isNotEmpty()) Similar(similar, onOpenSimilar)
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

/** Le titre dessiné du film ; le nom en Bebas Neue s'il n'y en a pas ou s'il ne se charge pas. */
@Composable
private fun FilmTitle(film: MediaItem) {
    var logoFailed by remember(film.logo) { mutableStateOf(false) }
    val logo = film.logo?.takeUnless { logoFailed }
    if (logo != null) {
        JellyfinImage(
            logo,
            width = LogoWidth,
            height = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { logoFailed = true },
            modifier = Modifier.width(LogoWidth).height(LogoHeight).semantics { contentDescription = film.name },
        )
    } else {
        if (film.resumable) Kicker("EN COURS")
        Text(film.name.uppercase(), style = titleStyle(film.name), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** « Arrêté à 1 h 21 ……… 47 min restantes · Fin à 22:14 », au-dessus de la barre. */
@Composable
private fun Resume(film: MediaItem) {
    val end = rememberEndsAt(film)
    Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(MediaText.stoppedAt(film), style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, modifier = Modifier.weight(1f))
            Text(
                listOfNotNull(MediaText.progressLine(film), end).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = LuneAttenuee,
            )
        }
        ProgressBar(film.progress, Modifier.fillMaxWidth())
    }
}

/** « Fin à 22:14 » si on lance le film maintenant. */
@Composable
private fun EndsAt(film: MediaItem) {
    rememberEndsAt(film)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee) }
}

/** L'heure de fin avance avec l'horloge, tant que la fiche reste ouverte. */
@Composable
private fun rememberEndsAt(film: MediaItem): String? {
    val now by produceState(LocalTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalTime.now()
        }
    }
    return MediaText.endsAt(film, now)
}

/** Réalisation, audio, sous-titres et fichier, sur une ligne. */
@Composable
private fun Facts(detail: ItemDetail) {
    val directors = detail.people.filter { it.type == "Director" }.joinToString(", ") { it.name }
    val file = listOfNotNull(detail.video, detail.sizeBytes?.let(::formatGigabytes)).joinToString(" · ")
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Fact("Réalisation", directors.ifEmpty { "Inconnue" })
        Fact("Audio", detail.audioLanguages.joinToString(" · ").ifEmpty { "Inconnu" })
        Fact("Sous-titres", detail.subtitleLanguages.joinToString(", ").ifEmpty { "Aucun" })
        Fact("Fichier", file.ifEmpty { "Inconnu" })
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Fact(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Les acteurs, focalisables pour pouvoir faire défiler la rangée à la télécommande. */
@Composable
private fun Cast(detail: ItemDetail) {
    ContentRow("Distribution", modifier = Modifier.padding(top = 12.dp), spacing = 18.dp) {
        items(detail.people.filter { it.type == "Actor" }.take(20), key = { it.id + it.role }) { person ->
            var focused by remember { mutableStateOf(false) }
            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = {},
                    modifier = Modifier.size(80.dp).onFocusChanged { focused = it.isFocused }.focusRing(focused, 40.dp),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = tileColor(person.name),
                        focusedContainerColor = tileColor(person.name),
                        contentColor = Lune,
                        focusedContentColor = Lune,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(initials(person.name), style = MaterialTheme.typography.titleMedium)
                        JellyfinImage(person.image, width = 80.dp, height = null, modifier = Modifier.fillMaxSize())
                    }
                }
                Text(person.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                person.role?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Les films proches, avec leur nom dessous : ici, pas de bandeau du haut pour le donner. */
@Composable
private fun Similar(films: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    ContentRow("Dans le même esprit", modifier = Modifier.padding(top = 12.dp)) {
        items(films, key = { it.id }) { film ->
            Column(Modifier.width(SimilarWidth), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterCard(film, onClick = { onOpen(film) }, width = SimilarWidth)
                Text(film.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private const val TAGLINE_MAX_LENGTH = 70
private val DetailWidth = HeroWidth + 60.dp
private val LogoWidth = 360.dp
private val LogoHeight = 110.dp
private val SimilarWidth = 112.dp

private fun initials(name: String): String = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

private fun formatGigabytes(bytes: Long): String {
    val gigabytes = bytes / 1_000_000_000.0
    return if (gigabytes >= 1) String.format(Locale.FRENCH, "%.1f Go", gigabytes) else "${bytes / 1_000_000} Mo"
}
