package io.github.touya0.nimbe.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.feature.apps.AppTile
import io.github.touya0.nimbe.feature.apps.InstalledApp
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit

/**
 * Applis que l'invité ne voit pas : OK masque ou remet. Par défaut, celles où la famille est connectée
 * avec ses comptes (Netflix, Disney+…). Chaque changement est enregistré tout de suite.
 */
@Composable
fun GuestAppsScreen(
    apps: List<InstalledApp>?,
    hidden: Set<String>,
    onChange: (Set<String>) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    // Toujours la dernière liste : un clic gardé par une tuile ne repart pas d'un choix périmé.
    val current by rememberUpdatedState(hidden)
    val change by rememberUpdatedState(onChange)
    val first = remember { FocusRequester() }
    LaunchedEffect(apps != null) { runCatching { first.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(Nuit).padding(horizontal = 56.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Applis masquées pour l'invité", style = MaterialTheme.typography.displaySmall)
                Text(
                    "OK pour masquer ou remettre. Masque celles où la famille est connectée avec ses comptes : " +
                        "${hidden.count { id -> apps?.any { it.packageName == id } == true }} masquée(s).",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LuneAttenuee,
                )
            }
            PillButton("Terminer", onClick = onClose)
            PillButton("Rétablir", onClick = onReset)
        }
        if (apps == null) {
            Text("Lecture des applis du stick…", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            // Place pour l'anneau du focus, que la grille couperait.
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                val masked = app.packageName in hidden
                AppTile(
                    app,
                    onClick = {
                        val id = app.packageName
                        change(if (id in current) current - id else current + id)
                    },
                    modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                    // Grisée : l'invité ne la verra pas.
                    dimmedNote = "Masquée pour l'invité".takeIf { masked },
                )
            }
        }
    }
}
