package io.github.touya0.nimbe.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

// La maquette utilise Manrope (interface) et Bebas Neue (titres de films).
// En attendant d'intégrer les fichiers de police, on garde la police sans empattement du système.
private val Interface = FontFamily.SansSerif

// Tailles pensées pour une lecture à 3 m : aucun texte courant sous 14 sp (28 px en 1080p).
val Typography = Typography(
    displaySmall = TextStyle(fontFamily = Interface, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Interface, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Interface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Interface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Interface, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Interface, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Interface, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

// Horloge de l'en-tête : grande et fine (Manrope 200 dans la maquette).
val ClockStyle = TextStyle(fontFamily = Interface, fontWeight = FontWeight.ExtraLight, fontSize = 22.sp)
