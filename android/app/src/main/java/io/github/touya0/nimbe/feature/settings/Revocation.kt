package io.github.touya0.nimbe.feature.settings

import io.github.touya0.nimbe.data.homeassistant.HomeAssistantAccount
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.data.nextcloud.NextcloudAccounts
import kotlinx.coroutines.flow.first

/** Ce que « Révoquer tous les accès » a fait pour un service. [onServer] : le serveur a lui-même retiré l'accès. */
data class Revocation(val service: String, val onServer: Boolean, val ifNot: String)

/**
 * Coupe l'accès de cette TV à chaque service relié : révoque le jeton côté serveur quand il répond,
 * et l'oublie sur la TV dans tous les cas. Profils, PIN parent et réglages restent.
 * Nextcloud : chaque galerie reliée (une par profil adulte). Spotify s'ajoutera ici quand il sera relié.
 */
suspend fun revokeAll(jellyfin: JellyfinAccount, homeAssistant: HomeAssistantAccount, nextcloud: NextcloudAccounts): List<Revocation> = buildList {
    if (jellyfin.link.first() != null) {
        add(Revocation("Jellyfin", jellyfin.disconnect(), "retire aussi « Nimbe » dans Tableau de bord → Appareils"))
    }
    if (homeAssistant.link.first() != null) {
        add(Revocation("Home Assistant", homeAssistant.disconnect(), "supprime aussi le jeton dans Profil → Sécurité du compte"))
    }
    nextcloud.links.first().forEach { (profileId, link) ->
        add(
            Revocation(
                "Nextcloud (${link.loginName})",
                nextcloud.disconnect(profileId),
                "retire aussi « Nimbe » dans Paramètres → Sécurité → Appareils et sessions",
            ),
        )
    }
}

/** Une ligne par service, pour l'écran : ce qui est fait, et ce qu'il reste à faire à la main. */
fun Revocation.summary(): String =
    if (onServer) "$service : accès révoqué sur le serveur" else "$service : oublié sur la TV, serveur injoignable · $ifNot"
