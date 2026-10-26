package io.github.touya0.nimbe.update

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface CheckResult {
    data object UpToDate : CheckResult
    data class Available(val manifest: UpdateManifest) : CheckResult
    data class Failed(val message: String) : CheckResult
}

/**
 * Les étapes d'une mise à jour : vérifier, télécharger, contrôler, installer.
 * Utilisé la nuit par [UpdateWorker] et à la demande depuis l'écran Mise à jour.
 */
class Updater(context: Context) {

    private val context = context.applicationContext
    val store = UpdateStore(this.context)
    private val inspector = ApkInspector(this.context)
    private val github by lazy { GitHubReleases(userAgent = "Nimbe/${installed().versionName}") }

    // Dans le cache : « Vider le cache » peut l'effacer, au pire on retélécharge.
    private val dir get() = File(context.cacheDir, "updates")

    fun installed(): ApkInfo = inspector.installed()

    fun installedAt(): Long = inspector.installedAt()

    suspend fun check(now: Long = System.currentTimeMillis()): CheckResult {
        val channel = store.current().channel
        return try {
            val latest = github.latest(channel)
            val newer = latest?.takeIf { it.versionCode > installed().versionCode }
            store.recordCheck(newer, now)
            removeFilesExcept(newer)
            if (newer == null) CheckResult.UpToDate else CheckResult.Available(newer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Pas de détail technique à l'écran : GitHub injoignable, réponse illisible…
            val message = if (e is IOException) "GitHub injoignable" else "Réponse de GitHub illisible"
            store.recordCheckError(message, now)
            CheckResult.Failed(message)
        }
    }

    /**
     * Télécharge l'APK si besoin, puis le contrôle. En cas d'échec, le rapport est enregistré
     * pour l'écran « n'a pas été installée » et l'exception remonte.
     */
    suspend fun prepare(manifest: UpdateManifest, onProgress: (Long) -> Unit = {}): File = reporting(manifest) {
        dir.mkdirs()
        val apk = apkFile(manifest)
        if (!apk.exists()) {
            val part = File(dir, "${apk.name}.part")
            try {
                github.download(manifest.apkUrl, part, manifest.size, onProgress)
            } catch (e: IOException) {
                throw UpdateException(UpdateStep.Download, "Téléchargement interrompu, il reprendra où il s'est arrêté", e)
            }
            if (part.length() != manifest.size) {
                throw UpdateException(UpdateStep.Download, "Fichier incomplet, il reprendra où il s'est arrêté")
            }
            if (!part.renameTo(apk)) throw UpdateException(UpdateStep.Download, "Impossible d'enregistrer le fichier")
        }
        onProgress(manifest.size)
        verify(apk, manifest)
        store.markReady()
        apk
    }

    /** Contrôle une dernière fois le fichier, puis le confie à Android. */
    suspend fun install(manifest: UpdateManifest, interactive: Boolean, onProgress: (Long) -> Unit = {}) {
        val apk = prepare(manifest, onProgress)
        reporting(manifest) {
            try {
                withContext(Dispatchers.IO) { UpdateInstaller.install(context, apk, manifest.versionName, interactive) }
            } catch (e: Exception) {
                throw UpdateException(UpdateStep.Install, "Android n'a pas pu ouvrir l'installation", e)
            }
        }
    }

    /**
     * Au démarrage : si la version proposée est maintenant installée (ou dépassée),
     * on oublie la proposition et on supprime le fichier.
     */
    suspend fun reconcile() {
        val available = store.current().available ?: return
        if (available.versionCode <= installed().versionCode) {
            store.clearAvailable()
            removeFilesExcept(null)
        }
    }

    /** Une vidéo ou de la musique en cours : on n'installe pas, cela couperait la lecture. */
    fun isPlaying(): Boolean = context.getSystemService(AudioManager::class.java)?.isMusicActive == true

    /** Android autorise-t-il Nimbe à installer des applis (« sources inconnues ») ? */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    private suspend fun verify(apk: File, manifest: UpdateManifest) = withContext(Dispatchers.IO) {
        if (Sha256.of(apk) != manifest.sha256) {
            apk.delete()
            throw UpdateException(UpdateStep.Sha256, "Le fichier téléchargé ne correspond pas à celui publié sur GitHub. Il a été supprimé par sécurité.")
        }
        val info = inspector.archive(apk)
        val installed = installed()
        if (info == null || info.packageName != installed.packageName) {
            apk.delete()
            throw UpdateException(UpdateStep.Signature, "Ce fichier n'est pas une version de Nimbe. Il a été supprimé.")
        }
        if (!UpdateRules.sameSigners(installed.signers, info.signers)) {
            apk.delete()
            throw UpdateException(UpdateStep.Signature, "Ce fichier n'est pas signé avec la clé de Nimbe. Il a été supprimé par sécurité.")
        }
        if (info.versionCode != manifest.versionCode || info.versionCode <= installed.versionCode) {
            apk.delete()
            throw UpdateException(UpdateStep.Version, "Ce fichier n'est pas plus récent que la version installée.")
        }
    }

    private suspend fun <T> reporting(manifest: UpdateManifest, block: suspend () -> T): T = try {
        block()
    } catch (e: UpdateException) {
        store.recordFailure(UpdateFailure(manifest.versionName, e.step, e.message.orEmpty(), System.currentTimeMillis()))
        throw e
    }

    private fun apkFile(manifest: UpdateManifest) = File(dir, "nimbe-${manifest.versionCode}.apk")

    private fun removeFilesExcept(keep: UpdateManifest?) {
        val kept = keep?.let { apkFile(it).name }
        dir.listFiles()?.filterNot { kept != null && it.name.startsWith(kept) }?.forEach { it.delete() }
    }
}
