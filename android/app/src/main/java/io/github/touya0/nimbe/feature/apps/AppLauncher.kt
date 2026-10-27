package io.github.touya0.nimbe.feature.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import io.github.touya0.nimbe.core.storage.AppLaunchDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ce qui sort du launcher vers Android : ouvrir une appli, ses infos, sa désinstallation, les réglages.
 * Chaque ouverture réussie est notée pour « Récemment utilisées ».
 */
class AppLauncher(
    private val context: Context,
    private val repository: AppsRepository,
    private val launches: AppLaunchDao,
    private val scope: CoroutineScope,
) {

    fun open(app: InstalledApp) {
        if (tryStart(repository.launchIntent(app.packageName))) {
            scope.launch { launches.recordLaunch(app.packageName, System.currentTimeMillis()) }
        } else {
            Toast.makeText(context, "Impossible d'ouvrir ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

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

    fun openAccessibilitySettings() = start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Impossible d'ouvrir l'accessibilité")

    /** Choix de l'écran de veille dans Android ; Google TV n'a pas cet écran, on ouvre alors les réglages. */
    fun openScreensaverSettings() {
        if (!tryStart(Intent(Settings.ACTION_DREAM_SETTINGS))) openAndroidSettings()
    }

    /**
     * « Affichage et son » de Google TV, où se trouve HDMI-CEC. Depuis Android 12, les réglages CEC sont
     * privés au système : une appli ne peut ni les lire ni les changer, seulement ouvrir cet écran.
     */
    fun openDisplaySoundSettings() {
        if (!tryStart(Intent(Settings.ACTION_SOUND_SETTINGS))) openAndroidSettings()
    }

    fun openAndroidSettings() = start(Intent(Settings.ACTION_SETTINGS), "Impossible d'ouvrir les réglages Android")

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
