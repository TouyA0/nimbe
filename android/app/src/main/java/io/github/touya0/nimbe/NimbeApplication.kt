package io.github.touya0.nimbe

import android.app.Application
import io.github.touya0.nimbe.crashguard.CrashGuard

class NimbeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // En tout premier, pour compter un plantage même pendant le reste du démarrage.
        CrashGuard.install(this)
    }
}
