package io.github.touya0.nimbe.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Défilement à la télécommande, comme sur les applis de streaming : l'élément focalisé se pose toujours
 * au même endroit, à [leading] du début de la liste, et c'est le contenu qui glisse sous lui.
 * Par défaut, Android TV le place au tiers de la liste : les cartes de gauche sortent alors à moitié de l'écran.
 * Près du début et de la fin, la liste s'arrête à ses bords et le focus avance seul.
 * [fraction] décale le point de repère vers le milieu (grilles) : 0 le garde contre [leading].
 */
@OptIn(ExperimentalFoundationApi::class)
private class PivotSpec(private val leadingPx: Float, private val fraction: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val target = leadingPx + fraction * containerSize
        // Un élément qui ne tient pas sous le repère se cale sur la fin de la liste.
        val placed = if (target + size > containerSize) (containerSize - size).coerceAtLeast(0f) else target
        return offset - placed
    }
}

/** Les listes défilantes de [content] posent le focus à [leading] de leur début (voir [PivotSpec]). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScroll(leading: Dp, fraction: Float = 0f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val spec = remember(leading, fraction, density) { PivotSpec(with(density) { leading.toPx() }, fraction) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
