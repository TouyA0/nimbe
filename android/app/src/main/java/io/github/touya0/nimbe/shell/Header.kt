package io.github.touya0.nimbe.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.ClockStyle
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class Tab(val label: String) {
    Home("Accueil"),
    Tv("TV"),
    Movies("Films"),
    Series("Séries"),
    Music("Musique"),
    Gallery("Galerie"),
    Apps("Applis"),
}

/**
 * En-tête toujours visible : onglets à gauche, horloge à droite.
 * Comme sur les autres accueils TV, poser le focus sur un onglet l'affiche.
 */
@Composable
fun Header(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    requesters: Map<Tab, FocusRequester>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // En remontant depuis le contenu, le focus revient sur l'onglet quitté.
        Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tab.entries.forEach { tab ->
                TabPill(
                    tab = tab,
                    selected = tab == selected,
                    onSelect = { onSelect(tab) },
                    modifier = Modifier.focusRequester(requesters.getValue(tab)),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Clock()
    }
}

@Composable
private fun TabPill(tab: Tab, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val container = if (selected) Lune else Color.Transparent
    val content = if (selected) Nuit else LuneAttenuee
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onSelect,
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .focusRing(focused, NimbeFocus.PillRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = content,
            focusedContainerColor = if (selected) Lune else PanneauClair,
            focusedContentColor = if (selected) Nuit else Lune,
            pressedContainerColor = Lune,
            pressedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = tab.label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            // Réveil pile au changement de minute, plutôt qu'une boucle toutes les secondes.
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Text(now.format(ClockFormat), style = ClockStyle, color = Lune)
}
