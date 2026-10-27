package io.github.touya0.nimbe.feature.screensaver

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** L'écran de veille choisi dans Android est-il celui de Nimbe ? */
object ScreensaverSetting {

    /** Valeur à donner à screensaver_components par adb pour ce paquet (version publiée ou de développement). */
    fun component(context: Context): String = ComponentName(context, NimbeDream::class.java).flattenToString()

    /** true / false, ou null si Android ne laisse pas lire le réglage. */
    fun isActive(context: Context): Boolean? = runCatching {
        val resolver = context.contentResolver
        val enabled = Settings.Secure.getInt(resolver, "screensaver_enabled", 1) == 1
        val chosen = Settings.Secure.getString(resolver, "screensaver_components").orEmpty()
        enabled && chosen.split(',').any { ComponentName.unflattenFromString(it) == ComponentName(context, NimbeDream::class.java) }
    }.getOrNull()
}
