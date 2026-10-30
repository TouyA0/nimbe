package io.github.touya0.nimbe.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Mouvements de Nimbe : courts et amortis, pour que la télécommande paraisse immédiate.
 * Avec « Réduire les animations », tout passe d'un état à l'autre sans transition.
 */
object NimbeMotion {
    /** Retour du focus, couleurs d'un bouton. */
    const val FAST_MS = 140

    /** Apparition d'un panneau, changement du haut de l'écran. */
    const val MEDIUM_MS = 260

    /** Fondu d'une image de fond. */
    const val SLOW_MS = 480

    /** Décélération marquée : le mouvement part vite et se pose en douceur. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Pour ce qui sort de l'écran : accélère sans s'attarder. */
    val Exit = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}

/** Durée [durationMs] avec la courbe de Nimbe, ou rien si les animations sont réduites. */
@Composable
@ReadOnlyComposable
fun <T> nimbeTween(durationMs: Int = NimbeMotion.MEDIUM_MS, delayMs: Int = 0): FiniteAnimationSpec<T> =
    if (LocalDisplay.current.reduceMotion) snap() else tween(durationMs, delayMs, NimbeMotion.Emphasized)

/** Ressort légèrement amorti, pour l'agrandissement au focus. */
@Composable
@ReadOnlyComposable
fun <T> nimbeSpring(): FiniteAnimationSpec<T> =
    if (LocalDisplay.current.reduceMotion) snap() else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
