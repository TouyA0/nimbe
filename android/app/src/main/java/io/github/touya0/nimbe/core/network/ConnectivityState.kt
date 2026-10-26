package io.github.touya0.nimbe.core.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.runningFold

/** État de connexion à afficher, avec l'heure de début d'une coupure. */
@Composable
fun rememberConnectivity(monitor: ConnectivityMonitor): State<ConnectivityStatus> {
    val flow = remember(monitor) {
        monitor.states.runningFold(ConnectivityStatus.Initial) { previous, state ->
            ConnectivityStatus.next(previous, state, System.currentTimeMillis())
        }
    }
    return flow.collectAsStateWithLifecycle(initialValue = ConnectivityStatus.Initial)
}
