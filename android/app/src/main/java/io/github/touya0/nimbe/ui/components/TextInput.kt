package io.github.touya0.nimbe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair

/**
 * Champ de saisie sur une ligne : OK ouvre le clavier d'Android, « Valider » sur le clavier appelle [onSubmit].
 * Haut et Bas quittent le champ (le champ les garderait sinon pour le curseur) ; Gauche et Droite déplacent le curseur.
 */
@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace("\n", "")) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = Lune, fontSize = 20.sp),
        cursorBrush = SolidColor(Focus),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionUp -> FocusDirection.Up
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                true
            }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Nuit.copy(alpha = 0.6f), NimbeFocus.CardShape)
                    .border(1.dp, PanneauClair, NimbeFocus.CardShape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp), color = LuneAttenuee)
                }
                inner()
            }
        },
    )
}
