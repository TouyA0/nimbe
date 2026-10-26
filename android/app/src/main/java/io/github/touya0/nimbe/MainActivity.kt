package io.github.touya0.nimbe

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.touya0.nimbe.core.network.ConnectivityMonitor
import io.github.touya0.nimbe.core.storage.LauncherPrefs
import io.github.touya0.nimbe.crashguard.CrashGuard
import io.github.touya0.nimbe.crashguard.RescueScreen
import io.github.touya0.nimbe.crashguard.SafeModeScreen
import io.github.touya0.nimbe.crashguard.StartupMode
import io.github.touya0.nimbe.crashguard.StockLauncher
import io.github.touya0.nimbe.feature.apps.AppLauncher
import io.github.touya0.nimbe.feature.apps.AppsRepository
import io.github.touya0.nimbe.feature.apps.rememberInstalledApps
import io.github.touya0.nimbe.shell.NimbeApp
import io.github.touya0.nimbe.ui.theme.DisplaySettings
import io.github.touya0.nimbe.ui.theme.NimbeTheme

class MainActivity : ComponentActivity() {

    private val apps by lazy { AppsRepository(applicationContext) }
    private val launcher by lazy { AppLauncher(this, apps) }
    private val prefs by lazy { LauncherPrefs(applicationContext) }
    private val connectivity by lazy { ConnectivityMonitor(applicationContext) }
    private var mode by mutableStateOf(StartupMode.Normal)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = CrashGuard.startupMode(this)
        simulateCrashIfAsked(intent)

        setContent {
            // Réglages d'affichage appliqués partout, y compris aux écrans Secours et sans échec.
            val display by prefs.display.collectAsStateWithLifecycle(initialValue = DisplaySettings())
            NimbeTheme(display) {
                when (mode) {
                    StartupMode.Rescue -> RescueScreen(
                        onOpenStock = {
                            // Au retour sur Nimbe, on arrive dans le mode sans échec déjà enregistré.
                            mode = StartupMode.SafeMode
                            StockLauncher.open(this)
                        },
                        onRetry = ::restartNormally,
                    )
                    StartupMode.SafeMode -> SafeModeScreen(
                        apps = rememberInstalledApps(apps),
                        lastError = CrashGuard.lastError(this),
                        onOpen = launcher::open,
                        onRestartNormally = ::restartNormally,
                        onOpenStock = { StockLauncher.open(this) },
                    )
                    StartupMode.Normal -> NimbeApp(
                        repository = apps,
                        prefs = prefs,
                        launcher = launcher,
                        connectivityMonitor = connectivity,
                    )
                }
            }
        }
    }

    private fun restartNormally() {
        CrashGuard.leaveSafeMode(this)
        mode = StartupMode.Normal
    }

    /**
     * Pour tester le garde-fou, uniquement dans les versions de développement :
     * adb shell am start -n io.github.touya0.nimbe/.MainActivity --ez nimbe.simulate_crash true
     */
    private fun simulateCrashIfAsked(intent: Intent?) {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && intent?.getBooleanExtra(EXTRA_SIMULATE_CRASH, false) == true) {
            throw IllegalStateException("Plantage simulé pour tester le garde-fou")
        }
    }

    private companion object {
        const val EXTRA_SIMULATE_CRASH = "nimbe.simulate_crash"
    }
}
