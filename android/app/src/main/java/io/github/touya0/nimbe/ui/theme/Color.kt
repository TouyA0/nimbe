package io.github.touya0.nimbe.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Palette de la maquette (écran « Identité »).
val Nuit = Color(0xFF0B0E13)
val Panneau = Color(0xFF141922)
val PanneauClair = Color(0xFF232A36)
val Lune = Color(0xFFF4F2EE)
val LuneAttenueeBase = Color(0xB3F4F2EE)

/** Texte secondaire : atténué, sauf en contraste élevé où il passe en blanc plein. */
val LuneAttenuee: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDisplay.current.highContrast) Lune else LuneAttenueeBase

val Focus = Color(0xFFF0B44C)
val EnLigne = Color(0xFF8FD4A0)
val Alerte = Color(0xFFF87171)

// Couleurs de fond des tuiles d'applis, reprises de l'écran « Toutes les applis ».
val TileColors = listOf(
    Color(0xFF5B3FB5),
    Color(0xFFB3261E),
    Color(0xFF1E3A8A),
    Color(0xFF0369A1),
    Color(0xFFC2410C),
    Color(0xFF6D28D9),
    Color(0xFF166534),
    Color(0xFF0E7490),
    Color(0xFF475569),
    Color(0xFF2A2F38),
)
