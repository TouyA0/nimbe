package io.github.touya0.nimbe.feature.films

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.ItemDetail
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.feature.apps.tileColor
import io.github.touya0.nimbe.feature.media.Backdrop
import io.github.touya0.nimbe.feature.media.Chip
import io.github.touya0.nimbe.feature.media.JellyfinImage
import io.github.touya0.nimbe.feature.media.MediaText
import io.github.touya0.nimbe.feature.media.MetaLine
import io.github.touya0.nimbe.feature.media.SourceBadge
import io.github.touya0.nimbe.feature.media.rememberJellyfin
import io.github.touya0.nimbe.feature.media.titleStyle
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.feature.streaming.AlsoOnButtons
import io.github.touya0.nimbe.feature.streaming.rememberAlsoOn
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.bleed
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import java.util.Locale

/**
 * Fiche d'un film : affiche, résumé, reprise, langues, fichier et distribution.
 * Elle s'affiche aussitôt avec ce que la liste connaissait, puis se complète.
 * [onPlay] reçoit le film et true pour repartir du début.
 */
@Composable
fun FilmDetailScreen(item: MediaItem, onPlay: (MediaItem, Boolean) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val loader = rememberJellyfin("detail:${item.id}", item.id) { detail(it, item.id) }
    val detail = loader.value
    val film = detail?.item ?: item
    val alsoOn = rememberAlsoOn(film, TitleKind.Movie)
    val playRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playRequester.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Nuit)) {
        Backdrop(film.backdrop)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 28.dp)) {
            Text("‹  Films", style = MaterialTheme.typography.titleSmall, color = LuneAttenuee)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Box(Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Panneau)) {
                    JellyfinImage(film.primary, width = 150.dp, height = null, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SourceBadge("JELLYFIN")
                    Text(film.name.uppercase(), style = titleStyle(film.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MetaLine(film)
                        detail?.video?.let { Chip(it) }
                    }
                    film.overview?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(520.dp))
                    }
                    val resumable = film.resumable
                    if (resumable) Resume(film)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        PillButton(
                            text = if (resumable) "▶  Reprendre" else "▶  Lire",
                            onClick = { onPlay(film, false) },
                            modifier = Modifier.focusRequester(playRequester),
                        )
                        if (resumable) PillButton("Depuis le début", onClick = { onPlay(film, true) })
                        // Le même film sur une plateforme du profil : utile quand le Pi est coupé.
                        AlsoOnButtons(alsoOn)
                    }
                }
            }
            if (detail != null) Facts(detail)
            if (detail != null && detail.people.any { it.type == "Actor" }) Cast(detail)
        }
    }
}

/** « Arrêté à 1 h 21 ……… 47 min restantes », au-dessus de la barre. */
@Composable
private fun Resume(film: MediaItem) {
    Column(Modifier.width(330.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(MediaText.stoppedAt(film), style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, modifier = Modifier.weight(1f))
            MediaText.progressLine(film)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee) }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(PanneauClair, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(film.progress).height(4.dp).background(Focus, RoundedCornerShape(2.dp)))
        }
    }
}

/** Réalisation, audio, sous-titres et fichier, sur une ligne. */
@Composable
private fun Facts(detail: ItemDetail) {
    val directors = detail.people.filter { it.type == "Director" }.joinToString(", ") { it.name }
    val file = listOfNotNull(detail.video, detail.sizeBytes?.let(::formatGigabytes)).joinToString(" · ")
    Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Fact("Réalisation", directors.ifEmpty { "Inconnue" })
        Fact("Audio", detail.audioLanguages.joinToString(" · ").ifEmpty { "Inconnu" })
        Fact("Sous-titres", (detail.subtitleLanguages + "Aucun").joinToString(", "))
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
    Text("Distribution", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
    LazyRow(
        modifier = Modifier.bleed(48.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(detail.people.filter { it.type == "Actor" }.take(20), key = { it.id + it.role }) { person ->
            var focused by remember { mutableStateOf(false) }
            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = {},
                    modifier = Modifier.size(72.dp).onFocusChanged { focused = it.isFocused }.focusRing(focused, 36.dp),
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
                        JellyfinImage(person.image, width = 72.dp, height = null, modifier = Modifier.fillMaxSize())
                    }
                }
                Text(person.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                person.role?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

private fun initials(name: String): String = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

private fun formatGigabytes(bytes: Long): String {
    val gigabytes = bytes / 1_000_000_000.0
    return if (gigabytes >= 1) String.format(Locale.FRENCH, "%.1f Go", gigabytes) else "${bytes / 1_000_000} Mo"
}
