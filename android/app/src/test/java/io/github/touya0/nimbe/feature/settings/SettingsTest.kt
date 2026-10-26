package io.github.touya0.nimbe.feature.settings

import io.github.touya0.nimbe.ui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    @Test
    fun `taille du texte relue depuis sa clé, Normal par défaut`() {
        TextSize.entries.forEach { assertEquals(it, TextSize.fromKey(it.key)) }
        assertEquals(TextSize.Normal, TextSize.fromKey(null))
        assertEquals(TextSize.Normal, TextSize.fromKey("valeur_inconnue"))
    }

    @Test
    fun `tailles de fichiers en unités françaises`() {
        assertEquals("moins de 1 Mo", formatBytes(500_000))
        assertEquals("412 Mo", formatBytes(412L * 1024 * 1024))
        assertEquals("3,1 Go", formatBytes((3.1 * 1024 * 1024 * 1024).toLong()))
    }
}
