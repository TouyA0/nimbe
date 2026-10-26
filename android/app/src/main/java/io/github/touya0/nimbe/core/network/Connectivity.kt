package io.github.touya0.nimbe.core.network

/** État de la connexion du stick, tel que l'affiche le launcher. */
enum class NetworkState {
    /** Pas encore mesuré, au démarrage : on n'affiche rien plutôt qu'une fausse alerte. */
    Unknown,

    /** Connecté et internet vérifié par Android. */
    Online,

    /** Connecté au réseau de la maison, mais internet ne répond pas (box coupée, panne du fournisseur). */
    NoInternet,

    /** Plus de Wi-Fi ni de câble. */
    NoNetwork,
}

/** [since] : heure (ms) à laquelle la connexion s'est dégradée, null quand tout va bien. */
data class ConnectivityStatus(val state: NetworkState, val since: Long?) {
    val offline: Boolean get() = state == NetworkState.NoInternet || state == NetworkState.NoNetwork

    companion object {
        val Initial = ConnectivityStatus(NetworkState.Unknown, since = null)

        /**
         * Nouvel état à partir du précédent. L'heure de début d'une coupure est conservée
         * quand on passe de « pas d'internet » à « pas de réseau » (et inversement) : c'est la même panne.
         */
        fun next(previous: ConnectivityStatus, state: NetworkState, now: Long): ConnectivityStatus = when (state) {
            NetworkState.Online, NetworkState.Unknown -> ConnectivityStatus(state, since = null)
            else -> ConnectivityStatus(state, since = previous.since.takeIf { previous.offline } ?: now)
        }
    }
}

/** Applis qui ne servent à rien sans internet : marquées « Hors ligne » pendant une coupure. */
object InternetApps {
    private val packages = setOf(
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.disney.disneyplus",
        "com.spotify.tv.android",
        "com.amazon.amazonvideo.livingroom",
        "deezer.android.tv",
        "com.canal.android.canal",
        "tv.twitch.android.app",
        "tv.arte.plus7",
    )

    fun needsInternet(packageName: String): Boolean = packageName in packages
}
