package io.github.touya0.nimbe.feature.status

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.touya0.nimbe.data.homeassistant.HistorySeries
import io.github.touya0.nimbe.ui.theme.PanneauClair

/** Bleu clair de la courbe de la maquette : une couleur qui ne veut dire ni « OK » ni « alerte ». */
private val CurveColor = Color(0xFF8AB8E6)

/**
 * Courbe d'une série sur toute la largeur : de la première à la dernière heure, entre le minimum et le maximum
 * (avec une marge). Une ligne pointillée marque le maximum ; un point, la dernière valeur.
 */
@Composable
fun HistoryChart(series: HistorySeries, modifier: Modifier = Modifier) {
    val points = series.points
    Canvas(modifier) {
        val start = points.first().at
        val span = (points.last().at - start).coerceAtLeast(1)
        val low = points.minOf { it.value } - 2
        val high = (series.max?.value ?: points.maxOf { it.value }) + 2
        fun x(at: Long) = (at - start).toFloat() / span * size.width
        fun y(value: Double) = size.height - ((value - low) / (high - low)).toFloat() * size.height

        val maxY = y(high - 2)
        drawLine(
            PanneauClair,
            Offset(0f, maxY),
            Offset(size.width, maxY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
        val path = Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) moveTo(x(point.at), y(point.value)) else lineTo(x(point.at), y(point.value))
            }
        }
        drawPath(path, CurveColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = points.last()
        drawCircle(CurveColor, radius = 4.dp.toPx(), center = Offset(x(last.at), y(last.value)))
    }
}
