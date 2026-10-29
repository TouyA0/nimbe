package io.github.touya0.nimbe.data.profiles

import io.github.touya0.nimbe.data.layout.HomeRow
import io.github.touya0.nimbe.data.layout.LayoutItem
import io.github.touya0.nimbe.data.layout.ProfileLayout
import io.github.touya0.nimbe.data.layout.TabKeys

/**
 * Profil Enfant : un accueil simplifié, seulement les applis autorisées par un parent, pas de réglages,
 * et sortie du profil par le PIN parent. Jellyfin reste entier pour l'instant (choix du 27/10) :
 * le filtrage par âge viendra avec un utilisateur Jellyfin dédié.
 */
object ChildMode {

    /** Applis faites pour les enfants, autorisées tant qu'un parent n'a rien choisi. */
    val KidsApps = setOf(
        "com.google.android.youtube.tvkids",
    )

    /**
     * Mise en page fixe : films et séries, les applis autorisées. Pas de « Continuer à regarder »
     * (c'est la reprise de toute la maison). La Galerie n'apparaît que si un parent
     * a choisi un album pour l'enfant ([Profile.gallery]).
     */
    fun layout(profile: Profile?): ProfileLayout =
        if (profile?.gallery == null) Layout else Layout.copy(tabs = Layout.tabs.map { if (it.id == "gallery") it.copy(visible = true) else it })

    private val Layout = ProfileLayout(
        homeRows = listOf(
            LayoutItem(HomeRow.Apps.key, visible = true),
            LayoutItem(HomeRow.Latest.key, visible = true),
            LayoutItem(HomeRow.Continue.key, visible = false),
            // Pas de galerie : ni les photos de la famille, ni leurs souvenirs.
            LayoutItem(HomeRow.Memories.key, visible = false),
        ),
        tabs = TabKeys.defaults.map { LayoutItem(it, visible = it in setOf(TabKeys.HOME, "movies", "series", TabKeys.APPS)) },
    )

    fun allowedApps(profile: Profile): Set<String> = profile.allowedApps ?: KidsApps
}

val Profile?.isChild: Boolean get() = this?.kind == ProfileKind.Child

/** Profils sans réglages, qui ne modifient pas « Mes applis » de la famille. */
val Profile?.isRestricted: Boolean get() = isGuest || isChild

/**
 * Passer de [from] à [to] demande le PIN parent : quitter un profil Enfant pour un autre profil,
 * dès qu'un PIN parent existe. Sans PIN, rien ne protège la sortie (Réglages → Sécurité le signale).
 */
fun leavingNeedsPin(from: Profile?, to: Profile, pinSet: Boolean): Boolean =
    pinSet && from.isChild && from?.id != to.id
