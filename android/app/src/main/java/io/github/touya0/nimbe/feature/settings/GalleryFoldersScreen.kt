package io.github.touya0.nimbe.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.nextcloud.NextcloudException
import io.github.touya0.nimbe.data.nextcloud.NextcloudFolders
import io.github.touya0.nimbe.data.nextcloud.NextcloudGallery
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import kotlinx.coroutines.launch

/** Contenu d'un dossier en cours de lecture sur le serveur. */
private sealed interface Listing {
    data object Loading : Listing
    data class Ready(val folders: List<String>) : Listing
    data class Failed(val message: String) : Listing
}

/**
 * Choisir les dossiers que montre la galerie d'un profil, en parcourant le compte Nextcloud.
 * OK sur un dossier l'ouvre ; « Montrer ce dossier » l'ajoute. Rien n'est enregistré avant « Enregistrer ».
 */
@Composable
fun GalleryFoldersScreen(
    gallery: NextcloudGallery,
    profileId: String,
    profileName: String,
    initial: List<String>,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(initial) }
    // null : racine du compte.
    var current by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val listing by produceState<Listing>(Listing.Loading, current, attempt) {
        value = Listing.Loading
        value = try {
            Listing.Ready(gallery.folders(profileId, current).orEmpty())
        } catch (e: NextcloudException) {
            Listing.Failed(e.message.orEmpty())
        }
    }
    fun parent(path: String) = path.substringBeforeLast('/', "").ifEmpty { null }
    BackHandler { current?.let { current = parent(it) } ?: onClose() }

    // Le focus va sur la première ligne utile de chaque dossier ouvert.
    val firstRow = remember { FocusRequester() }
    val parentRow = remember { FocusRequester() }
    LaunchedEffect(current, listing is Listing.Ready) { runCatching { firstRow.requestFocus() } }

    Column(
        Modifier.fillMaxSize().background(Nuit).verticalScroll(rememberScrollState()).padding(horizontal = 96.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Dossiers de la galerie de $profileName", style = MaterialTheme.typography.displaySmall)
        Text(
            "Seules les photos et vidéos de ces dossiers (et de leurs sous-dossiers) apparaissent sur la TV. " +
                "Sans dossier choisi, la galerie montre tout le compte.",
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
        )

        SettingsGroupTitle("Montrés")
        if (selected.isEmpty()) {
            Text("Tout le compte", style = MaterialTheme.typography.bodyLarge)
        } else {
            selected.forEach { folder ->
                LinkRow(title = folder.substringAfterLast('/'), detail = folder, value = "Retirer", onClick = { selected = selected - folder })
            }
        }

        SettingsGroupTitle(current?.let { "Dans « ${it.substringAfterLast('/')} »" } ?: "Dossiers du compte")
        val covered = current != null && selected.any { current == it || current!!.startsWith("$it/") }
        var first = true
        fun focusFirst(): Modifier = if (first) Modifier.focusRequester(firstRow).also { first = false } else Modifier
        current?.let { folder ->
            if (covered) {
                Text("● Ce dossier est montré", style = MaterialTheme.typography.labelMedium, color = EnLigne)
            } else {
                SettingRow(
                    title = "Montrer ce dossier",
                    detail = folder,
                    onClick = {
                        selected = NextcloudFolders.normalize(selected + folder)
                        // La ligne disparaît : sans cela, le focus partirait sur « Retirer » en haut.
                        scope.launch {
                            withFrameNanos { }
                            runCatching { parentRow.requestFocus() }
                        }
                    },
                    modifier = focusFirst(),
                    trailing = { Text("Ajouter", style = MaterialTheme.typography.titleSmall) },
                )
            }
            LinkRow(
                title = "‹ Dossier parent",
                detail = null,
                value = "",
                onClick = { current = parent(folder) },
                modifier = Modifier.focusRequester(parentRow),
            )
        }
        when (val state = listing) {
            Listing.Loading -> Text("Chargement…", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
            is Listing.Failed -> {
                Text(state.message, style = MaterialTheme.typography.bodyLarge, color = Alerte)
                PillButton("Réessayer", onClick = { attempt++ }, modifier = focusFirst())
            }
            is Listing.Ready -> {
                if (state.folders.isEmpty()) {
                    Text("Pas de sous-dossier ici.", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
                }
                state.folders.forEach { folder ->
                    val shown = selected.any { folder == it || folder.startsWith("$it/") }
                    SettingRow(
                        title = folder.substringAfterLast('/'),
                        detail = if (shown) "Montré" else null,
                        onClick = { current = folder },
                        modifier = focusFirst(),
                        trailing = { Text("Ouvrir ›", style = MaterialTheme.typography.titleSmall) },
                    )
                }
            }
        }

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Enregistrer", onClick = {
                scope.launch {
                    gallery.setFolders(profileId, selected)
                    onClose()
                }
            })
            PillButton("Tout le compte", onClick = { selected = emptyList() })
            PillButton("Annuler", onClick = onClose)
        }
    }
}
