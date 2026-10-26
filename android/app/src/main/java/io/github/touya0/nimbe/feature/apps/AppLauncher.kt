package io.github.touya0.nimbe.feature.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** Ce qui sort du launcher vers Android : ouvrir une appli, ses infos, sa désinstallation. */
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

    private fun start(intent: Intent?, error: String) {
        try {
            if (intent == null) throw ActivityNotFoundException()
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }
    }
}
