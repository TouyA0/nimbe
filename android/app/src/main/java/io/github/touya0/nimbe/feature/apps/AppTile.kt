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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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

@Composable
fun AppTile(app: InstalledApp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TileSurface(color = tileColor(app.packageName), onClick = onClick, modifier = modifier) {
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
            if (app.category != AppCategory.Other) {
                Text(text = app.category.label, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
            }
        }
    }
}

/** Tuile « Toutes les applis », en bout de rangée « Mes applis ». */
@Composable
fun AllAppsTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TileSurface(color = Color(0xFF1A1F28), onClick = onClick, modifier = modifier) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Text("▦", style = MaterialTheme.typography.titleMedium)
        }
        Text("Toutes les applis", style = MaterialTheme.typography.titleSmall, maxLines = 2)
    }
}

@Composable
private fun TileSurface(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(64.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color,
            contentColor = Lune,
            focusedContainerColor = color,
            focusedContentColor = Lune,
            pressedContainerColor = color,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp).height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}
