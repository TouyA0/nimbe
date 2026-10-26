package io.github.touya0.nimbe.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** Taille du texte réglable, pour lire de loin. [key] est la valeur enregistrée. */
enum class TextSize(val key: String, val label: String, val scale: Float) {
    Normal("normal", "Normal", 1f),
    Large("grand", "Grand", 1.15f),
    ExtraLarge("tres_grand", "Très grand", 1.3f),
    ;

    companion object {
        fun fromKey(key: String?): TextSize = entries.firstOrNull { it.key == key } ?: Normal
    }
}

/** Réglages « Affichage et accessibilité », valables pour toute la TV. */
data class DisplaySettings(
    val textSize: TextSize = TextSize.Normal,
    /** Textes secondaires en blanc plein et focus blanc épais. */
    val highContrast: Boolean = false,
    /** Pas d'agrandissement au focus ni d'animation. */
    val reduceMotion: Boolean = false,
    /** Rappels « OK ouvrir · OK maintenu options » en bas des écrans. */
    val keyHints: Boolean = true,
)

val LocalDisplay = staticCompositionLocalOf { DisplaySettings() }
