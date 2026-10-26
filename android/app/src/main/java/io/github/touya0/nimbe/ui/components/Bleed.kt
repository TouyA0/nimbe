package io.github.touya0.nimbe.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Étend un élément au-delà de la marge de son parent, de [horizontal] de chaque côté.
 * Sert aux rangées et grilles : sinon, l'anneau de focus de la première et de la dernière tuile
 * est coupé au bord de la liste. La liste remet la marge avec son contentPadding.
 */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + 2 * extra,
            maxWidth = constraints.maxWidth + 2 * extra,
        ),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}
