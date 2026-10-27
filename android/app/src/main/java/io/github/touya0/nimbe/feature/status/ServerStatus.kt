package io.github.touya0.nimbe.feature.status

import io.github.touya0.nimbe.core.network.NetworkState
import io.github.touya0.nimbe.data.homeassistant.HaConnection
import io.github.touya0.nimbe.data.homeassistant.HaLive
import io.github.touya0.nimbe.data.homeassistant.SensorCatalog
import io.github.touya0.nimbe.data.homeassistant.SensorKind

/** Le voyant de l'en-tête, du plus grave au plus rassurant. */
enum class ServerLevel {
    /** Plus de Wi-Fi ni de câble. */
    NoNetwork,

    /** Ni Jellyfin ni Home Assistant ne répondent : le Pi est coupé (ou injoignable). */
    PiUnreachable,

    /** Le Pi répond, mais un service est en panne. */
    ServiceDown,

    NoInternet,

    /** Home Assistant ne répond plus, ou un capteur ne donne pas de valeur : on ne sait pas si tout va bien. */
    Unknown,

    /** Home Assistant en direct, toutes les mesures connues, aucun service en panne. */
    Ok,

    /** Rien à dire : Home Assistant pas relié (ou première connexion en cours), et rien en panne. */
    Hidden,
}

/** [down] : noms des services en panne, pour le voyant (« Jellyfin en panne »). */
data class ServerVerdict(val level: ServerLevel, val down: List<String> = emptyList())

/**
 * Choisit le voyant. Règle du dossier : « inconnu » n'est jamais affiché comme « OK ». Le vert demande
 * une connexion en direct à Home Assistant (ses pings toutes les 30 s garantissent qu'elle est vivante)
 * et une valeur pour chaque capteur suivi.
 *
 * Jellyfin est aussi vérifié directement par la TV : s'il ne répond plus alors que Home Assistant répond,
 * le Pi est allumé et c'est Jellyfin qui est en panne ; si les deux se taisent, c'est le Pi.
 */
object ServerStatus {

    fun verdict(network: NetworkState, jellyfinDown: Boolean, ha: HaLive): ServerVerdict {
        if (network == NetworkState.NoNetwork) return ServerVerdict(ServerLevel.NoNetwork)
        val live = ha.connection is HaConnection.Live
        if (jellyfinDown && !live) return ServerVerdict(ServerLevel.PiUnreachable)

        val followed = ha.sensors.map { ha.entities[it] }
        val down = buildList {
            if (jellyfinDown) add(JELLYFIN)
            if (live) {
                followed.filterNotNull()
                    .filter { SensorCatalog.kindOf(it) == SensorKind.Service && it.state == "off" }
                    .forEach { add(it.serviceName) }
            }
        }.distinctBy { it.lowercase() }
        if (down.isNotEmpty()) return ServerVerdict(ServerLevel.ServiceDown, down)
        if (network == NetworkState.NoInternet) return ServerVerdict(ServerLevel.NoInternet)
        return when (ha.connection) {
            is HaConnection.Lost -> ServerVerdict(ServerLevel.Unknown)
            // Juste connecté, les premières valeurs arrivent dans l'instant : pas d'« inconnu » éclair.
            is HaConnection.Live if ha.entities.isEmpty() -> ServerVerdict(ServerLevel.Hidden)
            is HaConnection.Live ->
                if (followed.any { it == null || it.unknown }) ServerVerdict(ServerLevel.Unknown) else ServerVerdict(ServerLevel.Ok)
            HaConnection.Off, HaConnection.Connecting -> ServerVerdict(ServerLevel.Hidden)
        }
    }

    private const val JELLYFIN = "Jellyfin"
}
