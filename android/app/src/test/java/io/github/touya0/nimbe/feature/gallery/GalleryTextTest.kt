package io.github.touya0.nimbe.feature.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryTextTest {

    @Test
    fun `résumé comme la maquette`() {
        val now = 10_000_000_000L
        assertEquals(
            "Nextcloud · 4 812 photos · 236 vidéos · synchronisé il y a 3 min",
            GalleryText.summary(4812, 236, now - 3 * 60_000, now),
        )
        assertEquals("Nextcloud · 1 photo", GalleryText.summary(1, 0, null, now))
    }

    @Test
    fun `durées écoulées`() {
        val now = 10_000_000_000L
        assertEquals("à l'instant", GalleryText.ago(now - 20_000, now))
        assertEquals("il y a 2 h", GalleryText.ago(now - 2 * 3_600_000, now))
        assertEquals("hier", GalleryText.ago(now - 30 * 3_600_000L, now))
        assertEquals("il y a 4 jours", GalleryText.ago(now - 4 * 86_400_000L, now))
    }
}
