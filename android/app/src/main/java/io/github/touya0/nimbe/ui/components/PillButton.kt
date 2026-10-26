package io.github.touya0.nimbe.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair

/** Bouton en pilule de la maquette : clair quand il a le focus, gris sinon. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.PillRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PanneauClair,
            contentColor = Lune,
            focusedContainerColor = Lune,
            focusedContentColor = Nuit,
            pressedContainerColor = Lune,
            pressedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
