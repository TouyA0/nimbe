package io.github.touya0.nimbe.data.profiles

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.profilesStore by preferencesDataStore(name = "profiles")

/**
 * Profils de la TV et profil actif, gardés sur le stick. Rien de secret ici : le PIN parent, quand il viendra,
 * ira dans le coffre chiffré.
 */
class ProfilesStore(context: Context) {

    private val store = context.applicationContext.profilesStore

    val state: Flow<ProfilesState> = store.data.map { prefs ->
        ProfilesState(
            profiles = ProfileCodec.decode(prefs[PROFILES]),
            activeId = prefs[ACTIVE],
            onStart = StartChoice.fromKey(prefs[ON_START]),
        )
    }

    /** Ajoute ou remplace [profile] (même identifiant). Le premier profil créé devient le profil actif. */
    suspend fun save(profile: Profile) {
        store.edit { prefs ->
            val profiles = ProfileCodec.decode(prefs[PROFILES])
            val cleaned = profile.copy(name = ProfileRules.cleanName(profile.name))
            val updated = if (profiles.any { it.id == profile.id }) {
                profiles.map { if (it.id == profile.id) cleaned else it }
            } else {
                profiles + cleaned
            }
            prefs[PROFILES] = ProfileCodec.encode(updated)
            if (prefs[ACTIVE] == null) prefs[ACTIVE] = cleaned.id
        }
    }

    /** Supprime un profil ; s'il était actif, le premier adulte prend sa place. */
    suspend fun delete(id: String) {
        store.edit { prefs ->
            val remaining = ProfileCodec.decode(prefs[PROFILES]).filter { it.id != id }
            prefs[PROFILES] = ProfileCodec.encode(remaining)
            if (prefs[ACTIVE] == id) {
                val next = remaining.firstOrNull { it.kind == ProfileKind.Adult } ?: remaining.firstOrNull()
                if (next != null) prefs[ACTIVE] = next.id else prefs.remove(ACTIVE)
            }
        }
    }

    suspend fun setActive(id: String) {
        store.edit { it[ACTIVE] = id }
    }

    suspend fun setOnStart(choice: StartChoice) {
        store.edit { it[ON_START] = choice.key }
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        private val PROFILES = stringPreferencesKey("profiles")
        private val ACTIVE = stringPreferencesKey("active")
        private val ON_START = stringPreferencesKey("on_start")
    }
}
