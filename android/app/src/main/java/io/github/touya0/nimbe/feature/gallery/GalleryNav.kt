package io.github.touya0.nimbe.feature.gallery

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

/**
 * Ce qui est ouvert par-dessus la rubrique : un souvenir ou une année (calculés sur la TV),
 * un album Nextcloud, ou un dossier (on y descend niveau par niveau).
 */
sealed interface GalleryOpen {
    data class Local(val collection: GalleryCollection) : GalleryOpen
    data class Album(val album: NextcloudAlbum, val shared: Boolean) : GalleryOpen
    data class Folder(val path: String) : GalleryOpen
}

/** Un niveau ouvert et la position de sa grille. */
class OpenedLevel(val target: GalleryOpen) {
    val grid = LazyGridState()
}

/**
 * Où l'on en est dans l'onglet Galerie : rubrique, pile de ce qui est ouvert (dossier dans un dossier…),
 * position de chaque liste. Gardé par le shell (un par profil) : le plein écran remplace l'onglet, et au
 * retour tout est à sa place, avec le focus sur la photo regardée en dernier ([restoreFileId]).
 */
@Stable
class GalleryNav {
    var section by mutableStateOf(GallerySection.ForYou)
    var restoreFileId by mutableStateOf<Long?>(null)

    /** La carte (dossier, album, souvenir) dont on revient : elle reprend le focus. Voir [key]. */
    var restoreKey by mutableStateOf<String?>(null)

    val forYou = LazyListState()
    val timeline = LazyGridState()
    val albums = LazyGridState()
    val videos = LazyGridState()
    val shared = LazyGridState()

    private val stack = mutableStateListOf<OpenedLevel>()

    /** Le niveau affiché, null : la rubrique elle-même. */
    val opened: OpenedLevel? get() = stack.lastOrNull()
    val open: GalleryOpen? get() = opened?.target
    val depth: Int get() = stack.size

    /**
     * Pose le focus sur l'onglet Galerie, fourni par le shell. Appelé avant d'ouvrir ou de fermer :
     * sans cela, la carte qui avait le focus disparaît, le focus retombe sur le premier onglet (Accueil)
     * et l'en-tête, qui suit le focus, change d'onglet.
     */
    var holdFocus: () -> Unit = {}

    /**
     * Retour dans une grille qui a défilé : remonte en haut et rend le focus aux rubriques ; rend true s'il
     * a servi. Fourni par la page affichée, appelé par le shell avant de quitter l'onglet.
     */
    var backToTop: () -> Boolean = { false }

    fun open(target: GalleryOpen) {
        holdFocus()
        restoreKey = null
        stack.add(OpenedLevel(target))
    }

    /** Remonte d'un niveau (le dossier parent, ou la rubrique). */
    fun close() {
        holdFocus()
        if (stack.isNotEmpty()) restoreKey = key(stack.removeAt(stack.lastIndex).target)
    }

    companion object {
        /** Identifiant d'une carte, pour lui rendre le focus au retour. */
        fun key(target: GalleryOpen): String = when (target) {
            is GalleryOpen.Folder -> "f:" + target.path
            is GalleryOpen.Album -> "a:" + target.album.davPath
            is GalleryOpen.Local -> "l:" + target.collection.kicker + "/" + target.collection.title
        }
    }

    /** Tout refermer d'un coup (changement de rubrique). */
    fun closeAll() {
        if (stack.isNotEmpty()) holdFocus()
        stack.clear()
    }
}
