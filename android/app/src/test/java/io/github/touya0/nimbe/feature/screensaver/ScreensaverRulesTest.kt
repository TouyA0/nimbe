package io.github.touya0.nimbe.feature.screensaver

import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import io.github.touya0.nimbe.data.profiles.ChildGallery
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ScreensaverRulesTest {

    private val adult = Profile("q", "Quentin", 0, ProfileKind.Adult)
    private val photos = ScreensaverPrefs(mode = ScreensaverMode.Photos, sourceProfileId = "q")

    @Test
    fun `la nuit, l'heure seule`() {
        assertTrue(ScreensaverRules.isNight(LocalTime.of(23, 30), photos))
        assertTrue(ScreensaverRules.isNight(LocalTime.of(6, 59), photos))
        assertFalse(ScreensaverRules.isNight(LocalTime.of(7, 0), photos))
        assertFalse(ScreensaverRules.isNight(LocalTime.of(23, 30), photos.copy(night = false)))
    }

    @Test
    fun `photos selon le profil actif`() {
        assertEquals(PhotoSource("q", null), ScreensaverRules.source(photos, adult))
        assertNull(ScreensaverRules.source(photos.copy(mode = ScreensaverMode.Clock), adult))
        assertNull(ScreensaverRules.source(photos, Profile("i", "Invité", 1, ProfileKind.Guest)))
        val pick = ChildGallery("q", "Enfance", folder = "Photos/Enfance")
        assertEquals(PhotoSource("q", pick), ScreensaverRules.source(photos, Profile("e", "Enfant", 2, ProfileKind.Child, gallery = pick)))
        assertNull(ScreensaverRules.source(photos, Profile("e", "Enfant", 2, ProfileKind.Child)))
    }

    @Test
    fun `seulement des photos avec aperçu`() {
        fun item(id: Long, mime: String = "image/jpeg", preview: Boolean = true, at: Long = id) = GalleryItem("q", id, "$id", mime, at, 1, 0, 0, preview)
        val items = listOf(item(1), item(2, "video/mp4"), item(3, preview = false), item(4))
        assertEquals(listOf(4L, 1L), ScreensaverRules.playlist(items, shuffle = false, seed = 0).map { it.fileId })
        assertEquals(setOf(1L, 4L), ScreensaverRules.playlist(items, shuffle = true, seed = 0).map { it.fileId }.toSet())
    }

    @Test
    fun `réglages relus`() {
        val prefs = ScreensaverPrefs(
            mode = ScreensaverMode.PhotosInfo,
            sourceProfileId = "q",
            pick = ChildGallery("q", "Bretagne", albumPath = "photos/q/albums/Bretagne"),
            slideSeconds = 30,
            shuffle = false,
            showServer = false,
            night = false,
        )
        assertEquals(prefs, ScreensaverRules.decode(ScreensaverRules.encode(prefs)))
        assertEquals(ScreensaverPrefs(), ScreensaverRules.decode("pas du json"))
        // Un choix qui ne vient pas de la galerie source est ignoré.
        val other = ScreensaverRules.encode(prefs.copy(sourceProfileId = "a"))
        assertNull(ScreensaverRules.decode(other).pick)
    }
}
