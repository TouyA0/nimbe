package io.github.touya0.nimbe.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.feature.apps.AllAppsTile
import io.github.touya0.nimbe.feature.apps.AppTile
import io.github.touya0.nimbe.feature.apps.InstalledApp
import io.github.touya0.nimbe.feature.apps.defaultDock
import io.github.touya0.nimbe.ui.components.KeyHints
import io.github.touya0.nimbe.ui.components.bleed
import io.github.touya0.nimbe.ui.theme.LuneAttenuee

/**
 * Accueil, version socle : seule la rangée « Mes applis » existe pour l'instant.
 * Continuer à regarder, En direct, Ajouté récemment et Souvenirs arriveront avec leurs intégrations.
 */
@Composable
fun HomeScreen(
    apps: List<InstalledApp>?,
    onOpen: (InstalledApp) -> Unit,
    onAllApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 48.dp)) {
        Text("Bienvenue sur Nimbe", style = MaterialTheme.typography.displaySmall)
        Text(
            "Tes films, ta TV, ta musique et tes photos arriveront ici au fil des versions.",
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
        )

        Spacer(Modifier.weight(1f))

        Text("Mes applis", style = MaterialTheme.typography.headlineSmall)
        // La rangée n'apparaît qu'une fois la liste chargée : sinon, l'ajout des tuiles devant
        // « Toutes les applis » décale la rangée et la première appli sort de l'écran.
        if (apps == null) {
            Text(
                "Chargement…",
                style = MaterialTheme.typography.bodyLarge,
                color = LuneAttenuee,
                modifier = Modifier.padding(vertical = 36.dp),
            )
        } else LazyRow(
            modifier = Modifier.bleed(48.dp),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(defaultDock(apps), key = { it.packageName }) { app ->
                AppTile(app = app, onClick = { onOpen(app) }, modifier = Modifier.width(170.dp))
            }
            item(key = "toutes") {
                AllAppsTile(onClick = onAllApps, modifier = Modifier.width(170.dp))
            }
        }

        KeyHints("OK" to "ouvrir", modifier = Modifier.padding(vertical = 20.dp))
    }
}
