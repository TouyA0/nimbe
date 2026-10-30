package io.github.touya0.nimbe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.touya0.nimbe.core.network.ConnectivityMonitor
import io.github.touya0.nimbe.core.storage.LauncherPrefs
import io.github.touya0.nimbe.crashguard.CrashGuard
import io.github.touya0.nimbe.crashguard.RescueScreen
import io.github.touya0.nimbe.crashguard.SafeModeScreen
import io.github.touya0.nimbe.crashguard.StartupMode
import io.github.touya0.nimbe.crashguard.StockLauncher
import io.github.touya0.nimbe.data.notifications.NewNotification
import io.github.touya0.nimbe.data.notifications.NotificationLevel
import io.github.touya0.nimbe.data.notifications.NotificationType
import io.github.touya0.nimbe.data.notifications.Presence
import io.github.touya0.nimbe.feature.apps.AppLauncher
import io.github.touya0.nimbe.feature.apps.AppsRepository
import io.github.touya0.nimbe.feature.apps.rememberInstalledApps
import io.github.touya0.nimbe.feature.notifications.BannerHost
import io.github.touya0.nimbe.shell.NimbeApp
import io.github.touya0.nimbe.ui.theme.DisplaySettings
import io.github.touya0.nimbe.ui.theme.NimbeTheme
import io.github.touya0.nimbe.update.UpdateController
import io.github.touya0.nimbe.update.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val apps by lazy { AppsRepository(applicationContext) }
    private val notifications by lazy { (application as NimbeApplication).notifications }
    private val database by lazy { (application as NimbeApplication).database }
    private val launcher by lazy { AppLauncher(this, apps, database.appLaunches(), lifecycleScope) }
    private val prefs by lazy { LauncherPrefs(applicationContext) }
    private val connectivity by lazy { ConnectivityMonitor(applicationContext) }
    private val updates by lazy { UpdateController(this, Updater(applicationContext), lifecycleScope) }
    private var mode by mutableStateOf(StartupMode.Normal)

    /** Un appui sur Accueil pendant que Nimbe tourne déjà : retour à l'onglet Accueil. */
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** La TV vient de s'allumer : c'est là qu'on demande « Qui regarde ? ». */
    private val screenOn = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn.tryEmit(Unit)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = CrashGuard.startupMode(this)
        simulateCrashIfAsked(intent)
        postTestNotificationIfAsked(intent)
        ContextCompat.registerReceiver(this, screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)

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
                    StartupMode.Normal -> Box(Modifier.fillMaxSize()) { NimbeApp(
                        repository = apps,
                        prefs = prefs,
                        launcher = launcher,
                        connectivityMonitor = connectivity,
                        launches = database.appLaunches(),
                        updates = updates,
                        jellyfin = (application as NimbeApplication).jellyfin,
                        library = (application as NimbeApplication).library,
                        homeAssistant = (application as NimbeApplication).homeAssistant,
                        homeAssistantMonitor = (application as NimbeApplication).homeAssistantMonitor,
                        nextcloud = (application as NimbeApplication).nextcloud,
                        gallery = (application as NimbeApplication).gallery,
                        profiles = (application as NimbeApplication).profiles,
                        parentPin = (application as NimbeApplication).parentPin,
                        layouts = (application as NimbeApplication).layouts,
                        screenTime = (application as NimbeApplication).screenTime,
                        streaming = (application as NimbeApplication).streaming,
                        streamingHistory = database.streamingHistory(),
                        homePresses = homePresses,
                        screenOn = screenOn,
                        notifications = notifications,
                    )
                        BannerHost(notifications)
                    }
                }
            }
        }
    }

    /** Le bandeau se place par-dessus cette activité ; ailleurs, c'est [io.github.touya0.nimbe.feature.notifications.BannerOverlay]. */
    override fun onStart() {
        super.onStart()
        notifications.presence.value = Presence.Browsing
    }

    override fun onStop() {
        notifications.presence.value = Presence.OtherApp
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOnReceiver)
        super.onDestroy()
    }

    /**
     * Touche fléchée maintenue : la télécommande répète l'appui bien plus vite que l'œil ne suit,
     * et le focus file au-delà de ce qu'on visait. On ne laisse passer qu'une répétition toutes les [REPEAT_MS].
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && event.keyCode in DPAD_KEYS) {
            if (event.eventTime - lastRepeatAt < REPEAT_MS) return true
            lastRepeatAt = event.eventTime
        }
        // Un bandeau critique ne prend pas le focus (le film continue) : Retour le ferme.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && notifications.banner.value?.persistent == true) {
                notifications.dismissBanner()
                swallowBackUp = true
                return true
            }
            if (event.action == KeyEvent.ACTION_UP && swallowBackUp) {
                swallowBackUp = false
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private var lastRepeatAt = 0L
    private var swallowBackUp = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        postTestNotificationIfAsked(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.tryEmit(Unit)
    }

    private fun restartNormally() {
        CrashGuard.leaveSafeMode(this)
        mode = StartupMode.Normal
    }

    /**
     * Pour tester le garde-fou, uniquement dans les versions de développement :
     * adb shell am start -S -n io.github.touya0.nimbe.debug/io.github.touya0.nimbe.MainActivity --ez nimbe.simulate_crash true
     * Le -S arrête d'abord l'appli : sans lui, l'activité (singleTask) déjà ouverte reçoit l'intent sans passer par onCreate.
     */
    private fun simulateCrashIfAsked(intent: Intent?) {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && intent?.getBooleanExtra(EXTRA_SIMULATE_CRASH, false) == true) {
            throw IllegalStateException("Plantage simulé pour tester le garde-fou")
        }
    }

    /**
     * Pour essayer les bandeaux, uniquement dans les versions de développement (niveau Critical, Normal ou Discreet) :
     * adb shell am start -n io.github.touya0.nimbe.debug/io.github.touya0.nimbe.MainActivity --es nimbe.test_notification Critical
     * Ajouter --ei nimbe.test_delay 8 laisse le temps d'ouvrir une autre appli, pour voir le bandeau par-dessus.
     */
    private fun postTestNotificationIfAsked(intent: Intent?) {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val level = intent?.getStringExtra(EXTRA_TEST_NOTIFICATION)?.takeIf { debuggable } ?: return
        val wait = intent.getIntExtra(EXTRA_TEST_DELAY, 0) * 1000L
        (application as NimbeApplication).scope.launch {
            delay(wait)
            notifications.post(
                NewNotification(
                    key = "test:${System.currentTimeMillis()}",
                    type = NotificationType.Home,
                    level = NotificationLevel.valueOf(level),
                    kicker = "ESSAI",
                    title = "Notification d'essai",
                    body = "Niveau $level",
                ),
            )
        }
    }

    private companion object {
        const val EXTRA_SIMULATE_CRASH = "nimbe.simulate_crash"
        const val EXTRA_TEST_NOTIFICATION = "nimbe.test_notification"
        const val EXTRA_TEST_DELAY = "nimbe.test_delay"
        const val REPEAT_MS = 85L
        val DPAD_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
    }
}
