package io.github.touya0.nimbe.feature.apps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch

/**
 * Liste des applis installées, null pendant le premier chargement.
 * Rechargée à chaque retour sur le launcher : une appli a pu être installée ou retirée.
 */
@Composable
fun rememberInstalledApps(repository: AppsRepository): List<InstalledApp>? {
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(repository) {
        val job = scope.launch { apps = repository.load() }
        onPauseOrDispose { job.cancel() }
    }
    return apps
}
