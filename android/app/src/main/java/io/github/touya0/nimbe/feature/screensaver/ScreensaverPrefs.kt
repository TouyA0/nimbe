package io.github.touya0.nimbe.feature.screensaver

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import io.github.touya0.nimbe.data.profiles.ChildGallery
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfileKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalTime

private val Context.screensaverStore by preferencesDataStore(name = "screensaver")

/** Que montrer quand personne ne touche à la télécommande, comme dans la maquette. */
enum class ScreensaverMode(val key: String, val label: String, val detail: String) {
    Clock("clock", "Heure et serveur", "Fond noir, très discret"),
    Photos("photos", "Photos", "Tes photos en plein écran, rien par-dessus"),
    PhotosInfo("photos_info", "Photos et infos", "Photos + heure, météo, état du serveur"),
    ;

    companion object {
        fun fromKey(key: String?): ScreensaverMode = entries.firstOrNull { it.key == key } ?: Clock
    }
}

/**
 * Réglages de l'écran de veille, pour toute la TV. Les photos viennent de la galerie d'un adulte
 * ([sourceProfileId]) : toute sa galerie (ses dossiers montrés), ou seulement [pick], un album ou un dossier.
 * [night] : de 23 h à 7 h, l'heure seule sur fond noir, pour ne pas éclairer la pièce.
 */
data class ScreensaverPrefs(
    val mode: ScreensaverMode = ScreensaverMode.Clock,
    val sourceProfileId: String? = null,
    val pick: ChildGallery? = null,
    val slideSeconds: Int = 15,
    val shuffle: Boolean = true,
    val showClock: Boolean = true,
    val showServer: Boolean = true,
    val showCaption: Boolean = true,
    val night: Boolean = true,
) {
    companion object {
        val SlideChoices = listOf(10, 15, 30, 60)
        val NightStart: LocalTime = LocalTime.of(23, 0)
        val NightEnd: LocalTime = LocalTime.of(7, 0)
    }
}

/** D'où viennent les photos à cet instant : la galerie d'un adulte, entière ou un seul album ou dossier. */
data class PhotoSource(val profileId: String, val pick: ChildGallery?)

/** Règles de l'écran de veille, sans Android (testées sur le PC). */
object ScreensaverRules {

    fun isNight(now: LocalTime, prefs: ScreensaverPrefs): Boolean =
        prefs.night && (now >= ScreensaverPrefs.NightStart || now < ScreensaverPrefs.NightEnd)

    /**
     * Les photos montrées dépendent du profil actif : un enfant ne voit que l'album choisi pour lui
     * par un parent, un invité aucune photo de la famille (l'heure seule). Un adulte voit la source réglée.
     * null : pas de photos, l'écran de veille montre l'heure et le serveur.
     */
    fun source(prefs: ScreensaverPrefs, active: Profile?): PhotoSource? {
        if (prefs.mode == ScreensaverMode.Clock) return null
        return when (active?.kind) {
            ProfileKind.Guest -> null
            ProfileKind.Child -> active.gallery?.let { PhotoSource(it.sourceProfileId, it) }
            else -> prefs.sourceProfileId?.let { PhotoSource(it, prefs.pick) }
        }
    }

    /** Photos seulement (pas de vidéos sur un écran de veille), celles qui ont un aperçu ; mélangées si demandé. */
    fun playlist(items: List<GalleryItem>, shuffle: Boolean, seed: Long): List<GalleryItem> {
        val photos = items.filter { !it.isVideo && it.hasPreview }
        return if (shuffle) photos.shuffled(kotlin.random.Random(seed)) else photos.sortedByDescending { it.takenAt }
    }

    fun encode(prefs: ScreensaverPrefs): String = JSONObject()
        .put("mode", prefs.mode.key)
        .put("source", prefs.sourceProfileId ?: "")
        .apply {
            prefs.pick?.let { pick ->
                put("pickSource", pick.sourceProfileId)
                put("pickName", pick.name)
                pick.albumPath?.let { put("pickAlbum", it) }
                pick.folder?.let { put("pickFolder", it) }
            }
        }
        .put("slide", prefs.slideSeconds)
        .put("shuffle", prefs.shuffle)
        .put("clock", prefs.showClock)
        .put("server", prefs.showServer)
        .put("caption", prefs.showCaption)
        .put("night", prefs.night)
        .toString()

    /** Un réglage illisible ou une valeur inconnue reprend sa valeur par défaut. */
    fun decode(text: String?): ScreensaverPrefs {
        val defaults = ScreensaverPrefs()
        val json = try {
            JSONObject(text ?: return defaults)
        } catch (_: JSONException) {
            return defaults
        }
        val album = json.optString("pickAlbum").ifEmpty { null }
        val folder = json.optString("pickFolder").ifEmpty { null }
        val pickSource = json.optString("pickSource").ifEmpty { null }
        val pick = if (pickSource != null && (album == null) != (folder == null)) ChildGallery(pickSource, json.optString("pickName"), album, folder) else null
        return ScreensaverPrefs(
            mode = ScreensaverMode.fromKey(json.optString("mode")),
            sourceProfileId = json.optString("source").ifEmpty { null },
            pick = pick?.takeIf { it.sourceProfileId == json.optString("source") },
            slideSeconds = json.optInt("slide", defaults.slideSeconds).takeIf { it in ScreensaverPrefs.SlideChoices } ?: defaults.slideSeconds,
            shuffle = json.optBoolean("shuffle", defaults.shuffle),
            showClock = json.optBoolean("clock", defaults.showClock),
            showServer = json.optBoolean("server", defaults.showServer),
            showCaption = json.optBoolean("caption", defaults.showCaption),
            night = json.optBoolean("night", defaults.night),
        )
    }
}

/** Réglages de l'écran de veille, pour toute la TV. */
class ScreensaverStore(context: Context) {

    private val store = context.applicationContext.screensaverStore

    val prefs: Flow<ScreensaverPrefs> = store.data.map { ScreensaverRules.decode(it[KEY]) }

    suspend fun save(prefs: ScreensaverPrefs) {
        store.edit { it[KEY] = ScreensaverRules.encode(prefs) }
    }

    private companion object {
        val KEY = stringPreferencesKey("prefs")
    }
}
