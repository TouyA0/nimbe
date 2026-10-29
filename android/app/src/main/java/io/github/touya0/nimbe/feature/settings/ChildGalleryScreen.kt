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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.nextcloud.NextcloudAlbum
import io.github.touya0.nimbe.data.nextcloud.NextcloudException
import io.github.touya0.nimbe.data.nextcloud.NextcloudGallery
import io.github.touya0.nimbe.data.nextcloud.NextcloudLink
import io.github.touya0.nimbe.data.profiles.ChildGallery
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.feature.profiles.ProfileAvatar
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import kotlinx.coroutines.launch

/**
 * Choisir les photos qu'un enfant voit : un album ou un dossier de la galerie d'un adulte.
 * L'enfant ne verra rien d'autre de ce compte. [onSave] reçoit le choix (null : pas de galerie).
 */
@Composable
fun ChildGalleryScreen(
    gallery: NextcloudGallery,
    child: Profile,
    adults: List<Profile>,
    links: Map<String, NextcloudLink>,
    onSave: (ChildGallery?) -> Unit,
    onClose: () -> Unit,
) {
    val linkedAdults = adults.filter { it.id in links }
    // Le parent dont on parcourt la galerie : celui déjà choisi, sinon le seul relié.
    var source by remember { mutableStateOf(child.gallery?.sourceProfileId?.takeIf { id -> linkedAdults.any { it.id == id } } ?: linkedAdults.singleOrNull()?.id) }
    var folder by remember { mutableStateOf<String?>(null) }
    BackHandler {
        when {
            folder != null -> folder = folder!!.substringBeforeLast('/', "").ifEmpty { null }
            source != null && linkedAdults.size > 1 -> source = null
            else -> onClose()
        }
    }
    val first = remember { FocusRequester() }
    // Sans galerie reliée ou au choix du parent, la première ligne est déjà là ; sinon, SourceChoices s'en charge.
    LaunchedEffect(source, folder) { if (source == null || linkedAdults.isEmpty()) runCatching { first.requestFocus() } }

    Column(
        Modifier.fillMaxSize().background(Nuit).verticalScroll(rememberScrollState()).padding(horizontal = 96.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Galerie de ${child.name}", style = MaterialTheme.typography.displaySmall)
        Text(
            "Choisis un album ou un dossier de ta galerie Nextcloud : ce sera la seule chose que ${child.name} verra dans l'onglet Galerie.",
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
        )
        Text(
            "Aujourd'hui : " + (child.gallery?.let { "« ${it.name} »" } ?: "pas de galerie"),
            style = MaterialTheme.typography.titleMedium,
        )

        when {
            linkedAdults.isEmpty() -> {
                Text(
                    "Aucun adulte n'a encore relié sa galerie : Réglages → Serveurs et comptes → Galeries Nextcloud.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Alerte,
                )
                PillButton("Retour", onClick = onClose, modifier = Modifier.focusRequester(first))
            }
            source == null -> {
                SettingsGroupTitle("Depuis la galerie de")
                linkedAdults.forEachIndexed { index, adult ->
                    SettingRow(
                        title = adult.name,
                        detail = "compte ${links.getValue(adult.id).loginName}",
                        onClick = { source = adult.id },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        leading = { ProfileAvatar(adult, 40.dp) },
                    )
                }
            }
            else -> SourceChoices(gallery, source!!, folder, first, onFolder = { folder = it }, onPick = { onSave(it); onClose() })
        }

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (child.gallery != null) {
                PillButton("Retirer la galerie", onClick = {
                    onSave(null)
                    onClose()
                })
            }
            PillButton("Annuler", onClick = onClose)
        }
    }
}

/** Albums (les siens et ceux qu'on lui a partagés), puis les dossiers du compte, à parcourir. */
@Composable
private fun SourceChoices(
    gallery: NextcloudGallery,
    source: String,
    folder: String?,
    first: FocusRequester,
    onFolder: (String?) -> Unit,
    onPick: (ChildGallery) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val albums by produceState<List<NextcloudAlbum>?>(null, source) {
        value = try {
            gallery.refreshAlbums(source, shared = false) + gallery.refreshAlbums(source, shared = true)
        } catch (_: NextcloudException) {
            (gallery.cachedAlbums(source, false).orEmpty() + gallery.cachedAlbums(source, true).orEmpty())
        }
    }
    val folders by produceState<Result<List<String>>?>(null, source, folder) {
        value = null
        value = try {
            Result.success(gallery.folders(source, folder).orEmpty())
        } catch (e: NextcloudException) {
            Result.failure(e)
        }
    }
    var firstUsed = false
    fun focusFirst(): Modifier = if (!firstUsed) Modifier.focusRequester(first).also { firstUsed = true } else Modifier
    // Le focus va sur la première ligne quand elle arrive : les albums et dossiers se chargent après l'écran.
    LaunchedEffect(folder, albums != null, folders != null) { runCatching { first.requestFocus() } }

    if (folder == null) {
        SettingsGroupTitle("Albums")
        when (val list = albums) {
            null -> Text("Chargement…", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
            else -> if (list.isEmpty()) {
                Text("Pas d'album dans l'appli Photos de Nextcloud : choisis plutôt un dossier.", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
            } else {
                list.forEach { album ->
                    SettingRow(
                        title = album.name,
                        detail = listOfNotNull(album.owner?.let { "partagé par $it" }, "${album.count} éléments").joinToString(" · "),
                        onClick = { onPick(ChildGallery(source, album.name, albumPath = album.davPath)) },
                        modifier = focusFirst(),
                        trailing = { Text("Choisir", style = MaterialTheme.typography.titleSmall) },
                    )
                }
            }
        }
    }

    SettingsGroupTitle(folder?.let { "Dans « ${it.substringAfterLast('/')} »" } ?: "Dossiers")
    folder?.let { current ->
        SettingRow(
            title = "Choisir ce dossier",
            detail = "$current, sous-dossiers compris",
            onClick = { onPick(ChildGallery(source, current.substringAfterLast('/'), folder = current)) },
            modifier = focusFirst(),
            trailing = { Text("Choisir", style = MaterialTheme.typography.titleSmall) },
        )
        LinkRow(title = "‹ Dossier parent", detail = null, value = "", onClick = { onFolder(current.substringBeforeLast('/', "").ifEmpty { null }) })
    }
    when (val result = folders) {
        null -> Text("Chargement…", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
        else -> result.fold(
            onSuccess = { list ->
                if (list.isEmpty()) Text("Pas de sous-dossier ici.", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
                list.forEach { path ->
                    SettingRow(
                        title = path.substringAfterLast('/'),
                        detail = null,
                        onClick = { scope.launch { onFolder(path) } },
                        modifier = focusFirst(),
                        trailing = { Text("Ouvrir ›", style = MaterialTheme.typography.titleSmall) },
                    )
                }
            },
            onFailure = { Text(it.message.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = Alerte) },
        )
    }
}
