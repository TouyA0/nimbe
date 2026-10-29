package io.github.touya0.nimbe.feature.gallery

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.touya0.nimbe.data.nextcloud.NextcloudAlbum

/** Les rubriques de l'onglet Galerie, dans l'ordre de la maquette. */
enum class GallerySection(val label: String) {
    ForYou("Pour toi"),
    Timeline("Chronologie"),
    Albums("Albums"),
    Videos("Vidéos"),
    Shared("Partagées"),
}

/** Ce qui est ouvert par-dessus la rubrique : un souvenir ou un dossier (calculé sur la TV), ou un album Nextcloud. */
sealed interface GalleryOpen {
    data class Local(val collection: GalleryCollection) : GalleryOpen
    data class Album(val album: NextcloudAlbum, val shared: Boolean) : GalleryOpen
}

/**
 * Où l'on en est dans l'onglet Galerie : rubrique, album ouvert, position de chaque liste.
 * Gardé par le shell (un par profil) : le plein écran remplace l'onglet, et au retour tout est à sa place,
 * avec le focus sur la photo regardée en dernier ([restoreFileId]).
 */
@Stable
class GalleryNav {
    var section by mutableStateOf(GallerySection.ForYou)
    var open by mutableStateOf<GalleryOpen?>(null)
        private set
    var restoreFileId by mutableStateOf<Long?>(null)

    val forYou = LazyListState()
    val timeline = LazyGridState()
    val albums = LazyGridState()
    val videos = LazyGridState()
    val shared = LazyGridState()

    /**
     * Pose le focus sur l'onglet Galerie, fourni par le shell. Appelé avant d'ouvrir ou de fermer :
     * sans cela, la carte qui avait le focus disparaît, le focus retombe sur le premier onglet (Accueil)
     * et l'en-tête, qui suit le focus, change d'onglet.
     */
    var holdFocus: () -> Unit = {}

    /** La grille de ce qui est ouvert : neuve à chaque ouverture, pour commencer en haut. */
    var opened = LazyGridState()
        private set

    fun open(target: GalleryOpen) {
        holdFocus()
        opened = LazyGridState()
        open = target
    }

    fun close() {
        holdFocus()
        open = null
    }
}
