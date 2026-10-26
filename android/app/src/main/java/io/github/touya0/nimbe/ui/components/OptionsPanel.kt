package io.github.touya0.nimbe.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair

/** Une ligne du panneau d'options. [icon] est un caractère simple (▶, ✕, ↗…). */
data class MenuOption(
    val icon: String,
    val title: String,
    val detail: String? = null,
    val onSelect: () -> Unit,
)

/**
 * Panneau « Options » ouvert par la touche Menu (ou OK maintenu), dont le contenu s'adapte à l'élément.
 * [returnFocus] reçoit le focus à la fermeture, pour revenir là où on était.
 */
data class MenuRequest(
    val subject: String,
    val options: List<MenuOption>,
    val returnFocus: FocusRequester? = null,
)

@Stable
class MenuState {
    var current by mutableStateOf<MenuRequest?>(null)
        private set
    internal var pendingReturn: FocusRequester? = null

    fun open(request: MenuRequest) {
        if (request.options.isNotEmpty()) current = request
    }

    fun close() {
        pendingReturn = current?.returnFocus
        current = null
    }

    /** À appeler quand l'option choisie fait disparaître l'élément d'origine : on ne lui rend pas le focus. */
    fun dropReturnFocus() {
        pendingReturn = null
    }
}

/** Affiche le contenu, et par-dessus le panneau d'options quand il est ouvert. */
@Composable
fun MenuHost(state: MenuState, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        state.current?.let { request -> OptionsPanel(request, onDismiss = state::close) }
    }
    val current = state.current
    LaunchedEffect(current) {
        if (current == null) {
            state.pendingReturn?.let { runCatching { it.requestFocus() } }
            state.pendingReturn = null
        }
    }
}

@Composable
private fun OptionsPanel(request: MenuRequest, onDismiss: () -> Unit) {
    val requesters = remember(request) { request.options.map { FocusRequester() } }
    var focusedIndex by remember(request) { mutableIntStateOf(0) }

    BackHandler(onBack = onDismiss)
    LaunchedEffect(request) { runCatching { requesters.first().requestFocus() } }

    Row(Modifier.fillMaxSize()) {
        // Le contenu derrière reste visible mais assombri.
        Spacer(Modifier.weight(1.5f).fillMaxHeight().background(Nuit.copy(alpha = 0.75f)))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Panneau)
                .padding(horizontal = 32.dp, vertical = 36.dp)
                // Le focus ne quitte pas le panneau : haut et bas parcourent la liste, gauche et droite ne font rien.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val step = when (event.key) {
                        Key.DirectionUp -> -1
                        Key.DirectionDown -> 1
                        Key.DirectionLeft, Key.DirectionRight -> return@onPreviewKeyEvent true
                        else -> return@onPreviewKeyEvent false
                    }
                    val target = (focusedIndex + step).coerceIn(requesters.indices)
                    runCatching { requesters[target].requestFocus() }
                    true
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(request.subject, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
            Text("Options", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(bottom = 8.dp))
            // Défile si les options ne tiennent pas (longue liste d'applis masquées, par exemple).
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { request.options.forEachIndexed { index, option ->
                OptionRow(
                    option = option,
                    onClick = {
                        onDismiss()
                        option.onSelect()
                    },
                    modifier = Modifier
                        .focusRequester(requesters[index])
                        .onFocusChanged { if (it.isFocused) focusedIndex = index },
                )
            } }
            Text("Retour ferme le menu sans rien changer", style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
        }
    }
}

@Composable
private fun OptionRow(option: MenuOption, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PanneauClair.copy(alpha = 0.6f),
            contentColor = Lune,
            focusedContainerColor = Lune,
            focusedContentColor = Nuit,
            pressedContainerColor = Lune,
            pressedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(28.dp).background(Color.Gray.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(option.icon, style = MaterialTheme.typography.titleSmall)
            }
            Column(Modifier.width(0.dp).weight(1f)) {
                Text(option.title, style = MaterialTheme.typography.titleSmall)
                option.detail?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = if (focused) Nuit.copy(alpha = 0.7f) else LuneAttenuee)
                }
            }
        }
    }
}
