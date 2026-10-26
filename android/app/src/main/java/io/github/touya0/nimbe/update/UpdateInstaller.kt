package io.github.touya0.nimbe.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/** Installation par une session PackageInstaller : Android vérifie à son tour la signature. */
object UpdateInstaller {

    const val EXTRA_INTERACTIVE = "io.github.touya0.nimbe.update.INTERACTIVE"
    const val EXTRA_VERSION = "io.github.touya0.nimbe.update.VERSION"

    /**
     * [interactive] : quelqu'un est devant la TV, on peut afficher la confirmation d'Android.
     * Sinon (la nuit), une installation qui demande une confirmation est abandonnée et attendra le matin.
     */
    fun install(context: Context, apk: File, versionName: String, interactive: Boolean) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Android 12+ : mise à jour sans clic, si Nimbe est déjà celui qui s'est installé.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("nimbe.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java)
                    .putExtra(EXTRA_INTERACTIVE, interactive)
                    .putExtra(EXTRA_VERSION, versionName)
                // Mutable : Android y ajoute le résultat de l'installation.
                val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(sessionId)
            throw e
        }
    }

    /** Peut-on espérer une installation sans personne devant la TV ? */
    val canInstallUnattended: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * Résultat de l'installation. En cas de succès, Android arrête Nimbe pour le remplacer :
 * ce récepteur ne le voit souvent pas, c'est le démarrage suivant qui le constate.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val interactive = intent.getBooleanExtra(UpdateInstaller.EXTRA_INTERACTIVE, false)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (interactive) {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
                    if (sessionId >= 0) runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
                }
            }
            // Réussi, ou refusé par la personne devant la TV : rien à signaler.
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                val failure = UpdateFailure(
                    versionName = intent.getStringExtra(UpdateInstaller.EXTRA_VERSION).orEmpty(),
                    failedAt = UpdateStep.Install,
                    reason = reasonOf(status),
                    at = System.currentTimeMillis(),
                )
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        UpdateStore(context).recordFailure(failure)
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }

    private fun reasonOf(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Pas assez de place sur le stick"
        PackageInstaller.STATUS_FAILURE_CONFLICT,
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
        PackageInstaller.STATUS_FAILURE_INVALID -> "Android a refusé ce fichier (code $status)"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation bloquée par Android"
        else -> "Android n'a pas pu installer (code $status)"
    }
}
