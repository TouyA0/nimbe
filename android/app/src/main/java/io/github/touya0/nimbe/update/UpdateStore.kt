package io.github.touya0.nimbe.update

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateStore by preferencesDataStore(name = "updates")

/** Ce que le stick sait des mises à jour, partagé entre la vérification de nuit et l'écran. */
data class UpdateState(
    val channel: UpdateChannel = UpdateChannel.Stable,
    /** Dernière version trouvée plus récente que celle installée au moment de la vérification. */
    val available: UpdateManifest? = null,
    /** Son APK est téléchargé et a passé toutes les vérifications. */
    val ready: Boolean = false,
    val lastCheckAt: Long? = null,
    /** null si la dernière vérification a abouti. */
    val lastCheckError: String? = null,
    val snoozedUntil: Long = 0L,
    val failure: UpdateFailure? = null,
)

class UpdateStore(context: Context) {

    private val store = context.applicationContext.updateStore

    val state: Flow<UpdateState> = store.data.map { prefs ->
        UpdateState(
            channel = UpdateChannel.fromKey(prefs[CHANNEL]),
            available = prefs[AVAILABLE]?.let { runCatching { UpdateManifest.parse(it) }.getOrNull() },
            ready = prefs[READY] ?: false,
            lastCheckAt = prefs[LAST_CHECK_AT],
            lastCheckError = prefs[LAST_CHECK_ERROR],
            snoozedUntil = prefs[SNOOZED_UNTIL] ?: 0L,
            failure = UpdateFailure.decode(prefs[FAILURE]),
        )
    }

    suspend fun current(): UpdateState = state.first()

    suspend fun setChannel(channel: UpdateChannel) {
        store.edit { it[CHANNEL] = channel.key }
    }

    suspend fun recordCheck(available: UpdateManifest?, at: Long) = store.edit { prefs ->
        val previous = prefs[AVAILABLE]?.let { runCatching { UpdateManifest.parse(it) }.getOrNull() }
        // Une autre version que celle déjà téléchargée : le fichier prêt ne vaut plus.
        if (previous?.versionCode != available?.versionCode) prefs.remove(READY)
        if (available == null) prefs.remove(AVAILABLE) else prefs[AVAILABLE] = available.toJson()
        prefs[LAST_CHECK_AT] = at
        prefs.remove(LAST_CHECK_ERROR)
    }

    suspend fun recordCheckError(message: String, at: Long) = store.edit {
        it[LAST_CHECK_AT] = at
        it[LAST_CHECK_ERROR] = message
    }

    /** Fichier téléchargé et contrôlé : un échec précédent ne compte plus. */
    suspend fun markReady() = store.edit {
        it[READY] = true
        it.remove(FAILURE)
    }

    suspend fun recordFailure(failure: UpdateFailure) = store.edit {
        it[FAILURE] = failure.encode()
        it.remove(READY)
    }

    suspend fun clearFailure() = store.edit { it.remove(FAILURE) }

    suspend fun snooze(until: Long) = store.edit { it[SNOOZED_UNTIL] = until }

    /** La version disponible est installée (ou dépassée) : on oublie tout ce qui la concernait. */
    suspend fun clearAvailable() = store.edit {
        it.remove(AVAILABLE)
        it.remove(READY)
        it.remove(FAILURE)
        it.remove(SNOOZED_UNTIL)
    }

    private companion object {
        val CHANNEL = stringPreferencesKey("channel")
        val AVAILABLE = stringPreferencesKey("available")
        val READY = booleanPreferencesKey("ready")
        val LAST_CHECK_AT = longPreferencesKey("last_check_at")
        val LAST_CHECK_ERROR = stringPreferencesKey("last_check_error")
        val SNOOZED_UNTIL = longPreferencesKey("snoozed_until")
        val FAILURE = stringPreferencesKey("failure")
    }
}
