package io.github.touya0.nimbe

import android.app.ActivityManager
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import io.github.touya0.nimbe.core.storage.NimbeDatabase
import io.github.touya0.nimbe.core.storage.SecretVault
import io.github.touya0.nimbe.crashguard.CrashGuard
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.data.jellyfin.JellyfinLibrary
import io.github.touya0.nimbe.update.UpdateScheduler
import io.github.touya0.nimbe.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

class NimbeApplication : Application(), SingletonImageLoader.Factory {

    /** Une seule base pour tout le processus, ouverte au premier usage. */
    val database: NimbeDatabase by lazy { NimbeDatabase.create(this) }

    val vault: SecretVault by lazy { SecretVault(this) }

    /** Connexion à Jellyfin, partagée par les écrans et le lecteur. */
    val jellyfin: JellyfinAccount by lazy { JellyfinAccount(this, vault) }

    val library: JellyfinLibrary by lazy { JellyfinLibrary(jellyfin) }

    /** Travaux courts qui ne doivent pas retarder l'affichage. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // En tout premier, pour compter un plantage même pendant le reste du démarrage.
        CrashGuard.install(this)
        UpdateScheduler.schedule(this)
        // Après une mise à jour, on oublie la proposition qui vient d'être installée.
        scope.launch { runCatching { Updater(this@NimbeApplication).reconcile() } }
    }

    /**
     * Chargeur d'images de toute l'appli. Le stick a peu de mémoire : 15 % pour les images en mémoire,
     * et 1 Go sur le disque pour ne pas retélécharger les affiches à chaque démarrage.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(!isLowRamDevice())
            .build()

    private fun isLowRamDevice(): Boolean = getSystemService(ActivityManager::class.java)?.isLowRamDevice == true

    companion object {
        private const val IMAGE_DISK_CACHE_BYTES = 1024L * 1024 * 1024

        /**
         * Paquet de Nimbe et de son code. La version de développement s'installe sous BASE_PACKAGE.debug :
         * pour reconnaître notre code ou nos paquets, on compare à cette valeur, pas à context.packageName.
         */
        const val BASE_PACKAGE = "io.github.touya0.nimbe"
    }
}
