package io.github.touya0.nimbe.crashguard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.touya0.nimbe.NimbeApplication

/**
 * L'accueil d'origine du stick (celui du fabricant ou de Google), jamais désactivé :
 * c'est la sortie de secours quand Nimbe ne fonctionne plus.
 */
object StockLauncher {

    fun intent(context: Context): Intent? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val candidates = context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            // Ni ce Nimbe ni l'autre (version publiée ou de développement) : ce ne sont pas des secours.
            .filterNot { it.activityInfo.packageName.startsWith(NimbeApplication.BASE_PACKAGE) }
            // Priorité négative : écrans de transition du système (ex. FallbackHome des Réglages), pas un vrai accueil.
            .filter { it.priority >= 0 }
        val best = candidates
            .sortedWith(
                compareByDescending<android.content.pm.ResolveInfo> {
                    it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                }.thenByDescending { it.priority },
            )
            .firstOrNull() ?: return null
        return Intent(home)
            .setClassName(best.activityInfo.packageName, best.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Ouvre l'accueil d'origine, ou à défaut les réglages Android pour en choisir un. */
    fun open(context: Context) {
        val target = intent(context) ?: Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(target)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
