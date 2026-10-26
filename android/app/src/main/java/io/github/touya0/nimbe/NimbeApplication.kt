package io.github.touya0.nimbe

import android.app.Application
import io.github.touya0.nimbe.core.storage.NimbeDatabase
import io.github.touya0.nimbe.core.storage.SecretVault
import io.github.touya0.nimbe.crashguard.CrashGuard
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.update.UpdateScheduler
import io.github.touya0.nimbe.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NimbeApplication : Application() {

    /** Une seule base pour tout le processus, ouverte au premier usage. */
    val database: NimbeDatabase by lazy { NimbeDatabase.create(this) }

    val vault: SecretVault by lazy { SecretVault(this) }

    /** Connexion à Jellyfin, partagée par les écrans et le lecteur. */
    val jellyfin: JellyfinAccount by lazy { JellyfinAccount(this, vault) }

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

    companion object {
        /**
         * Paquet de Nimbe et de son code. La version de développement s'installe sous BASE_PACKAGE.debug :
         * pour reconnaître notre code ou nos paquets, on compare à cette valeur, pas à context.packageName.
         */
        const val BASE_PACKAGE = "io.github.touya0.nimbe"
    }
}
