package io.github.touya0.nimbe.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.LocalDisplay
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.NimbeMotion
import io.github.touya0.nimbe.ui.theme.nimbeSpring
import io.github.touya0.nimbe.ui.theme.nimbeTween

/**
 * Focus de la maquette : l'élément grandit de 8 % et un anneau ambre l'entoure,
 * séparé de lui par un espace vide. Les Surface TV dessinent leur bordure collée à l'élément,
 * d'où ce modificateur. Avec [grow] à false (onglets), seul l'anneau apparaît ; [scale] règle
 * l'agrandissement des petits éléments (boutons), pour qu'ils ne mordent pas sur leurs voisins.
 *
 * L'anneau ne claque pas : il apparaît en fondu en s'écartant de l'élément, avec un halo léger,
 * pendant que l'élément grandit sur un ressort amorti.
 */
@Composable
fun Modifier.focusRing(focused: Boolean, cornerRadius: Dp, grow: Boolean = true, scale: Float = NimbeFocus.SCALE): Modifier {
    val display = LocalDisplay.current
    val grown by animateFloatAsState(
        targetValue = if (focused && grow && !display.reduceMotion) scale else 1f,
        animationSpec = nimbeSpring(),
        label = "focus",
    )
    val shown by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = nimbeTween(NimbeMotion.FAST_MS),
        label = "anneau",
    )
    // Contraste élevé : anneau blanc et plus épais, comme dans la maquette.
    val ringColor = if (display.highContrast) Lune else Focus
    val ringWidth = if (display.highContrast) 5.dp else RingWidth
    return this
        .graphicsLayer {
            scaleX = grown
            scaleY = grown
        }
        .drawWithContent {
            drawContent()
            if (shown > 0f) drawRing(shown, ringColor, ringWidth.toPx(), cornerRadius.toPx(), halo = !display.highContrast)
        }
}

private fun DrawScope.drawRing(
    progress: Float,
    color: androidx.compose.ui.graphics.Color,
    stroke: Float,
    corner: Float,
    halo: Boolean,
) {
    // L'anneau part collé à l'élément et s'écarte jusqu'à sa place.
    val gap = RingGap.toPx() * (0.35f + 0.65f * progress)
    val offset = gap + stroke / 2
    val topLeft = Offset(-offset, -offset)
    val ringSize = Size(size.width + 2 * offset, size.height + 2 * offset)
    if (halo) {
        drawRoundRect(
            color = color.copy(alpha = 0.22f * progress),
            topLeft = topLeft,
            size = ringSize,
            cornerRadius = CornerRadius(corner + offset),
            style = Stroke(width = stroke + HaloWidth.toPx()),
        )
    }
    drawRoundRect(
        color = color.copy(alpha = progress),
        topLeft = topLeft,
        size = ringSize,
        cornerRadius = CornerRadius(corner + offset),
        style = Stroke(width = stroke),
    )
}

private val RingWidth = 3.dp
private val RingGap = 3.dp
private val HaloWidth = 5.dp
