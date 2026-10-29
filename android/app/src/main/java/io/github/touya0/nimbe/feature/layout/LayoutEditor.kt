package io.github.touya0.nimbe.feature.layout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.layout.LayoutItem
import io.github.touya0.nimbe.data.layout.LayoutRules
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair

/**
 * Liste ordonnée d'éléments (rangées de l'accueil, onglets) à la télécommande seule :
 * OK prend l'élément, Haut et Bas le déplacent, OK ou Retour le posent ; OK maintenu ou Menu le masque.
 * Les éléments masqués sont en dessous : OK les remet à la fin des visibles.
 *
 * [label] : nom affiché ; [hideProblem] : pourquoi un élément ne peut pas être masqué ;
 * [fixed] : élément qu'on ne peut pas déplacer (l'Accueil).
 */
@Composable
fun LayoutEditor(
    items: List<LayoutItem>,
    label: (String) -> String,
    onChange: (List<LayoutItem>) -> Unit,
    modifier: Modifier = Modifier,
    hideProblem: (String) -> String? = { null },
    fixed: (String) -> Boolean = { false },
    rowHeight: Dp = 56.dp,
) {
    // Élément pris : il suit Haut et Bas jusqu'à ce qu'on le pose.
    var picked by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = picked != null) { picked = null }
    // Toujours la dernière liste : un clic gardé par une ligne ne repart pas d'une liste périmée.
    val current by rememberUpdatedState(items)
    val change by rememberUpdatedState(onChange)
    // Quand une ligne disparaît (masquée) ou apparaît (remise), le focus va sur une ligne qui reste à l'écran.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(id: String) = requesters.getOrPut(id) { FocusRequester() }
    var focusNext by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(items, focusNext) {
        val id = focusNext ?: return@LaunchedEffect
        if (items.any { it.id == id && it.visible }) {
            runCatching { requester(id).requestFocus() }
            focusNext = null
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.filter { it.visible }.forEachIndexed { index, item ->
            // La clé garde le même nœud quand l'élément change de place : le focus le suit.
            key(item.id) {
                EditorRow(
                    title = label(item.id),
                    position = index + 1,
                    picked = picked == item.id,
                    detail = when {
                        picked == item.id -> "Haut et Bas pour déplacer · OK pour poser"
                        fixed(item.id) -> "Toujours en premier"
                        else -> null
                    },
                    height = rowHeight,
                    onClick = {
                        problem = null
                        picked = when {
                            picked == item.id -> null
                            fixed(item.id) -> {
                                problem = "${label(item.id)} reste en premier"
                                null
                            }
                            else -> item.id
                        }
                    },
                    onHide = {
                        picked = null
                        problem = hideProblem(item.id)
                        if (problem == null) {
                            val visible = current.filter { it.visible }
                            val at = visible.indexOfFirst { it.id == item.id }
                            focusNext = (visible.getOrNull(at + 1) ?: visible.getOrNull(at - 1))?.id
                            change(LayoutRules.setVisible(current, item.id, false))
                        }
                    },
                    onMove = { delta -> change(LayoutRules.move(current, item.id, delta)) },
                    modifier = Modifier.focusRequester(requester(item.id)),
                )
            }
        }
        problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Alerte) }
        val hidden = items.filterNot { it.visible }
        if (hidden.isNotEmpty()) {
            Text("Masquées · OK pour remettre à la fin", style = MaterialTheme.typography.titleSmall, color = LuneAttenuee)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                hidden.forEach { item ->
                    key(item.id) {
                        HiddenChip(label(item.id), onClick = {
                            focusNext = item.id
                            change(LayoutRules.setVisible(current, item.id, true))
                        })
                    }
                }
            }
        } else {
            Text("OK pour prendre et poser · OK maintenu pour masquer", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
        }
    }
}

@Composable
private fun EditorRow(
    title: String,
    position: Int,
    picked: Boolean,
    detail: String?,
    height: Dp,
    onClick: () -> Unit,
    onHide: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onHide,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.Menu -> {
                        if (event.type == KeyEventType.KeyUp) onHide()
                        true
                    }
                    // Pris : Haut et Bas déplacent au lieu de changer de focus.
                    picked && (event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> {
                        if (event.type == KeyEventType.KeyDown) onMove(if (event.key == Key.DirectionUp) -1 else 1)
                        true
                    }
                    else -> false
                }
            }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (picked) Focus.copy(alpha = 0.25f) else Panneau,
            contentColor = Lune,
            focusedContainerColor = if (picked) Focus.copy(alpha = 0.25f) else PanneauClair,
            focusedContentColor = Lune,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = (height - 40.dp) / 2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$position", style = MaterialTheme.typography.titleSmall, color = Focus)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee) }
            }
            Text(if (picked) "↕" else "⋮⋮", style = MaterialTheme.typography.titleMedium, color = if (picked) Focus else LuneAttenuee)
        }
    }
}

@Composable
private fun HiddenChip(title: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.PillRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PanneauClair,
            contentColor = Lune,
            focusedContainerColor = Lune,
            focusedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text("+ $title", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
