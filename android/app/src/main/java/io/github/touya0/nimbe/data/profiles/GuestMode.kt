package io.github.touya0.nimbe.data.profiles

import io.github.touya0.nimbe.data.layout.HomeRow
import io.github.touya0.nimbe.data.layout.LayoutItem
import io.github.touya0.nimbe.data.layout.ProfileLayout
import io.github.touya0.nimbe.data.layout.TabKeys

/**
 * Profil Invité : la TV et les films de la maison, rien de personnel. Rien n'est enregistré (ni reprise
 * de lecture envoyée à Jellyfin, ni applis récentes), pas de galerie ni de musique, pas de réglages,
 * et les applis des comptes de la famille sont masquées.
 */
object GuestMode {

    /** Applis où la famille est connectée avec ses comptes : masquées pour l'invité tant qu'on n'a rien choisi. */
    val FamilyAccountApps = setOf(
        "com.netflix.ninja",
        "com.disney.disneyplus",
        "com.amazon.amazonvideo.livingroom",
        "com.canal.android.canal",
        "com.spotify.tv.android",
        "com.deezer.android.tv",
        "com.google.android.apps.photos",
        // Achats sur le compte Google de la famille.
        "com.android.vending",
        // Réglages Android : l'invité n'a pas de réglages, ni ceux de Nimbe ni ceux du stick.
        "com.android.tv.settings",
        "com.google.android.tv.settings",
    )

    /** Mise en page fixe : l'invité n'a pas accès aux réglages pour la changer. */
    val Layout = ProfileLayout(
        homeRows = listOf(
            LayoutItem(HomeRow.Latest.key, visible = true),
            LayoutItem(HomeRow.Apps.key, visible = true),
            // Pas d'historique : la reprise de lecture des autres ne s'affiche pas non plus.
            LayoutItem(HomeRow.Continue.key, visible = false),
        ),
        tabs = TabKeys.defaults.map { LayoutItem(it, visible = it !in setOf("music", "gallery")) },
    )

    fun hiddenApps(profile: Profile): Set<String> = profile.hiddenApps ?: FamilyAccountApps
}

/** Ce que le profil actif a le droit de garder ou d'ouvrir. */
val Profile?.isGuest: Boolean get() = this?.kind == ProfileKind.Guest
