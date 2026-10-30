package io.github.touya0.nimbe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Nimbe n'a qu'un thème sombre : la TV est regardée le soir, dans le noir.
private val NimbeColors = darkColorScheme(
    primary = Focus,
    onPrimary = Nuit,
    background = Nuit,
    onBackground = Lune,
    surface = Panneau,
    onSurface = Lune,
    surfaceVariant = PanneauClair,
    onSurfaceVariant = LuneAttenueeBase,
    error = Alerte,
    onError = Nuit,
    border = Focus,
)

@Composable
fun NimbeTheme(display: DisplaySettings = DisplaySettings(), content: @Composable () -> Unit) {
    val density = LocalDensity.current
    MaterialTheme(colorScheme = NimbeColors, typography = Typography) {
        CompositionLocalProvider(
            // Texte en Lune par défaut, y compris hors d'une Surface.
            LocalContentColor provides Lune,
            LocalDisplay provides display,
            // La taille du texte agit sur toutes les tailles en sp, sans toucher aux marges en dp.
            LocalDensity provides Density(density.density, density.fontScale * display.textSize.scale),
            content = content,
        )
    }
}

// Système de focus de la maquette : un seul élément focalisé, agrandi de 8 %,
// entouré d'un anneau ambre décalé de l'élément (voir ui/components/FocusRing.kt).
object NimbeFocus {
    const val SCALE = 1.08f

    /** Boutons et pilules : agrandis moins, pour ne pas mordre sur leurs voisins. */
    const val SMALL_SCALE = 1.04f
    val CardRadius = 12.dp
    val CardShape = RoundedCornerShape(CardRadius)
    val PillRadius = 24.dp
    val PillShape = RoundedCornerShape(PillRadius)
}
