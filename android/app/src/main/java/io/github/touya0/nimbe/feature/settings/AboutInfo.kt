package io.github.touya0.nimbe.feature.settings

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.accessibility.AccessibilityManager
import io.github.touya0.nimbe.crashguard.CrashGuard
import io.github.touya0.nimbe.crashguard.LastError
import io.github.touya0.nimbe.homekey.HomeKeyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Ce qu'affiche « À propos et diagnostic ». */
data class AboutInfo(
    val version: String,
    val device: String,
    val android: String,
    val lastError: LastError?,
    val cacheBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
    val talkBackOn: Boolean,
    val homeKeyOn: Boolean,
)

suspend fun loadAbout(context: Context): AboutInfo = withContext(Dispatchers.IO) {
    val stats = StatFs(Environment.getDataDirectory().path)
    val accessibility = context.getSystemService(AccessibilityManager::class.java)
    AboutInfo(
        version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?",
        device = "${Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.FRENCH) }} ${Build.MODEL}",
        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        lastError = CrashGuard.lastError(context),
        cacheBytes = dirSize(context.cacheDir),
        freeBytes = stats.availableBytes,
        totalBytes = stats.totalBytes,
        talkBackOn = accessibility?.isTouchExplorationEnabled == true,
        homeKeyOn = HomeKeyService.isEnabled(context),
    )
}

/** Vide le cache de Nimbe (images, fichiers temporaires). Rien d'important n'y est gardé. */
suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
}

private fun dirSize(dir: File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

/** « 412 Mo », « 3,1 Go » : unités françaises, une décimale au-delà du Go. */
fun formatBytes(bytes: Long): String {
    val mo = bytes / (1024.0 * 1024.0)
    return when {
        mo < 1 -> "moins de 1 Mo"
        mo < 1024 -> "${mo.toInt()} Mo"
        else -> String.format(Locale.FRENCH, "%.1f Go", mo / 1024)
    }
}
