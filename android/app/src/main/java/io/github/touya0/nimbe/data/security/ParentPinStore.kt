package io.github.touya0.nimbe.data.security

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.touya0.nimbe.core.storage.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.securityStore by preferencesDataStore(name = "security")

/** Ce que les écrans ont besoin de savoir, sans jamais toucher à l'empreinte. */
data class SecuritySettings(val pinSet: Boolean, val protectSettings: Boolean, val lockout: PinLockout) {
    /** Les réglages ne demandent le PIN que s'il existe. */
    val settingsLocked: Boolean get() = pinSet && protectSettings
}

/**
 * PIN parent de la TV : facultatif, un seul pour toute la TV. Son empreinte va dans le coffre chiffré ;
 * le reste (PIN défini ou non, réglages protégés, blocage) dans DataStore, rien de secret.
 */
class ParentPinStore(context: Context, private val vault: SecretVault) {

    private val store = context.applicationContext.securityStore

    val settings: Flow<SecuritySettings> = store.data.map { prefs ->
        SecuritySettings(
            pinSet = prefs[PIN_SET] ?: false,
            protectSettings = prefs[PROTECT_SETTINGS] ?: true,
            lockout = prefs.lockout(),
        )
    }

    /** Définit ou remplace le PIN. Le calcul de l'empreinte prend une fraction de seconde : hors du fil principal. */
    suspend fun set(pin: String) {
        val hash = withContext(Dispatchers.Default) { PinHash.hash(pin) }
        // L'empreinte d'abord : « PIN défini » sans empreinte bloquerait les réglages pour de bon.
        vault.put(HASH, hash)
        store.edit {
            it[PIN_SET] = true
            it.saveLockout(PinLockout.Cleared)
        }
    }

    suspend fun remove() {
        store.edit {
            it[PIN_SET] = false
            it.saveLockout(PinLockout.Cleared)
        }
        vault.remove(HASH)
    }

    suspend fun setProtectSettings(protect: Boolean) {
        store.edit { it[PROTECT_SETTINGS] = protect }
    }

    /** Vérifie un PIN saisi et tient le compte des codes faux. Rien n'est vérifié pendant un blocage. */
    suspend fun check(pin: String, now: Long = System.currentTimeMillis()): PinCheck {
        val prefs = store.data.first()
        if (prefs[PIN_SET] != true) return PinCheck.NotSet
        val lockout = prefs.lockout()
        if (lockout.isLocked(now)) return PinCheck.Locked(lockout.lockedUntil)
        // Empreinte illisible (clé du Keystore perdue) : le PIN ne peut plus être vérifié, on le retire
        // plutôt que de fermer les réglages pour toujours.
        val hash = vault.get(HASH) ?: run {
            remove()
            return PinCheck.NotSet
        }
        if (withContext(Dispatchers.Default) { PinHash.matches(pin, hash) }) {
            store.edit { it.saveLockout(PinLockout.Cleared) }
            return PinCheck.Ok
        }
        val next = lockout.afterFailure(now)
        store.edit { it.saveLockout(next) }
        return if (next.isLocked(now)) PinCheck.Locked(next.lockedUntil) else PinCheck.Wrong(next.triesLeft)
    }

    private fun Preferences.lockout() = PinLockout(
        failures = this[FAILURES] ?: 0,
        lockouts = this[LOCKOUTS] ?: 0,
        lockedUntil = this[LOCKED_UNTIL] ?: 0,
    )

    private fun MutablePreferences.saveLockout(lockout: PinLockout) {
        this[FAILURES] = lockout.failures
        this[LOCKOUTS] = lockout.lockouts
        this[LOCKED_UNTIL] = lockout.lockedUntil
    }

    private companion object {
        const val HASH = "parentpin.hash"
        val PIN_SET = booleanPreferencesKey("pin_set")
        val PROTECT_SETTINGS = booleanPreferencesKey("protect_settings")
        val FAILURES = intPreferencesKey("pin_failures")
        val LOCKOUTS = intPreferencesKey("pin_lockouts")
        val LOCKED_UNTIL = longPreferencesKey("pin_locked_until")
    }
}
