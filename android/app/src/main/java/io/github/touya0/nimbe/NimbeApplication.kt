package io.github.touya0.nimbe

import android.app.Application
import io.github.touya0.nimbe.core.storage.NimbeDatabase
import io.github.touya0.nimbe.crashguard.CrashGuard
import io.github.touya0.nimbe.update.UpdateScheduler
import io.github.touya0.nimbe.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NimbeApplication : Application() {

    /** Une seule base pour tout le processus, ouverte au premier usage. */
    val database: NimbeDatabase by lazy { NimbeDatabase.create(this) }

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
}
