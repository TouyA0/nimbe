package io.github.touya0.nimbe.data.profiles

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONException
import org.json.JSONObject

private val Context.screenTimeStore by preferencesDataStore(name = "screentime")

/** Temps d'écran de chaque profil Enfant, compté sur le stick : une entrée JSON par profil. */
class ScreenTimeStore(context: Context) {

    private val store = context.applicationContext.screenTimeStore

    fun usage(profileId: String): Flow<Usage> = store.data.map { decode(it[key(profileId)]) }

    suspend fun update(profileId: String, transform: (Usage) -> Usage) {
        store.edit { prefs -> prefs[key(profileId)] = encode(transform(decode(prefs[key(profileId)]))) }
    }

    /** Ménage quand un profil est supprimé. */
    suspend fun remove(profileId: String) {
        store.edit { it.remove(key(profileId)) }
    }

    private fun key(profileId: String) = stringPreferencesKey("usage.$profileId")

    private fun encode(usage: Usage): String = JSONObject()
        .put("day", usage.day)
        .put("usedMs", usage.usedMs)
        .put("bonusUntil", usage.bonusUntil)
        .apply { usage.activeSince?.let { put("activeSince", it) } }
        .toString()

    private fun decode(text: String?): Usage {
        if (text.isNullOrBlank()) return Usage()
        val json = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return Usage()
        }
        return Usage(
            day = json.optString("day"),
            usedMs = json.optLong("usedMs").coerceAtLeast(0),
            activeSince = if (json.has("activeSince")) json.optLong("activeSince") else null,
            bonusUntil = json.optLong("bonusUntil"),
        )
    }
}
