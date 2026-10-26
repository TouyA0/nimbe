package io.github.touya0.nimbe.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.touya0.nimbe.ui.theme.DisplaySettings
import io.github.touya0.nimbe.ui.theme.TextSize
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

    val display: Flow<DisplaySettings> = store.data.map { prefs ->
        DisplaySettings(
            textSize = TextSize.fromKey(prefs[TEXT_SIZE]),
            highContrast = prefs[HIGH_CONTRAST] ?: false,
            reduceMotion = prefs[REDUCE_MOTION] ?: false,
            keyHints = prefs[KEY_HINTS] ?: true,
        )
    }

    suspend fun setDisplay(display: DisplaySettings) {
        store.edit {
            it[TEXT_SIZE] = display.textSize.key
            it[HIGH_CONTRAST] = display.highContrast
            it[REDUCE_MOTION] = display.reduceMotion
            it[KEY_HINTS] = display.keyHints
        }
    }

    suspend fun setDock(packages: List<String>) {
        store.edit { it[DOCK] = packages.joinToString(",") }
    }

    suspend fun setHidden(packages: Set<String>) {
        store.edit { it[HIDDEN] = packages }
    }

    private companion object {
        val DOCK = stringPreferencesKey("dock")
        val HIDDEN = stringSetPreferencesKey("hidden_apps")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val KEY_HINTS = booleanPreferencesKey("key_hints")
    }
}
