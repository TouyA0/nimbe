package io.github.touya0.nimbe.data.nextcloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryPrefsTest {

    private fun item(id: Long, path: String, mime: String = "image/jpeg") = GalleryItem("p", id, path, mime, 0, 1, 0, 0, true)

    @Test
    fun `captures d'écran reconnues par le nom ou le dossier`() {
        assertTrue(GalleryPrefsRules.isScreenshot("Photos/Screenshot_20240102-101500.png"))
        assertTrue(GalleryPrefsRules.isScreenshot("Mac/Capture d’écran 2024-01-02 à 10.15.00.png"))
        assertTrue(GalleryPrefsRules.isScreenshot("synchro_tel/Screenshots/IMG_0001.png"))
        assertFalse(GalleryPrefsRules.isScreenshot("Photos/IMG_20240102_101500.jpg"))
        // Un dossier qui s'appelle seulement « Captures » peut contenir de vraies photos : on n'y touche pas.
        assertFalse(GalleryPrefsRules.isScreenshot("Captures/IMG_1.jpg"))
    }

    @Test
    fun `filtre des vidéos et des captures`() {
        val items = listOf(item(1, "a/IMG_1.jpg"), item(2, "a/VID_1.mp4", "video/mp4"), item(3, "a/Screenshot_1.png"))
        assertEquals(listOf(1L, 2L), GalleryPrefs().filter(items).map { it.fileId })
        assertEquals(listOf(1L), GalleryPrefs(showVideos = false).filter(items).map { it.fileId })
        assertEquals(listOf(1L, 2L, 3L), GalleryPrefs(hideScreenshots = false).filter(items).map { it.fileId })
    }

    @Test
    fun `réglages relus, valeurs inconnues remplacées par défaut`() {
        val prefs = GalleryPrefs(showVideos = false, hideScreenshots = false, memories = false, slideSeconds = 15)
        assertEquals(prefs, GalleryPrefsRules.decode(GalleryPrefsRules.encode(prefs)))
        assertEquals(8, GalleryPrefsRules.decode("""{"slide":7}""").slideSeconds)
        assertEquals(GalleryPrefs(), GalleryPrefsRules.decode("pas du json"))
        assertEquals(GalleryPrefs(), GalleryPrefsRules.decode(null))
    }
}
