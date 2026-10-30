package io.github.touya0.nimbe.feature.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.touya0.nimbe.feature.apps.InstalledApp
import io.github.touya0.nimbe.feature.media.CONTINUE_KICKER
import io.github.touya0.nimbe.feature.media.CatalogFocus

/** Accueil d'un profil Invité ou Enfant, en haut de l'écran tant qu'aucune carte n'a le focus. */
data class HomeWelcome(val kicker: String, val title: String, val text: String)

/** Ce que montre le haut de l'accueil : l'élément focalisé, ou ce qu'il y a à dire quand aucun ne l'est. */
sealed interface HomeHero {
    val key: String

    data class Media(val focus: CatalogFocus) : HomeHero {
        override val key get() = "media:${focus.id}"
    }

    data class App(val app: InstalledApp) : HomeHero {
        override val key get() = "app:${app.packageName}"
    }

    data object AllApps : HomeHero {
        override val key get() = "toutes"
    }

    /** [image] : la couverture, à charger avec les droits du compte Nextcloud. */
    data class Memory(val kicker: String, val title: String, val detail: String, val image: Any?) : HomeHero {
        override val key get() = "souvenir:$kicker:$title"
    }

    data class Welcome(val welcome: HomeWelcome) : HomeHero {
        override val key get() = "bienvenue"
    }

    data object Loading : HomeHero {
        override val key get() = "chargement"
    }

    data object Empty : HomeHero {
        override val key get() = "vide"
    }
}

object HomeText {

    /** Les touches utiles pour cet élément. [canEdit] : le profil peut modifier « Mes applis ». */
    fun hints(hero: HomeHero, canEdit: Boolean): List<Pair<String, String>> = when (hero) {
        is HomeHero.Media -> listOf("OK" to okAction(hero.focus), HOLD to "options")
        is HomeHero.App -> listOf("OK" to "ouvrir") + if (canEdit) listOf(HOLD to "déplacer, retirer") else emptyList()
        HomeHero.AllApps -> listOf("OK" to "voir toutes les applis")
        is HomeHero.Memory -> listOf("OK" to "voir en plein écran")
        is HomeHero.Welcome, HomeHero.Loading, HomeHero.Empty -> emptyList()
    }

    private fun okAction(focus: CatalogFocus): String = when (focus) {
        is CatalogFocus.Jellyfin -> when {
            focus.item.resumable -> "reprendre"
            focus.kicker == CONTINUE_KICKER -> "lire"
            else -> "voir la fiche"
        }
        is CatalogFocus.Streaming -> "voir la fiche"
        is CatalogFocus.WatchNext -> "ouvrir dans ${focus.program.appLabel}"
    }

    /** « Bonsoir Quentin » ; sans nom, seulement le salut. */
    fun greeting(hour: Int, name: String?): String {
        val hello = if (hour in 5..17) "Bonjour" else "Bonsoir"
        return if (name.isNullOrBlank()) hello else "$hello $name"
    }

    private const val HOLD = "OK maintenu"
}

/** Ce que le reste de l'appli demande à l'accueil. Un par profil. */
@Stable
class HomeNav {
    /** Augmente pour relire « Continuer à regarder » et « Ajouté récemment ». */
    var reloads by mutableIntStateOf(0)

    /** L'accueil vient de s'afficher : le focus ira sur la première carte, dès qu'elle est connue. */
    var startFocus by mutableStateOf(false)

    /** Remonte en haut de l'accueil, sur la première carte ; false s'il y est déjà. Posé par l'accueil affiché. */
    var backToTop: () -> Boolean = { false }
}
