package io.github.touya0.nimbe.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Ce que fait la mise à jour en ce moment, pour la barre de progression. */
sealed interface UpdateProgress {
    data object Checking : UpdateProgress
    data class Downloading(val received: Long, val total: Long) : UpdateProgress
    data object Verifying : UpdateProgress
}

/** Actions des écrans de mise à jour, lancées dans la portée de l'activité. */
@Stable
class UpdateController(private val context: Context, private val updater: Updater, private val scope: CoroutineScope) {

    val state: Flow<UpdateState> = updater.store.state

    /** Lu une fois : la version installée ne change pas sans redémarrer Nimbe. */
    val installed: ApkInfo by lazy { updater.installed() }
    val installedAt: Long by lazy { updater.installedAt() }

    var progress by mutableStateOf<UpdateProgress?>(null)
        private set

    /** Résultat du dernier « Vérifier maintenant » de cette session. */
    var checkResult by mutableStateOf<CheckResult?>(null)
        private set

    fun isNewer(manifest: UpdateManifest?): Boolean = manifest != null && manifest.versionCode > installed.versionCode

    fun canInstall(): Boolean = updater.canInstall()

    fun checkNow() {
        if (progress != null) return
        scope.launch {
            progress = UpdateProgress.Checking
            try {
                checkResult = updater.check()
            } finally {
                progress = null
            }
        }
    }

    fun installNow(manifest: UpdateManifest) {
        if (progress != null) return
        scope.launch {
            progress = UpdateProgress.Downloading(0, manifest.size)
            try {
                // Ensuite, Android prend le relais : il affiche sa confirmation, puis redémarre Nimbe.
                updater.install(manifest, interactive = true) { received ->
                    progress = if (received >= manifest.size) UpdateProgress.Verifying else UpdateProgress.Downloading(received, manifest.size)
                }
            } catch (_: UpdateException) {
                // Le rapport est enregistré : l'écran passe sur « n'a pas été installée ».
            } finally {
                progress = null
            }
        }
    }

    /** « Plus tard » : on ne repropose pas avant demain matin. */
    fun snooze() {
        scope.launch { updater.store.snooze(System.currentTimeMillis() + UpdateRules.SNOOZE_MS) }
    }

    fun dismissFailure() {
        scope.launch { updater.store.clearFailure() }
    }

    fun setChannel(channel: UpdateChannel) {
        scope.launch {
            updater.store.setChannel(channel)
            checkResult = null
        }
    }

    /** Réglage Android « Installer des applis inconnues » pour Nimbe. */
    fun openInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Certains firmwares TV n'ont pas cet écran : l'installation demandera alors l'autorisation elle-même.
        }
    }
}
