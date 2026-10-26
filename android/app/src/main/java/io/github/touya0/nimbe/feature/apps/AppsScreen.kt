package io.github.touya0.nimbe.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.components.KeyHints
import io.github.touya0.nimbe.ui.components.bleed
import io.github.touya0.nimbe.ui.theme.LuneAttenuee

/** Écran « Toutes les applis ». [apps] vaut null pendant le chargement. */
@Composable
fun AppsScreen(apps: List<InstalledApp>?, onOpen: (InstalledApp) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 48.dp)) {
        Text("Toutes les applis", style = MaterialTheme.typography.displaySmall)
        Text(
            text = when {
                apps == null -> "Chargement…"
                apps.size == 1 -> "1 appli installée"
                else -> "${apps.size} applis installées"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = LuneAttenuee,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                apps == null -> Unit
                apps.isEmpty() -> Text(
                    "Aucune appli trouvée sur ce stick.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LuneAttenuee,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    // Marge pour que l'anneau et l'agrandissement du focus ne soient pas coupés.
                    modifier = Modifier.bleed(48.dp),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppTile(app = app, onClick = { onOpen(app) })
                    }
                }
            }
        }

        KeyHints("OK" to "ouvrir", modifier = Modifier.padding(vertical = 20.dp))
    }
}
