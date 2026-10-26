package io.github.touya0.nimbe

import android.app.Application
import io.github.touya0.nimbe.core.storage.NimbeDatabase
import io.github.touya0.nimbe.crashguard.CrashGuard

class NimbeApplication : Application() {

    /** Une seule base pour tout le processus, ouverte au premier usage. */
    val database: NimbeDatabase by lazy { NimbeDatabase.create(this) }

    override fun onCreate() {
        super.onCreate()
        // En tout premier, pour compter un plantage même pendant le reste du démarrage.
        CrashGuard.install(this)
    }
}
