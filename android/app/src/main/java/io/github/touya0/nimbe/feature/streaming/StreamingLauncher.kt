package io.github.touya0.nimbe.feature.streaming

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.touya0.nimbe.data.streaming.StreamingOffer
import java.net.URISyntaxException

/** Ce qu'a donné l'ouverture d'un titre. */
enum class LaunchResult {
    /** L'appli s'est ouverte sur le titre (fiche ou lecture). */
    Title,

    /** Lien refusé par l'appli : elle s'est ouverte sur son accueil. */
    AppOnly,

    /** Appli absente ou qui ne s'ouvre pas. */
    Failed,
}

/**
 * Ouvre un titre dans l'appli de sa plateforme. Les liens viennent de JustWatch : on n'en garde
 * que l'adresse https, envoyée à la seule appli de la plateforme (jamais à un composant choisi par le lien).
 * Comportement vérifié sur le stick : Disney+ ouvre la fiche ou lance la lecture, HBO Max lance la lecture.
 */
class StreamingLauncher(private val context: Context) {

    /** [page] : la page du titre dans l'appli plutôt que la lecture directe, quand la plateforme en a une. */
    fun launch(offer: StreamingOffer, page: Boolean): LaunchResult {
        // Sans fiche vérifiée pour cette plateforme, on ne tente que la lecture.
        val links = if (page && offer.service.pageLinks) listOfNotNull(offer.webUrl, offer.deepLink) else listOfNotNull(offer.deepLink, offer.webUrl)
        for (link in links) {
            val uri = httpsUri(link) ?: continue
            val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(offer.service.packageName)
            if (start(intent)) return LaunchResult.Title
        }
        val pm = context.packageManager
        val app = pm.getLeanbackLaunchIntentForPackage(offer.service.packageName) ?: pm.getLaunchIntentForPackage(offer.service.packageName)
        return if (app != null && start(app)) LaunchResult.AppOnly else LaunchResult.Failed
    }

    fun isInstalled(packageName: String): Boolean = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.isSuccess

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    companion object {
        /**
         * L'adresse https d'un lien JustWatch : telle quelle, ou extraite d'un lien `intent://…#Intent;scheme=https;end`.
         * Tout autre schéma est refusé.
         */
        fun httpsUri(link: String): Uri? {
            val uri = if (link.startsWith("intent:")) {
                try {
                    Intent.parseUri(link, Intent.URI_INTENT_SCHEME).data
                } catch (_: URISyntaxException) {
                    null
                }
            } else {
                Uri.parse(link)
            }
            return uri?.takeIf { it.scheme == "https" && !it.host.isNullOrEmpty() }
        }
    }
}
