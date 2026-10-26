package io.github.touya0.nimbe.crashguard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.ClockStyle
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.delay

private const val COUNTDOWN_S = 5

/**
 * Écran « Secours », affiché après trois plantages rapprochés.
 * Sans action, il ouvre l'accueil d'origine au bout de 5 secondes ; toucher la télécommande arrête le décompte.
 * Volontairement simple : il ne doit dépendre de rien qui pourrait planter à son tour.
 */
@Composable
fun RescueScreen(onOpenStock: () -> Unit, onRetry: () -> Unit) {
    var remaining by remember { mutableIntStateOf(COUNTDOWN_S) }
    var counting by remember { mutableStateOf(true) }
    val firstButton = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { firstButton.requestFocus() } }
    LaunchedEffect(counting) {
        while (counting && remaining > 0) {
            delay(1_000)
            remaining--
        }
        if (counting) onOpenStock()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nuit)
            .onPreviewKeyEvent {
                counting = false
                false
            }
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Countdown(remaining = remaining, visible = counting)

        Text("Le launcher a rencontré un problème", style = MaterialTheme.typography.displaySmall)
        Text(
            text = if (counting) {
                "Il s'est arrêté ${CrashPolicy.THRESHOLD} fois de suite. Pour que tu ne restes pas bloqué, " +
                    "on ouvre l'accueil d'origine du stick dans $remaining s."
            } else {
                "Il s'est arrêté ${CrashPolicy.THRESHOLD} fois de suite. Choisis comment continuer."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(560.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
            PillButton("Ouvrir l'accueil d'origine", onClick = onOpenStock, modifier = Modifier.focusRequester(firstButton))
            PillButton("Réessayer le launcher", onClick = onRetry)
        }

        Column(
            modifier = Modifier
                .padding(top = 16.dp)
                .width(560.dp)
                .background(Panneau, RoundedCornerShape(12.dp))
                .border(1.dp, PanneauClair, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("BON À SAVOIR", style = MaterialTheme.typography.labelMedium, color = Focus)
            Text(
                "L'accueil d'origine n'est jamais désactivé. Tu peux le remettre par défaut à tout moment " +
                    "dans les réglages Android du stick, ou depuis ton PC avec ADB.",
                style = MaterialTheme.typography.bodyMedium,
                color = LuneAttenuee,
            )
        }
    }
}

@Composable
private fun Countdown(remaining: Int, visible: Boolean) {
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        if (!visible) return@Box
        Canvas(Modifier.size(72.dp)) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = PanneauClair, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(
                color = Focus,
                startAngle = -90f,
                sweepAngle = 360f * remaining / COUNTDOWN_S,
                useCenter = false,
                style = stroke,
            )
        }
        Text("$remaining", style = ClockStyle)
    }
}
