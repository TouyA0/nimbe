package io.github.touya0.nimbe.feature.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** Ce qui sort du launcher vers Android : ouvrir une appli, ses infos, sa désinstallation, les réglages. */
class AppLauncher(private val context: Context, private val repository: AppsRepository) {

    fun open(app: InstalledApp) = start(repository.launchIntent(app.packageName), "Impossible d'ouvrir ${app.label}")

    fun showInfo(app: InstalledApp) = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)),
        "Impossible d'afficher les infos de ${app.label}",
    )

    /** Android demande lui-même confirmation avant de désinstaller. */
    fun uninstall(app: InstalledApp) = start(
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)),
        "Impossible de désinstaller ${app.label}",
    )

    /** Réglages Wi-Fi, ou à défaut les réglages généraux (certains sticks n'ont pas l'écran Wi-Fi direct). */
    fun openNetworkSettings() {
        if (!tryStart(Intent(Settings.ACTION_WIFI_SETTINGS))) {
            start(Intent(Settings.ACTION_SETTINGS), "Impossible d'ouvrir les réglages réseau")
        }
    }

    private fun start(intent: Intent?, error: String) {
        if (!tryStart(intent)) Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
    }

    private fun tryStart(intent: Intent?): Boolean = try {
        if (intent == null) throw ActivityNotFoundException()
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
