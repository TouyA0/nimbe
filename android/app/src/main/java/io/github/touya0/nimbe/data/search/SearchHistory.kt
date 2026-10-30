package io.github.touya0.nimbe.data.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.searchHistoryStore by preferencesDataStore(name = "search_history")

object SearchHistoryRules {
    const val MAX = 5

    /** La plus récente devant, sans doublon (accents et majuscules comptent pour rien). */
    fun add(history: List<String>, query: String): List<String> {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return history
        val plain = SearchText.normalize(cleaned)
        return (listOf(cleaned) + history.filterNot { SearchText.normalize(it) == plain }).take(MAX)
    }
}

/** Recherches récentes de chaque profil, sur le stick. L'invité n'en a pas : l'appelant ne lui en enregistre aucune. */
class SearchHistoryStore(context: Context) {

    private val store = context.applicationContext.searchHistoryStore

    fun history(profileId: String): Flow<List<String>> =
        store.data.map { prefs -> prefs[key(profileId)]?.split('\n')?.filter { it.isNotBlank() }.orEmpty() }

    suspend fun add(profileId: String, query: String) {
        store.edit { prefs ->
            val current = prefs[key(profileId)]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
            prefs[key(profileId)] = SearchHistoryRules.add(current, query.replace('\n', ' ')).joinToString("\n")
        }
    }

    /** Profil supprimé, ou « Effacer » : on oublie tout. */
    suspend fun clear(profileId: String) {
        store.edit { it.remove(key(profileId)) }
    }

    private fun key(profileId: String) = stringPreferencesKey(profileId)
}
