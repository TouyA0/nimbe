package io.github.touya0.nimbe.data.notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONException
import org.json.JSONObject

private val Context.notificationStore by preferencesDataStore(name = "notifications")

/** Pendant la lecture : [CriticalOnly] (le choix de la maquette) ou aussi les alertes normales. */
enum class DuringPlayback(val label: String) { CriticalOnly("Seulement les critiques"), CriticalAndNormal("Critiques et normales") }

/** Heures calmes, en minutes depuis minuit : la nuit, tout sauf le critique va seulement dans la cloche. */
data class QuietHours(val start: Int, val end: Int) {
    fun contains(minute: Int): Boolean = if (start <= end) minute in start until end else minute >= start || minute < end
}

data class NotificationPrefs(
    val modes: Map<NotificationType, DeliveryMode> = emptyMap(),
    val duringPlayback: DuringPlayback = DuringPlayback.CriticalOnly,
    val quietHours: QuietHours? = DefaultQuiet,
    val bannerSeconds: Int = 6,
) {
    fun mode(type: NotificationType): DeliveryMode = modes[type]?.takeIf { it in type.choices } ?: type.defaultMode

    companion object {
        val DefaultQuiet = QuietHours(23 * 60, 8 * 60)
        val DurationChoices = listOf(6, 10, 15)
    }
}

object NotificationPrefsCodec {

    fun encode(prefs: NotificationPrefs): String = JSONObject()
        .put("modes", JSONObject().apply { prefs.modes.forEach { (type, mode) -> put(type.key, mode.name) } })
        .put("playback", prefs.duringPlayback.name)
        .put("quiet", prefs.quietHours?.let { JSONObject().put("start", it.start).put("end", it.end) } ?: JSONObject.NULL)
        .put("seconds", prefs.bannerSeconds)
        .toString()

    /** Un réglage illisible ou inconnu reprend sa valeur par défaut. */
    fun decode(text: String?): NotificationPrefs {
        val defaults = NotificationPrefs()
        val json = try {
            JSONObject(text ?: return defaults)
        } catch (_: JSONException) {
            return defaults
        }
        val modes = json.optJSONObject("modes")
        return NotificationPrefs(
            modes = NotificationType.entries.mapNotNull { type ->
                val name = modes?.optString(type.key).orEmpty()
                DeliveryMode.entries.firstOrNull { it.name == name }?.let { type to it }
            }.toMap(),
            duringPlayback = DuringPlayback.entries.firstOrNull { it.name == json.optString("playback") } ?: defaults.duringPlayback,
            quietHours = if (json.has("quiet") && json.isNull("quiet")) {
                null
            } else {
                json.optJSONObject("quiet")?.let { QuietHours(it.optInt("start"), it.optInt("end")) } ?: defaults.quietHours
            },
            bannerSeconds = json.optInt("seconds", defaults.bannerSeconds).takeIf { it in NotificationPrefs.DurationChoices } ?: defaults.bannerSeconds,
        )
    }
}

/** Réglages par profil, et ce que le suivi des nouveaux épisodes a déjà vu (pour toute la TV). */
class NotificationPrefsStore(context: Context) {

    private val store = context.applicationContext.notificationStore

    fun prefs(profileId: String): Flow<NotificationPrefs> = store.data.map { NotificationPrefsCodec.decode(it[stringPreferencesKey(profileId)]) }

    suspend fun save(profileId: String, prefs: NotificationPrefs) {
        store.edit { it[stringPreferencesKey(profileId)] = NotificationPrefsCodec.encode(prefs) }
    }

    suspend fun remove(profileId: String) {
        store.edit { it.remove(stringPreferencesKey(profileId)) }
    }

    suspend fun episodesSeenAt(): Long? = store.data.first()[EPISODES_SEEN]

    suspend fun setEpisodesSeenAt(at: Long) {
        store.edit { it[EPISODES_SEEN] = at }
    }

    private companion object {
        val EPISODES_SEEN = longPreferencesKey("__episodes_seen_at")
    }
}
