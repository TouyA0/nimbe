package io.github.touya0.nimbe.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import io.github.touya0.nimbe.R

// Polices de la maquette, intégrées à l'appli (licence SIL OFL, textes dans assets/licences) :
// le launcher ne dépend d'aucun téléchargement pour s'afficher.

/** Manrope, police de l'interface. Un seul fichier « variable » couvre toutes les graisses. */
private val Manrope = FontFamily(
    listOf(FontWeight.ExtraLight, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            resId = R.font.manrope,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** Bebas Neue, réservée aux titres de films et de séries. */
val BebasNeue = FontFamily(Font(R.font.bebas_neue))

// Tailles pensées pour une lecture à 3 m : aucun texte courant sous 14 sp (28 px en 1080p).
val Typography = Typography(
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

// Horloge de l'en-tête : grande et fine (Manrope 200 dans la maquette).
val ClockStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraLight, fontSize = 22.sp)

/** Titre de film en grand, à utiliser avec Jellyfin (fiche film, zone du haut de l'accueil). */
val FilmTitleStyle = TextStyle(fontFamily = BebasNeue, fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = 1.sp)
