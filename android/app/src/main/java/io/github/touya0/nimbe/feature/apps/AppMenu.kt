package io.github.touya0.nimbe.feature.apps

import androidx.compose.ui.focus.FocusRequester
import io.github.touya0.nimbe.ui.components.MenuOption
import io.github.touya0.nimbe.ui.components.MenuRequest

/** Les actions possibles sur une appli, branchées par l'écran qui affiche le menu. */
class AppActions(
    val open: (InstalledApp) -> Unit,
    val addToDock: (InstalledApp) -> Unit,
    val removeFromDock: (InstalledApp) -> Unit,
    val move: (InstalledApp, Int) -> Unit,
    val hide: (InstalledApp) -> Unit,
    val showInfo: (InstalledApp) -> Unit,
    val uninstall: (InstalledApp) -> Unit,
)

/** Options du menu d'une appli, selon sa place dans « Mes applis ». */
fun appMenu(app: InstalledApp, dock: List<String>, actions: AppActions, returnFocus: FocusRequester?): MenuRequest {
    val index = dock.indexOf(app.packageName)
    val options = buildList {
        add(MenuOption("▶", "Ouvrir", app.label) { actions.open(app) })
        if (index < 0) {
            add(MenuOption("＋", "Ajouter à Mes applis", "À la fin de la rangée") { actions.addToDock(app) })
        } else {
            if (index > 0) add(MenuOption("◀", "Déplacer vers la gauche") { actions.move(app, -1) })
            if (index < dock.lastIndex) add(MenuOption("▶", "Déplacer vers la droite") { actions.move(app, 1) })
            add(MenuOption("−", "Retirer de Mes applis", "Elle reste dans Toutes les applis") { actions.removeFromDock(app) })
        }
        add(MenuOption("✕", "Masquer", "Ne s'affichera plus nulle part ; à remettre depuis Applis") { actions.hide(app) })
        add(MenuOption("ℹ", "Infos de l'appli", "Stockage, autorisations, forcer l'arrêt") { actions.showInfo(app) })
        if (app.removable) add(MenuOption("🗑", "Désinstaller", "Android demandera confirmation") { actions.uninstall(app) })
    }
    return MenuRequest(subject = app.label, options = options, returnFocus = returnFocus)
}

/** Menu d'une appli pour l'invité : l'ouvrir, sans rien changer à « Mes applis » de la famille. */
fun guestAppMenu(app: InstalledApp, open: (InstalledApp) -> Unit, returnFocus: FocusRequester?): MenuRequest =
    MenuRequest(
        subject = app.label,
        options = listOf(MenuOption("▶", "Ouvrir", "Profil Invité : « Mes applis » ne se modifie pas") { open(app) }),
        returnFocus = returnFocus,
    )

/** Menu des applis masquées, pour les remettre. */
fun hiddenAppsMenu(hidden: List<InstalledApp>, unhide: (InstalledApp) -> Unit, returnFocus: FocusRequester?): MenuRequest =
    MenuRequest(
        subject = "Applis masquées",
        options = hidden.map { app -> MenuOption("＋", "Afficher ${app.label}") { unhide(app) } },
        returnFocus = returnFocus,
    )
