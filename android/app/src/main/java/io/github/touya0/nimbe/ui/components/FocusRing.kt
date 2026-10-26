package io.github.touya0.nimbe.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.LocalDisplay
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.NimbeFocus

/**
 * Focus de la maquette : l'élément grandit de 8 % et un anneau ambre l'entoure,
 * séparé de lui par un espace vide. Les Surface TV dessinent leur bordure collée à l'élément,
 * d'où ce modificateur. Avec [grow] à false (onglets), seul l'anneau apparaît.
 */
@Composable
fun Modifier.focusRing(focused: Boolean, cornerRadius: Dp, grow: Boolean = true): Modifier {
    val display = LocalDisplay.current
    val scale by animateFloatAsState(
        targetValue = if (focused && grow && !display.reduceMotion) NimbeFocus.SCALE else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "focus",
    )
    // Contraste élevé : anneau blanc et plus épais, comme dans la maquette.
    val ringColor = if (display.highContrast) Lune else Focus
    val ringWidth = if (display.highContrast) 5.dp else RingWidth
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (focused) {
                val stroke = ringWidth.toPx()
                val offset = RingGap.toPx() + stroke / 2
                drawRoundRect(
                    color = ringColor,
                    topLeft = Offset(-offset, -offset),
                    size = Size(size.width + 2 * offset, size.height + 2 * offset),
                    cornerRadius = CornerRadius(cornerRadius.toPx() + offset),
                    style = Stroke(width = stroke),
                )
            }
        }
}

private val RingWidth = 3.dp
private val RingGap = 3.dp
