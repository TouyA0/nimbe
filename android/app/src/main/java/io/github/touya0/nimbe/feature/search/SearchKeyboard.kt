package io.github.touya0.nimbe.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.components.NimbeIcon
import io.github.touya0.nimbe.ui.components.NimbeIcons
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair

private val Letters = listOf("azertyuiop", "qsdfghjklm", "wxcvbn'-.@")
private val Digits = listOf("1234567890", "&\"()!?:;,/", "#%+=_*€$'@")

/**
 * Clavier à l'écran, toujours visible : à la télécommande, le clavier d'Android cacherait les résultats.
 * Pas de touches accentuées : la recherche n'en tient pas compte. OK maintenu sur « Effacer » vide tout.
 */
@Composable
internal fun SearchKeyboard(firstKey: FocusRequester, onType: (String) -> Unit, onDelete: () -> Unit, onClear: () -> Unit, onDictate: () -> Unit) {
    var digits by remember { mutableStateOf(false) }
    val rows = if (digits) Digits else Letters
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { index, key ->
                    Key(
                        key.toString(),
                        onClick = { onType(key.toString()) },
                        modifier = if (rowIndex == 0 && index == 0) Modifier.focusRequester(firstKey) else Modifier,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key("Espace", onClick = { onType(" ") }, weight = 3f)
            Key("Effacer", onClick = onDelete, onLongClick = onClear, weight = 3f)
            Key(if (digits) "abc" else "123", onClick = { digits = !digits }, weight = 2f)
        }
        Row(Modifier.fillMaxWidth()) {
            Key("Dicter", onClick = onDictate, weight = 1f, mic = true)
        }
    }
}

@Composable
private fun RowScope.Key(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    weight: Float = 1f,
    mic: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .weight(weight)
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
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
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mic) NimbeIcon(NimbeIcons.Mic, size = 18.dp)
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
