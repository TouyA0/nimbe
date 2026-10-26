package io.github.touya0.nimbe.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Suit la connexion du stick. On s'appuie sur la vérification d'Android (capacité VALIDATED, obtenue
 * par sa propre requête de test) plutôt que d'interroger nous-mêmes un serveur public.
 */
class ConnectivityMonitor(context: Context) {

    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    val states: Flow<NetworkState> = callbackFlow {
        fun stateOf(capabilities: NetworkCapabilities?): NetworkState = when {
            capabilities == null -> NetworkState.NoNetwork
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkState.Online
            else -> NetworkState.NoInternet
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(stateOf(capabilities))
            }

            override fun onLost(network: Network) {
                trySend(NetworkState.NoNetwork)
            }
        }

        trySend(stateOf(manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }))
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
