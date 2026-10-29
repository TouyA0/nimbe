package io.github.touya0.nimbe.feature.onboarding

import io.github.touya0.nimbe.data.homeassistant.HomeAssistantAccount
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.data.jellyfin.JellyfinDiscovery
import io.github.touya0.nimbe.data.jellyfin.JellyfinException
import io.github.touya0.nimbe.feature.homeassistant.HomeAssistantConnectController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit

/** Ce que la recherche a trouvé pour un service du Pi. */
sealed interface ServiceSpot {
    /** Déjà relié à cette TV ; [reachable] : il répond en ce moment. */
    data class Linked(val url: String, val reachable: Boolean) : ServiceSpot

    /** Trouvé sur le réseau, pas encore relié. */
    data class Found(val url: String) : ServiceSpot

    data object Missing : ServiceSpot
}

/** Résultat de l'étape « Serveur ». [piHost] : l'adresse du Pi, déduite des services trouvés. */
data class PiScan(val piHost: String?, val jellyfin: ServiceSpot, val homeAssistant: ServiceSpot) {
    val foundAnything: Boolean get() = jellyfin != ServiceSpot.Missing || homeAssistant != ServiceSpot.Missing
}

/**
 * Cherche le Raspberry Pi et ses services : Jellyfin par la découverte UDP, puis Home Assistant
 * sur la même machine (port 8123). Un service déjà relié à la TV n'est pas recherché, juste vérifié.
 * Aucune requête ne porte de jeton : on ne lit que des pages publiques.
 */
class PiScanner(
    private val jellyfin: JellyfinAccount,
    private val homeAssistant: HomeAssistantAccount,
    private val discovery: JellyfinDiscovery = JellyfinDiscovery(),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
) {

    /** [manualUrl] : adresse du Pi saisie par l'utilisateur (déjà normalisée), à la place de la découverte. */
    suspend fun scan(manualUrl: String? = null): PiScan = coroutineScope {
        val jellyfinLink = jellyfin.link.first()
        val homeAssistantLink = homeAssistant.link.first()
        val haLinked = homeAssistantLink?.let { link -> async { ServiceSpot.Linked(link.serverUrl, isHomeAssistant(link.serverUrl)) } }

        val jellyfinSpot = when {
            jellyfinLink != null -> ServiceSpot.Linked(jellyfinLink.serverUrl, respondsAsJellyfin(jellyfinLink.serverUrl))
            manualUrl != null -> if (respondsAsJellyfin(manualUrl)) ServiceSpot.Found(manualUrl) else ServiceSpot.Missing
            else -> discovery.find().firstNotNullOfOrNull { resolveDiscovered(jellyfin, it) }
                ?.let { ServiceSpot.Found(it.url) } ?: ServiceSpot.Missing
        }

        // Home Assistant tourne sur le même Pi que Jellyfin : on essaie cette machine, ou celle saisie.
        val haSpot = haLinked?.await() ?: run {
            val host = manualUrl?.let(::hostOf) ?: jellyfinSpot.url?.let(::hostOf)
            val candidate = host?.let(::homeAssistantUrl)
            if (candidate != null && isHomeAssistant(candidate)) ServiceSpot.Found(candidate) else ServiceSpot.Missing
        }

        PiScan(
            piHost = piHost(jellyfinSpot, haSpot),
            jellyfin = jellyfinSpot,
            homeAssistant = haSpot,
        )
    }

    private suspend fun respondsAsJellyfin(url: String): Boolean = try {
        jellyfin.api.publicInfo(url)
        true
    } catch (_: JellyfinException) {
        false
    }

    /** La page publique manifest.json de Home Assistant porte son nom : rien à deviner sur le port. */
    private suspend fun isHomeAssistant(url: String): Boolean = withContext(Dispatchers.IO) {
        val request = try {
            Request.Builder().url("$url/manifest.json").build()
        } catch (_: IllegalArgumentException) {
            return@withContext false
        }
        try {
            http.newCall(request).execute().use { response ->
                response.isSuccessful && isHomeAssistantManifest(response.body?.string().orEmpty())
            }
        } catch (_: IOException) {
            false
        }
    }

    companion object {

        /** Hôte d'une adresse de service, entre crochets pour l'IPv6. */
        fun hostOf(url: String): String? = try {
            URI(url).host?.takeIf { it.isNotEmpty() }
        } catch (_: URISyntaxException) {
            null
        }

        fun homeAssistantUrl(host: String): String = "http://$host:${HomeAssistantConnectController.HOME_ASSISTANT_PORT}"

        fun isHomeAssistantManifest(text: String): Boolean = try {
            JSONObject(text).optString("name") == "Home Assistant"
        } catch (_: JSONException) {
            false
        }

        /** Le Pi, c'est la machine de Jellyfin, sinon celle de Home Assistant ; un service qui répond passe avant. */
        fun piHost(jellyfin: ServiceSpot, homeAssistant: ServiceSpot): String? =
            listOf(jellyfin, homeAssistant).sortedByDescending { it.responds }.firstNotNullOfOrNull { spot -> spot.url?.let(::hostOf) }
    }
}

val ServiceSpot.url: String?
    get() = when (this) {
        is ServiceSpot.Linked -> url
        is ServiceSpot.Found -> url
        ServiceSpot.Missing -> null
    }

/** Trouvé à l'instant, ou relié et joignable. */
val ServiceSpot.responds: Boolean
    get() = this is ServiceSpot.Found || (this is ServiceSpot.Linked && reachable)
