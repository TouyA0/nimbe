package io.github.touya0.nimbe.feature.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.TileColors

/** Couleur stable par appli, pour que chaque tuile garde la même teinte d'un lancement à l'autre. */
fun tileColor(packageName: String): Color = TileColors[Math.floorMod(packageName.hashCode(), TileColors.size)]

/**
 * Tuile d'une appli. [onMenu] s'ouvre avec la touche Menu ou un appui long sur OK
 * (la télécommande Xiaomi n'a pas de touche Menu) ; il reçoit de quoi rendre le focus à la tuile.
 * [offline] : l'appli a besoin d'internet et il n'y en a plus ; elle reste ouvrable mais grisée.
 * [dimmedNote] : grisée pour une autre raison, avec ce mot sous son nom (« Masquée »).
 */
@Composable
fun AppTile(
    app: InstalledApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMenu: ((FocusRequester) -> Unit)? = null,
    offline: Boolean = false,
    dimmedNote: String? = null,
) {
    TileSurface(
        color = tileColor(app.packageName),
        onClick = onClick,
        onMenu = onMenu,
        modifier = modifier,
        dimmed = offline || dimmedNote != null,
    ) {
        val badge = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
        if (app.icon != null) {
            Image(bitmap = app.icon, contentDescription = null, modifier = badge)
        } else {
            Box(badge.background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text(app.label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
            }
        }
        Column {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Android ne connaît la catégorie que si l'appli la déclare : on n'affiche pas « Autres ».
            val note = dimmedNote ?: "Hors ligne".takeIf { offline }
            if (note != null) {
                Text(text = note, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
            } else if (app.category != AppCategory.Other) {
                Text(text = app.category.label, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
            }
        }
    }
}

/** Tuile d'action (« Toutes les applis », « Applis masquées »), avec un symbole à la place de l'icône. */
@Composable
fun ActionTile(symbol: String, label: String, onClick: (FocusRequester) -> Unit, modifier: Modifier = Modifier) {
    val requester = remember { FocusRequester() }
    TileSurface(
        color = Color(0xFF1A1F28),
        onClick = { onClick(requester) },
        onMenu = null,
        modifier = modifier.focusRequester(requester),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, style = MaterialTheme.typography.titleMedium)
        }
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 2)
    }
}

@Composable
private fun TileSurface(
    color: Color,
    onClick: () -> Unit,
    onMenu: ((FocusRequester) -> Unit)?,
    modifier: Modifier,
    dimmed: Boolean = false,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val background = if (dimmed) color.copy(alpha = 0.4f) else color
    Surface(
        onClick = onClick,
        onLongClick = onMenu?.let { { it(requester) } },
        modifier = modifier
            .height(64.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (onMenu != null && event.key == Key.Menu && event.type == KeyEventType.KeyUp) {
                    onMenu(requester)
                    true
                } else {
                    false
                }
            }
            .focusRing(focused, NimbeFocus.CardRadius),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = background,
            contentColor = Lune,
            focusedContainerColor = background,
            focusedContentColor = Lune,
            pressedContainerColor = background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp).height(64.dp).alpha(if (dimmed) 0.45f else 1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}
