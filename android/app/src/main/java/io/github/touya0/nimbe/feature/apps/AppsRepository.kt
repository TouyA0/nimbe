package io.github.touya0.nimbe.feature.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

enum class AppCategory(val label: String) {
    Video("Vidéo"),
    Music("Musique"),
    Game("Jeux"),
    Other("Autres"),
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    val icon: ImageBitmap?,
)

/** Liste les applis lançables du stick, en lisant le PackageManager d'Android. */
class AppsRepository(private val context: Context) {

    private val pm = context.packageManager

    suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        // Les applis TV déclarent LEANBACK_LAUNCHER ; certaines n'ont que LAUNCHER, on prend les deux.
        val intents = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .map { Intent(Intent.ACTION_MAIN).addCategory(it) }
        val collator = Collator.getInstance(Locale.FRENCH)

        intents
            .flatMap { @Suppress("DEPRECATION") pm.queryIntentActivities(it, 0) }
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString(),
                    category = categoryOf(info.activityInfo.applicationInfo),
                    icon = runCatching { info.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull(),
                )
            }
            .sortedWith(compareBy(collator) { it.label })
    }

    /** Intent pour ouvrir l'appli, en préférant son entrée TV. */
    fun launchIntent(packageName: String): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun categoryOf(info: ApplicationInfo): AppCategory = when (info.category) {
        ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> AppCategory.Video
        ApplicationInfo.CATEGORY_AUDIO -> AppCategory.Music
        ApplicationInfo.CATEGORY_GAME -> AppCategory.Game
        else -> AppCategory.Other
    }

    private companion object {
        // Icônes réduites : le stick a peu de mémoire.
        const val ICON_PX = 96
    }
}

/** Applis du dock par défaut, dans l'ordre de la maquette, si elles sont installées. */
private val PreferredDock = listOf(
    "org.jellyfin.androidtv",
    "com.google.android.youtube.tv",
    "com.netflix.ninja",
    "com.disney.disneyplus",
    "com.spotify.tv.android",
)

/** Choisit les applis de « Mes applis » : les préférées installées, complétées par ordre alphabétique. */
fun defaultDock(apps: List<InstalledApp>, size: Int = 6): List<InstalledApp> {
    val byPackage = apps.associateBy { it.packageName }
    val preferred = PreferredDock.mapNotNull { byPackage[it] }
    return (preferred + apps.filterNot { it in preferred }).take(size)
}
