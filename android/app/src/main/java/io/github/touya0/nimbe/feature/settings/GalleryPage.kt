package io.github.touya0.nimbe.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.nextcloud.GalleryPrefs
import io.github.touya0.nimbe.data.nextcloud.NextcloudFolders
import io.github.touya0.nimbe.data.nextcloud.NextcloudGallery
import io.github.touya0.nimbe.data.nextcloud.NextcloudLink
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfileKind
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import kotlinx.coroutines.launch

/**
 * Réglages → Galerie, pour le profil actif : ce que la galerie montre et le rythme du diaporama.
 * La galerie de l'enfant (un album choisi par un parent) se règle dans Profils.
 */
@Composable
internal fun GalleryPage(
    gallery: NextcloudGallery,
    profile: Profile?,
    links: Map<String, NextcloudLink>?,
    onConnect: () -> Unit,
    onFolders: () -> Unit,
) {
    PageTitle("Galerie", "Ce que montre la galerie Nextcloud de ${profile?.name ?: "ce profil"} sur la TV. Pour ce profil seulement.")
    if (profile == null || profile.kind != ProfileKind.Adult) {
        Text("La galerie se règle depuis un profil adulte.", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
        return
    }
    val link = links?.get(profile.id)
    if (link == null) {
        LinkRow(
            title = "Relier ta galerie",
            detail = "Un QR code à scanner avec ton téléphone, avec ton propre compte Nextcloud",
            value = "Relier",
            onClick = onConnect,
        )
        return
    }
    val scope = rememberCoroutineScope()
    val prefs by remember(profile.id) { gallery.prefs.prefs(profile.id) }.collectAsStateWithLifecycle(initialValue = null)
    val current = prefs ?: return
    fun save(changed: GalleryPrefs) {
        scope.launch { gallery.prefs.save(profile.id, changed) }
    }

    SettingsGroupTitle("Contenu")
    LinkRow(
        title = "Dossiers montrés",
        detail = "${link.serverUrl.substringAfter("://")} · compte ${link.loginName}",
        value = NextcloudFolders.label(link.folders),
        onClick = onFolders,
    )
    ToggleRow(
        title = "Vidéos",
        detail = if (current.showVideos) "Mélangées aux photos, avec leur rubrique" else "Masquées : seulement les photos",
        checked = current.showVideos,
        onChange = { save(current.copy(showVideos = it)) },
    )
    ToggleRow(
        title = "Masquer les captures d'écran",
        detail = "Les « Screenshot_… » et le dossier Screenshots du téléphone",
        checked = current.hideScreenshots,
        onChange = { save(current.copy(hideScreenshots = it)) },
    )
    ToggleRow(
        title = "Souvenirs « il y a un an »",
        detail = "Dans « Pour toi » et sur l'accueil (si la rangée Souvenirs y est affichée)",
        checked = current.memories,
        onChange = { save(current.copy(memories = it)) },
    )

    SettingsGroupTitle("Diaporama")
    val choices = GalleryPrefs.SlideChoices
    LinkRow(
        title = "Durée par photo",
        detail = "OK pour changer · passage en fondu",
        value = "${current.slideSeconds} s",
        onClick = { save(current.copy(slideSeconds = choices[(choices.indexOf(current.slideSeconds) + 1) % choices.size])) },
    )
}
