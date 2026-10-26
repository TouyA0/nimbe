package io.github.touya0.nimbe.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File

/** Ce qu'Android dit d'un APK, ou de la version installée. [signers] : empreintes SHA-256 des certificats. */
data class ApkInfo(val packageName: String, val versionCode: Long, val versionName: String?, val signers: Set<String>)

class ApkInspector(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    @Suppress("DEPRECATION")
    fun installed(): ApkInfo {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        return pm.getPackageInfo(context.packageName, flags).toApkInfo()
    }

    /** null si Android ne sait pas lire le fichier (APK abîmé ou tronqué). */
    @Suppress("DEPRECATION")
    fun archive(file: File): ApkInfo? {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        return pm.getPackageArchiveInfo(file.path, flags)?.toApkInfo()
    }

    /** Date de la dernière installation de Nimbe sur ce stick. */
    fun installedAt(): Long = pm.getPackageInfo(context.packageName, 0).lastUpdateTime

    // Android 9 et 10 ne remplissent pas toujours signingInfo pour un fichier : on se rabat alors sur signatures.
    @Suppress("DEPRECATION")
    private fun PackageInfo.toApkInfo(): ApkInfo {
        val signing = signingInfo
        val certificates = when {
            signing == null -> signatures
            signing.hasMultipleSigners() -> signing.apkContentsSigners
            else -> signing.signingCertificateHistory
        }
        return ApkInfo(
            packageName = packageName,
            versionCode = longVersionCode,
            versionName = versionName,
            signers = certificates.orEmpty().map { Sha256.of(it.toByteArray()) }.toSet(),
        )
    }
}
