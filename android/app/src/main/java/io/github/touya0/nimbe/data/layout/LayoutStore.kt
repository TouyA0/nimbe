package io.github.touya0.nimbe.data.layout

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.layoutStore by preferencesDataStore(name = "layouts")

/** Mise en page de chaque profil, en JSON dans DataStore : une entrée par profil. */
class LayoutStore(context: Context) {

    private val store = context.applicationContext.layoutStore

    /** Toujours une mise en page utilisable : celle par défaut tant que le profil n'a rien changé. */
    fun layout(profileId: String): Flow<ProfileLayout> = store.data.map { prefs ->
        LayoutRules.normalize(LayoutCodec.decode(prefs[key(profileId)]) ?: ProfileLayout.Default)
    }

    suspend fun save(profileId: String, layout: ProfileLayout) {
        store.edit { it[key(profileId)] = LayoutCodec.encode(LayoutRules.normalize(layout.copy(customized = true))) }
    }

    /** « Rétablir par défaut », et ménage quand un profil est supprimé. */
    suspend fun remove(profileId: String) {
        store.edit { it.remove(key(profileId)) }
    }

    private fun key(profileId: String) = stringPreferencesKey("layout.$profileId")
}
