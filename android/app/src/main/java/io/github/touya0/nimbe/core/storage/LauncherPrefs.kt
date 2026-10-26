package io.github.touya0.nimbe.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "launcher")

/** [packages] vaut null tant que l'utilisateur n'a jamais personnalisé son dock. */
data class DockSetting(val packages: List<String>?)

/**
 * Réglages non sensibles du launcher, gardés sur le stick (DataStore).
 * Les jetons des services n'iront jamais ici : ils auront leur coffre chiffré.
 */
class LauncherPrefs(context: Context) {

    private val store = context.applicationContext.dataStore

    /** Ordre de « Mes applis ». Enveloppé pour distinguer « pas encore lu » de « jamais personnalisé ». */
    val dock: Flow<DockSetting> = store.data.map { prefs ->
        DockSetting(prefs[DOCK]?.split(',')?.filter { it.isNotBlank() })
    }

    val hidden: Flow<Set<String>> = store.data.map { it[HIDDEN].orEmpty() }

    suspend fun setDock(packages: List<String>) {
        store.edit { it[DOCK] = packages.joinToString(",") }
    }

    suspend fun setHidden(packages: Set<String>) {
        store.edit { it[HIDDEN] = packages }
    }

    private companion object {
        val DOCK = stringPreferencesKey("dock")
        val HIDDEN = stringSetPreferencesKey("hidden_apps")
    }
}
