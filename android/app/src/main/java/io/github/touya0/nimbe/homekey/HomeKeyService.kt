package io.github.touya0.nimbe.homekey

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import io.github.touya0.nimbe.MainActivity
import io.github.touya0.nimbe.crashguard.CrashGuard
import io.github.touya0.nimbe.crashguard.StockLauncher

/**
 * Sur Google TV, Android donne toujours la touche Accueil à l'accueil d'origine, même quand Nimbe
 * est l'accueil par défaut. Ce service d'accessibilité la récupère, et ne lit que cette touche :
 * il ne voit ni le contenu de l'écran ni les autres touches.
 *
 * Il tourne dans le processus de Nimbe : si Nimbe plante au démarrage, le service ne démarre pas
 * et la touche revient d'elle-même à Google TV.
 *
 * Activation, une fois (le nom de la classe ne doit plus changer, sinon il faut la refaire) :
 * adb shell settings put secure enabled_accessibility_services io.github.touya0.nimbe/io.github.touya0.nimbe.homekey.HomeKeyService
 * adb shell settings put secure accessibility_enabled 1
 */
class HomeKeyService : AccessibilityService() {

    /** Décidé à l'appui, appliqué au relâchement : on ne prend jamais la moitié d'un appui. */
    private var taking = false

    override fun onServiceConnected() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bootCount = runCatching { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
        val lastOpened = prefs.getInt(KEY_LAST_BOOT, -1).takeIf { it >= 0 }
        if (HomeKeyPolicy.opensAtBoot(bootCount, lastOpened, CrashGuard.currentMode(this))) {
            prefs.edit().putInt(KEY_LAST_BOOT, bootCount ?: -1).apply()
            openNimbe()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                taking = HomeKeyPolicy.takesHomeKey(CrashGuard.currentMode(this))
            }
            KeyEvent.ACTION_UP -> if (taking) {
                taking = false
                val held = event.eventTime - event.downTime
                when (HomeKeyPolicy.target(held, ViewConfiguration.getLongPressTimeout().toLong())) {
                    HomeTarget.Nimbe -> openNimbe()
                    HomeTarget.Stock -> StockLauncher.open(this)
                }
                return true
            }
        }
        return taking
    }

    /** Comme un vrai appui sur Accueil : MainActivity revient à l'onglet Accueil (voir onNewIntent). */
    private fun openNimbe() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(home)
        } catch (_: ActivityNotFoundException) {
            StockLauncher.open(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private const val PREFS = "homekey"
        private const val KEY_LAST_BOOT = "last_boot"

        /** Le service est-il activé dans Android ? Pour l'afficher dans les réglages. */
        fun isEnabled(context: Context): Boolean {
            val own = ComponentName(context, HomeKeyService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return enabled.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == own }
        }
    }
}
