package io.github.touya0.nimbe.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.touya0.nimbe.feature.apps.AppsRepository
import io.github.touya0.nimbe.feature.apps.AppsScreen
import io.github.touya0.nimbe.feature.apps.InstalledApp
import io.github.touya0.nimbe.feature.home.ComingSoonScreen
import io.github.touya0.nimbe.feature.home.HomeScreen
import io.github.touya0.nimbe.ui.theme.Nuit
import kotlinx.coroutines.launch

@Composable
fun NimbeApp(repository: AppsRepository, onOpen: (InstalledApp) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val requesters = remember { Tab.entries.associateWith { FocusRequester() } }
    fun focusTab(target: Tab) {
        tab = target
        runCatching { requesters.getValue(target).requestFocus() }
    }

    // Recharge la liste à chaque retour sur le launcher : une appli a pu être installée ou retirée.
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(repository) {
        val job = scope.launch { apps = repository.load() }
        onPauseOrDispose { job.cancel() }
    }

    // Retour ramène à l'Accueil ; sur l'Accueil, il ne fait rien : c'est l'écran d'accueil du stick.
    BackHandler { focusTab(Tab.Home) }

    LaunchedEffect(Unit) { focusTab(tab) }

    Column(Modifier.fillMaxSize().background(Nuit)) {
        Header(selected = tab, onSelect = { tab = it }, requesters = requesters)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.Home -> HomeScreen(apps = apps, onOpen = onOpen, onAllApps = { focusTab(Tab.Apps) })
                Tab.Apps -> AppsScreen(apps = apps, onOpen = onOpen)
                Tab.Tv -> ComingSoonScreen("TV en direct", "Les chaînes et le guide des programmes arrivent avec la gateway du Pi.")
                Tab.Movies -> ComingSoonScreen("Films", "Ta bibliothèque Jellyfin arrive bientôt.")
                Tab.Series -> ComingSoonScreen("Séries", "Tes séries Jellyfin arrivent bientôt.")
                Tab.Music -> ComingSoonScreen("Musique", "Spotify arrive bientôt.")
                Tab.Gallery -> ComingSoonScreen("Galerie", "Tes photos Nextcloud arrivent bientôt.")
            }
        }
    }
}
