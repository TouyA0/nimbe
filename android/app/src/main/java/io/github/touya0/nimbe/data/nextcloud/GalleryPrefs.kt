package io.github.touya0.nimbe.data.nextcloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONException
import org.json.JSONObject

private val Context.galleryPrefsStore by preferencesDataStore(name = "gallery_prefs")

/**
 * Réglages de la galerie d'un profil (Réglages → Galerie). [hideScreenshots] : les captures d'écran
 * du téléphone n'ont rien à faire sur la TV, masquées par défaut. [slideSeconds] : rythme du diaporama.
 */
data class GalleryPrefs(
    val showVideos: Boolean = true,
    val hideScreenshots: Boolean = true,
    val memories: Boolean = true,
    val slideSeconds: Int = 8,
) {
    /** Ce que la galerie montre, d'après ces réglages ; l'index, lui, garde tout. */
    fun filter(items: List<GalleryItem>): List<GalleryItem> = items.filter { item ->
        (showVideos || !item.isVideo) && !(hideScreenshots && GalleryPrefsRules.isScreenshot(item.path))
    }

    companion object {
        /** Rythmes proposés, en secondes ; OK passe au suivant. */
        val SlideChoices = listOf(5, 8, 15, 30)
    }
}

/** Règles et lecture des réglages, sans Android (testées sur le PC). */
object GalleryPrefsRules {

    /**
     * Capture d'écran d'après son nom ou son dossier : « Screenshot_2024… », « Capture d'écran… »,
     * dossiers « Screenshots » ou « Captures d'écran » (Android, iPhone, Windows, macOS).
     */
    fun isScreenshot(path: String): Boolean {
        val lower = WebDav.displayName(path).lowercase()
        val folders = path.lowercase().split('/').dropLast(1)
        return SCREENSHOT_NAMES.any { lower.startsWith(it) } || folders.any { it in SCREENSHOT_FOLDERS }
    }

    fun encode(prefs: GalleryPrefs): String = JSONObject()
        .put("videos", prefs.showVideos)
        .put("screenshots", prefs.hideScreenshots)
        .put("memories", prefs.memories)
        .put("slide", prefs.slideSeconds)
        .toString()

    /** Un réglage illisible ou une valeur inconnue reprend sa valeur par défaut. */
    fun decode(text: String?): GalleryPrefs {
        val defaults = GalleryPrefs()
        val json = try {
            JSONObject(text ?: return defaults)
        } catch (_: JSONException) {
            return defaults
        }
        return GalleryPrefs(
            showVideos = json.optBoolean("videos", defaults.showVideos),
            hideScreenshots = json.optBoolean("screenshots", defaults.hideScreenshots),
            memories = json.optBoolean("memories", defaults.memories),
            slideSeconds = json.optInt("slide", defaults.slideSeconds).takeIf { it in GalleryPrefs.SlideChoices } ?: defaults.slideSeconds,
        )
    }

    private val SCREENSHOT_NAMES = listOf("screenshot", "capture d'écran", "capture d’écran", "capture du ", "screen shot")
    private val SCREENSHOT_FOLDERS = setOf("screenshots", "captures d'écran", "captures d’écran")
}

/** Réglages de galerie de chaque profil, dans DataStore (une entrée par profil). */
class GalleryPrefsStore(context: Context) {

    private val store = context.applicationContext.galleryPrefsStore

    fun prefs(profileId: String): Flow<GalleryPrefs> = store.data.map { GalleryPrefsRules.decode(it[key(profileId)]) }

    suspend fun save(profileId: String, prefs: GalleryPrefs) {
        store.edit { it[key(profileId)] = GalleryPrefsRules.encode(prefs) }
    }

    /** Profil supprimé : ses réglages partent avec lui. */
    suspend fun remove(profileId: String) {
        store.edit { it.remove(key(profileId)) }
    }

    private fun key(profileId: String) = stringPreferencesKey(profileId)
}
