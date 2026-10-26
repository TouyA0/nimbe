package io.github.touya0.nimbe.crashguard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import io.github.touya0.nimbe.NimbeApplication
import kotlin.system.exitProcess

/** Dernière erreur enregistrée : résumé sans données personnelles et heure. */
data class LastError(val summary: String, val at: Long)

/**
 * Garde-fou anti-plantage. Il note chaque plantage, et au démarrage décide d'afficher
 * le launcher, l'écran Secours ou le mode sans échec (voir [CrashPolicy]).
 *
 * Limite connue : un plantage avant [install] (tout au début du démarrage) n'est pas compté.
 */
object CrashGuard {

    private const val PREFS = "crashguard"
    private const val KEY_CRASHES = "crashes"
    private const val KEY_SAFE_MODE = "safe_mode"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_LAST_ERROR_AT = "last_error_at"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** À appeler en tout premier dans Application.onCreate. */
    fun install(context: Context) {
        val prefs = prefs(context)
        val ownPackage = NimbeApplication.BASE_PACKAGE
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Jamais d'exception depuis ici : on laisserait l'appli dans un état pire.
            runCatching { record(prefs, error, ownPackage, System.currentTimeMillis()) }
            previous?.uncaughtException(thread, error) ?: exitProcess(10)
        }
    }

    // commit() et non apply() : le processus va mourir, l'écriture doit être finie avant.
    @SuppressLint("ApplySharedPref")
    private fun record(prefs: SharedPreferences, error: Throwable, ownPackage: String, now: Long) {
        val crashes = CrashPolicy.append(CrashPolicy.decode(prefs.getString(KEY_CRASHES, null)), now)
        prefs.edit()
            .putString(KEY_CRASHES, CrashPolicy.encode(crashes))
            .putString(KEY_LAST_ERROR, CrashPolicy.summarize(error, ownPackage))
            .putLong(KEY_LAST_ERROR_AT, now)
            .commit()
    }

    /**
     * Décide quoi afficher. Quand le Secours se déclenche, on passe aussi en mode sans échec
     * pour le prochain démarrage, et on repart d'un compteur vide.
     */
    @SuppressLint("ApplySharedPref")
    fun startupMode(context: Context, now: Long = System.currentTimeMillis()): StartupMode {
        val prefs = prefs(context)
        val mode = CrashPolicy.decide(
            crashes = CrashPolicy.decode(prefs.getString(KEY_CRASHES, null)),
            safeMode = prefs.getBoolean(KEY_SAFE_MODE, false),
            now = now,
        )
        if (mode == StartupMode.Rescue) {
            prefs.edit().putBoolean(KEY_SAFE_MODE, true).remove(KEY_CRASHES).commit()
        }
        return mode
    }

    /** Sortie du mode sans échec, par « Réessayer » ou « Redémarrer normalement ». */
    @SuppressLint("ApplySharedPref")
    fun leaveSafeMode(context: Context) {
        prefs(context).edit().remove(KEY_SAFE_MODE).remove(KEY_CRASHES).commit()
    }

    fun lastError(context: Context): LastError? {
        val prefs = prefs(context)
        val summary = prefs.getString(KEY_LAST_ERROR, null) ?: return null
        return LastError(summary, prefs.getLong(KEY_LAST_ERROR_AT, 0L))
    }
}
