package io.github.touya0.nimbe

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.touya0.nimbe.core.network.ConnectivityMonitor
import io.github.touya0.nimbe.core.storage.NimbeDatabase
import io.github.touya0.nimbe.core.storage.SecretVault
import io.github.touya0.nimbe.crashguard.CrashGuard
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantAccount
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantMonitor
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.data.jellyfin.JellyfinLibrary
import io.github.touya0.nimbe.data.layout.LayoutStore
import io.github.touya0.nimbe.data.nextcloud.NextcloudAccounts
import io.github.touya0.nimbe.data.nextcloud.GalleryPrefsStore
import io.github.touya0.nimbe.data.nextcloud.NextcloudGallery
import io.github.touya0.nimbe.data.notifications.NotificationCenter
import io.github.touya0.nimbe.data.notifications.NotificationPrefsStore
import io.github.touya0.nimbe.data.notifications.NotificationWatcher
import io.github.touya0.nimbe.data.profiles.ProfilesStore
import io.github.touya0.nimbe.data.profiles.ScreenTimeStore
import io.github.touya0.nimbe.data.profiles.ScreenTimeTracker
import io.github.touya0.nimbe.data.search.SearchHistoryStore
import io.github.touya0.nimbe.data.security.ParentPinStore
import io.github.touya0.nimbe.data.streaming.JustWatchApi
import io.github.touya0.nimbe.data.streaming.StreamingCatalog
import io.github.touya0.nimbe.data.streaming.StreamingRules
import io.github.touya0.nimbe.feature.notifications.BannerOverlay
import io.github.touya0.nimbe.feature.screensaver.ScreensaverStore
import io.github.touya0.nimbe.update.UpdateScheduler
import io.github.touya0.nimbe.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

class NimbeApplication : Application(), SingletonImageLoader.Factory {

    /** Une seule base pour tout le processus, ouverte au premier usage. */
    val database: NimbeDatabase by lazy { NimbeDatabase.create(this) }

    val vault: SecretVault by lazy { SecretVault(this) }

    /** Connexion à Jellyfin, partagée par les écrans et le lecteur. */
    val jellyfin: JellyfinAccount by lazy {
        JellyfinAccount(this, vault, onDisconnected = { database.cachedResponses().clear() })
    }

    /** Connexion à Home Assistant, pour l'état du Pi et des services. */
    val homeAssistant: HomeAssistantAccount by lazy { HomeAssistantAccount(this, vault) }

    /** Galeries Nextcloud, une par profil adulte. */
    val nextcloud: NextcloudAccounts by lazy { NextcloudAccounts(this, vault, onDisconnected = { gallery.forget(it) }) }

    /** Réglages de l'écran de veille, pour toute la TV. */
    val screensaver: ScreensaverStore by lazy { ScreensaverStore(this) }

    /** Réglages de galerie de chaque profil (vidéos, captures, souvenirs, rythme du diaporama). */
    val galleryPrefs: GalleryPrefsStore by lazy { GalleryPrefsStore(this) }

    /** Index des photos et vidéos de chaque galerie, lu par l'onglet Galerie et l'écran de veille. */
    val gallery: NextcloudGallery by lazy { NextcloudGallery(nextcloud, database.gallery(), database.cachedResponses(), scope, galleryPrefs) }

    /** État du Pi en direct, partagé par l'en-tête, l'écran « État du serveur » et bientôt l'écran de veille. */
    val homeAssistantMonitor: HomeAssistantMonitor by lazy { HomeAssistantMonitor(homeAssistant, scope) }

    /** PIN parent facultatif, pour toute la TV. */
    val parentPin: ParentPinStore by lazy { ParentPinStore(this, vault) }

    /** Rangées de l'accueil et onglets de chaque profil. */
    val layouts: LayoutStore by lazy { LayoutStore(this) }

    /** Temps d'écran des profils Enfant, compté sur le stick. */
    val screenTime: ScreenTimeStore by lazy { ScreenTimeStore(this) }

    /** Profils de la TV et profil actif. */
    val profiles: ProfilesStore by lazy { ProfilesStore(this) }

    val library: JellyfinLibrary by lazy { JellyfinLibrary(jellyfin, database.cachedResponses()) }

    /** Catalogue des plateformes de streaming (JustWatch), gardé dans la base pour les coupures d'internet. */
    val streaming: StreamingCatalog by lazy { StreamingCatalog(JustWatchApi(), database.cachedResponses()) }

    /** Recherches récentes de chaque profil. */
    val searchHistory: SearchHistoryStore by lazy { SearchHistoryStore(this) }

    /** Alertes enregistrées et bandeaux à l'écran. */
    val notifications: NotificationCenter by lazy {
        NotificationCenter(database.notifications(), NotificationPrefsStore(this), profiles, scope)
    }

    /** Travaux courts qui ne doivent pas retarder l'affichage. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // En tout premier, pour compter un plantage même pendant le reste du démarrage.
        CrashGuard.install(this)
        UpdateScheduler.schedule(this)
        // Après une mise à jour, on oublie la proposition qui vient d'être installée.
        scope.launch { runCatching { Updater(this@NimbeApplication).reconcile() } }
        // Les contenus enregistrés de plus d'un mois ne servent plus : on les retire.
        scope.launch { database.cachedResponses().deleteOlderThan(System.currentTimeMillis() - CACHE_MAX_AGE_MS) }
        // Les titres ouverts sur les plateformes quittent « Continuer à regarder » au bout d'un mois.
        scope.launch { database.streamingHistory().deleteOlderThan(System.currentTimeMillis() - StreamingRules.HISTORY_DAYS * DAY_MS) }
        // Le temps d'écran se compte aussi pendant les autres applis : le suivi vit avec le processus.
        ScreenTimeTracker(this, profiles, screenTime, scope, bringToFront = ::bringNimbeToFront).start()
        // Les alertes arrivent aussi quand on est dans une autre appli.
        val updater = Updater(this)
        NotificationWatcher(
            this,
            notifications,
            homeAssistantMonitor,
            library,
            ConnectivityMonitor(this),
            readyUpdate = updater.store.state.map { state ->
                state.available?.takeIf { state.ready && it.versionCode > updater.installed().versionCode }
            }.distinctUntilChanged(),
            profiles,
            screenTime,
            scope,
        ).start()
        BannerOverlay(this, notifications, scope).start()
    }

    /** Ramène Nimbe devant l'appli ouverte (temps d'écran écoulé). Android peut refuser : on n'insiste pas. */
    private fun bringNimbeToFront() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    /**
     * Chargeur d'images de toute l'appli. Le stick a peu de mémoire : 15 % pour les images en mémoire,
     * et 1 Go sur le disque pour ne pas retélécharger les affiches à chaque démarrage.
     * Délai de lecture de 30 s : sans aperçus préparés à l'avance, Nextcloud fabrique chacun au moment
     * où on le demande, et le Pi dépasse souvent les 10 s par défaut quand la grille en demande beaucoup.
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
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build() }))
            }
            .build()

    private fun isLowRamDevice(): Boolean = getSystemService(ActivityManager::class.java)?.isLowRamDevice == true

    companion object {
        private const val IMAGE_DISK_CACHE_BYTES = 1024L * 1024 * 1024
        private const val CACHE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /**
         * Paquet de Nimbe et de son code. La version de développement s'installe sous BASE_PACKAGE.debug :
         * pour reconnaître notre code ou nos paquets, on compare à cette valeur, pas à context.packageName.
         */
        const val BASE_PACKAGE = "io.github.touya0.nimbe"
    }
}
