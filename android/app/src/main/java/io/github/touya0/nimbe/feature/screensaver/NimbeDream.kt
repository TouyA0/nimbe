package io.github.touya0.nimbe.feature.screensaver

import android.service.dreams.DreamService
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.touya0.nimbe.NimbeApplication
import io.github.touya0.nimbe.core.network.ConnectivityMonitor
import io.github.touya0.nimbe.core.network.rememberConnectivity
import io.github.touya0.nimbe.core.storage.LauncherPrefs
import io.github.touya0.nimbe.feature.status.ServerStatus
import io.github.touya0.nimbe.ui.theme.DisplaySettings
import io.github.touya0.nimbe.ui.theme.NimbeTheme
import kotlinx.coroutines.delay

/**
 * Écran de veille de Nimbe, déclenché par Android après l'inactivité. Compose a besoin d'un cycle de vie
 * et d'un registre d'état sur la vue : un DreamService n'en fournit pas, on les tient ici.
 * Toute touche de la télécommande le ferme (comportement par défaut d'un DreamService non interactif).
 */
class NimbeDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        // Pas de lumière inutile la nuit : Android baisse la luminosité si l'écran le permet.
        isScreenBright = false
        val app = application as NimbeApplication
        val prefs = LauncherPrefs(this)
        val connectivity = ConnectivityMonitor(this)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@NimbeDream)
            setViewTreeSavedStateRegistryOwner(this@NimbeDream)
            setContent {
                val display by prefs.display.collectAsStateWithLifecycle(initialValue = DisplaySettings())
                val network by rememberConnectivity(connectivity)
                val ha by app.homeAssistantMonitor.live.collectAsStateWithLifecycle()
                val jellyfin by app.library.health.collectAsStateWithLifecycle()
                // Jellyfin vérifié comme sur l'accueil : sans cela, un Pi coupé pendant la veille passerait inaperçu.
                LaunchedEffect(Unit) {
                    while (true) {
                        app.library.check()
                        delay(CHECK_INTERVAL_MS)
                    }
                }
                NimbeTheme(display) {
                    ScreensaverContent(ServerStatus.verdict(network.state, jellyfinDown = !jellyfin.reachable, ha), ha)
                }
            }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 30_000L
    }
}
