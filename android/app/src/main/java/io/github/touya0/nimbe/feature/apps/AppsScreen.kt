package io.github.touya0.nimbe.feature.apps

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import io.github.touya0.nimbe.ui.components.NimbeIcons
import io.github.touya0.nimbe.ui.components.PivotScroll
import io.github.touya0.nimbe.ui.components.RowCardPadding
import io.github.touya0.nimbe.ui.components.ScreenMargin
import io.github.touya0.nimbe.ui.theme.RowTitleStyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.core.network.InternetApps
import io.github.touya0.nimbe.ui.components.KeyHints
import io.github.touya0.nimbe.ui.components.bleed
import io.github.touya0.nimbe.ui.theme.LuneAttenuee

/**
 * Écran « Applis » : « Récemment utilisées » puis toutes les applis.
 * [apps] vaut null pendant le chargement ; les applis masquées n'y sont pas.
 */
@Composable
fun AppsScreen(
    apps: List<InstalledApp>?,
    recents: List<InstalledApp>,
    hiddenCount: Int,
    onOpen: (InstalledApp) -> Unit,
    onMenu: (InstalledApp, FocusRequester) -> Unit,
    onShowHidden: (FocusRequester) -> Unit,
    offline: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = ScreenMargin).padding(top = 16.dp)) {
        Text("Applis", style = MaterialTheme.typography.displaySmall)
        Text(
            text = when {
                apps == null -> "Chargement…"
                apps.size == 1 -> "1 appli installée"
                else -> "${apps.size} applis installées"
            } + if (hiddenCount > 0) " · $hiddenCount masquée${if (hiddenCount > 1) "s" else ""}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = LuneAttenuee,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                apps == null -> Unit
                apps.isEmpty() && hiddenCount == 0 -> Text(
                    "Aucune appli trouvée sur ce stick.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LuneAttenuee,
                    modifier = Modifier.align(Alignment.Center),
                )
                // La ligne focalisée se cale en haut de la grille : on voit toujours la suivante.
                else -> PivotScroll(RowCardPadding) { LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    // Marge pour que l'anneau et l'agrandissement du focus ne soient pas coupés.
                    modifier = Modifier.bleed(ScreenMargin),
                    contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = RowCardPadding, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (recents.isNotEmpty()) {
                        sectionTitle("Récemment utilisées")
                        items(recents, key = { "recent:${it.packageName}" }) { app ->
                            AppCard(
                                app = app,
                                onClick = { onOpen(app) },
                                onMenu = { requester -> onMenu(app, requester) },
                                offline = offline && InternetApps.needsInternet(app.packageName),
                                modifier = Modifier.animateItem(),
                            )
                        }
                        sectionTitle("Toutes les applis")
                    }
                    items(apps, key = { it.packageName }) { app ->
                        AppCard(
                            app = app,
                            onClick = { onOpen(app) },
                            onMenu = { requester -> onMenu(app, requester) },
                            offline = offline && InternetApps.needsInternet(app.packageName),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (hiddenCount > 0) {
                        // Deux colonnes, pour que le libellé tienne sur une ligne.
                        item(key = "masquees") {
                            val requester = remember { FocusRequester() }
                            ActionCard(
                                NimbeIcons.Apps,
                                if (hiddenCount > 1) "$hiddenCount masquées" else "1 masquée",
                                onClick = { onShowHidden(requester) },
                                modifier = Modifier.focusRequester(requester),
                            )
                        }
                    }
                } }
            }
        }

        KeyHints("OK" to "ouvrir", "OK maintenu" to "options", modifier = Modifier.padding(vertical = 14.dp))
    }
}

/** Titre de section sur toute la largeur de la grille. */
private fun LazyGridScope.sectionTitle(text: String) {
    item(key = "titre:$text", span = { GridItemSpan(maxLineSpan) }) {
        Text(text, style = RowTitleStyle, modifier = Modifier.padding(top = 8.dp))
    }
}
