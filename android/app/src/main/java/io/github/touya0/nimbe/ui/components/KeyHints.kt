package io.github.touya0.nimbe.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.theme.LocalDisplay
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee

/** Rappel des touches utiles, comme « [OK] ouvrir · [Menu] options » en bas de la maquette. */
@Composable
fun KeyHints(vararg hints: Pair<String, String>, modifier: Modifier = Modifier) {
    // Désactivables dans Affichage et accessibilité ; on garde la place pour ne pas décaler l'écran.
    if (!LocalDisplay.current.keyHints) {
        Spacer(modifier.height(24.dp))
        return
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { (key, action) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.labelMedium,
                    color = Lune,
                    modifier = Modifier
                        .border(1.dp, LuneAttenuee, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(text = action, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
            }
        }
    }
}
