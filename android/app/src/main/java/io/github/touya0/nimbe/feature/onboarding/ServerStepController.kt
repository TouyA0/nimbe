package io.github.touya0.nimbe.feature.onboarding

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.touya0.nimbe.data.jellyfin.ServerAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface ServerStep {
    data object Scanning : ServerStep
    data class Result(val scan: PiScan) : ServerStep

    /** Saisie de l'adresse du Pi ; [previous] : le résultat à retrouver avec Retour. */
    data class Manual(val previous: PiScan?) : ServerStep
}

/** Étape « Serveur » du premier lancement : chercher le Pi tout seul, sinon à l'adresse saisie. */
@Stable
class ServerStepController(private val scanner: PiScanner, private val scope: CoroutineScope) {

    var step by mutableStateOf<ServerStep>(ServerStep.Scanning)
        private set

    /** Recherche à l'adresse saisie en cours. */
    var checking by mutableStateOf(false)
        private set
    var addressError by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    fun scan() = launch {
        step = ServerStep.Scanning
        step = ServerStep.Result(scanner.scan())
    }

    fun enterAddress() {
        job?.cancel()
        checking = false
        addressError = null
        step = ServerStep.Manual((step as? ServerStep.Result)?.scan)
    }

    /** Retour depuis la saisie : le dernier résultat, ou une nouvelle recherche s'il n'y en a pas. */
    fun leaveAddress() {
        val previous = (step as? ServerStep.Manual)?.previous
        if (previous != null) {
            job?.cancel()
            checking = false
            step = ServerStep.Result(previous)
        } else {
            scan()
        }
    }

    fun tryAddress(input: String) {
        val url = ServerAddress.parse(input).getOrElse {
            addressError = it.message
            return
        }
        addressError = null
        launch {
            checking = true
            val scan = try {
                scanner.scan(manualUrl = url)
            } finally {
                checking = false
            }
            if (scan.foundAnything) {
                step = ServerStep.Result(scan)
            } else {
                addressError = "Ni Jellyfin ni Home Assistant ne répondent à cette adresse"
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }
}
