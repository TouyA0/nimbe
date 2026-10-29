package io.github.touya0.nimbe.data.streaming

import io.github.touya0.nimbe.data.profiles.ChildMode
import io.github.touya0.nimbe.data.profiles.GuestMode
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.isChild
import io.github.touya0.nimbe.data.profiles.isGuest

/** Quelles plateformes un profil voit dans Films et Séries. */
object StreamingRules {

    /** Au-delà, un titre ouvert quitte « Continuer à regarder ». */
    const val HISTORY_DAYS = 30L

    /**
     * Une plateforme apparaît si la maison s'y est dite abonnée ([chosen], rien tant que personne n'a choisi :
     * Netflix et Prime Video sont souvent préinstallés, une appli présente ne veut pas dire un abonnement),
     * que l'appli est bien sur le stick (sinon impossible d'ouvrir quoi que ce soit), et que le profil
     * a le droit d'ouvrir cette appli : l'invité ne voit pas les comptes de la famille, l'enfant seulement ses applis autorisées.
     */
    fun visible(chosen: Set<String>?, installed: Set<String>, profile: Profile?): List<StreamingService> =
        StreamingService.entries.filter { service ->
            chosen.orEmpty().contains(service.code) && service.packageName in installed && when {
                profile.isGuest -> service.packageName !in GuestMode.hiddenApps(profile!!)
                profile.isChild -> service.packageName in ChildMode.allowedApps(profile!!)
                else -> true
            }
        }

    /** Le profil Enfant ne voit que les titres tous publics. */
    fun kidsOnly(profile: Profile?): Boolean = profile.isChild
}
