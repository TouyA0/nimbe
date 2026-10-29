package io.github.touya0.nimbe.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import io.github.touya0.nimbe.ui.theme.TextSize

/** Ligne de réglage focalisable : titre, explication, et à droite sa valeur ou son interrupteur. */
@Composable
fun SettingRow(
    title: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    below: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Panneau,
            contentColor = Lune,
            focusedContainerColor = PanneauClair,
            focusedContentColor = Lune,
            pressedContainerColor = PanneauClair,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                leading?.invoke()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee) }
                }
                trailing()
            }
            below?.invoke()
        }
    }
}

@Composable
fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title = title, detail = detail, onClick = { onChange(!checked) }, trailing = { Toggle(checked) })
}

/** Ligne qui mène ailleurs (réglages Android, action) : valeur actuelle puis « › ». */
@Composable
fun LinkRow(title: String, detail: String?, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SettingRow(title = title, detail = detail, onClick = onClick, modifier = modifier, trailing = {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text("›", style = MaterialTheme.typography.titleMedium, color = LuneAttenuee)
    })
}

/** Taille du texte : gauche et droite changent la valeur, OK passe à la suivante. */
@Composable
fun TextSizeRow(value: TextSize, onChange: (TextSize) -> Unit) {
    val sizes = TextSize.entries
    fun shift(delta: Int) = onChange(sizes[(sizes.indexOf(value) + delta).coerceIn(sizes.indices)])
    SettingRow(
        title = "Taille du texte",
        detail = null,
        onClick = { onChange(sizes[(sizes.indexOf(value) + 1) % sizes.size]) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { shift(-1); true }
                Key.DirectionRight -> { shift(1); true }
                else -> false
            }
        },
        trailing = { Text("Gauche / droite", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee) },
        below = {
            Row(
                Modifier.fillMaxWidth().background(Nuit, NimbeFocus.PillShape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                sizes.forEach { size ->
                    val selected = size == value
                    Box(
                        Modifier
                            .weight(1f)
                            .background(if (selected) Lune else Nuit, NimbeFocus.PillShape)
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(size.label, style = MaterialTheme.typography.titleSmall, color = if (selected) Nuit else LuneAttenuee)
                    }
                }
            }
            Text("Exemple : Brumes · Saison 2 · 18 min restantes", style = MaterialTheme.typography.bodyLarge)
        },
    )
}

@Composable
private fun Toggle(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .background(if (checked) EnLigne.copy(alpha = 0.85f) else PanneauClair, NimbeFocus.PillShape)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).background(Lune, CircleShape))
    }
}

/** Titre de groupe dans une page de réglages. */
@Composable
fun SettingsGroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}
