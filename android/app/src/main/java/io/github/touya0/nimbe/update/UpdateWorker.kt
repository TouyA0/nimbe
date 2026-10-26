package io.github.touya0.nimbe.update

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Vérification de nuit : cherche une nouvelle version, la télécharge et la contrôle.
 * Sur Android 12+ elle l'installe aussi, si rien ne joue ; avant, Android exige un OK à l'écran
 * et Nimbe la proposera au prochain passage sur l'accueil.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val updater = Updater(applicationContext)
        val manifest = (updater.check() as? CheckResult.Available)?.manifest ?: return Result.success()
        try {
            updater.prepare(manifest)
            if (UpdateInstaller.canInstallUnattended && updater.canInstall() && !updater.isPlaying()) {
                updater.install(manifest, interactive = false)
            }
        } catch (_: UpdateException) {
            // Déjà enregistré pour l'écran Mise à jour ; on réessaiera la nuit prochaine.
        }
        return Result.success()
    }
}

object UpdateScheduler {

    private const val WORK_NAME = "nimbe-update-nightly"
    private val NIGHT = LocalTime.of(3, 0)

    /**
     * Chaque nuit entre 3 h et 4 h (un peu d'aléa pour ne pas tous interroger GitHub à la même seconde),
     * seulement en Wi-Fi ou en câble. Les versions de développement ne se mettent pas à jour toutes seules.
     */
    fun schedule(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNight(ZonedDateTime.now()) + Random.nextLong(0, 60 * 60_000L), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun delayUntilNight(now: ZonedDateTime): Long {
        var next = now.with(NIGHT)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
