package io.github.touya0.nimbe.crashguard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.feature.apps.AppTile
import io.github.touya0.nimbe.feature.apps.InstalledApp
import io.github.touya0.nimbe.ui.components.bleed
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Bandeau = Color(0xFF8A5A12)

/**
 * Mode sans échec : seules les applis et les options de réparation restent actives.
 * Les modules (Jellyfin, Galerie…) seront mis en pause ici quand ils existeront.
 */
@Composable
fun SafeModeScreen(
    apps: List<InstalledApp>?,
    lastError: LastError?,
    onOpen: (InstalledApp) -> Unit,
    onRestartNormally: () -> Unit,
    onOpenStock: () -> Unit,
) {
    val firstOption = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstOption.requestFocus() } }

    Column(Modifier.fillMaxSize().background(Nuit)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Bandeau).padding(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mode sans échec", style = MaterialTheme.typography.titleSmall)
            Text(
                "Seules les applis et les options de réparation sont actives, après ${CrashPolicy.THRESHOLD} plantages.",
                style = MaterialTheme.typography.bodyMedium,
                color = Lune.copy(alpha = 0.85f),
            )
        }

        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("On répare ?", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = lastError?.let { "Dernière erreur à ${formatTime(it.at)} : ${it.summary}." }
                        ?: "Aucune erreur détaillée n'a été enregistrée.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LuneAttenuee,
                )
                Text(
                    "Voici ce que tu peux faire, de la solution la plus douce à la plus radicale.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LuneAttenuee,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                RepairOption(
                    number = 1,
                    title = "Redémarrer normalement",
                    detail = "Le launcher repart comme avant. S'il plante encore, tu reviendras ici.",
                    onClick = onRestartNormally,
                    modifier = Modifier.focusRequester(firstOption),
                )
                RepairOption(
                    number = 2,
                    title = "Ouvrir l'accueil d'origine",
                    detail = "Le launcher reste installé, tu reviens quand tu veux.",
                    onClick = onOpenStock,
                )
            }

            Column(Modifier.weight(1f)) {
                Text("Tes applis restent accessibles", style = MaterialTheme.typography.headlineSmall)
                when {
                    apps == null -> Text("Chargement…", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.bleed(16.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(apps, key = { it.packageName }) { app -> AppTile(app = app, onClick = { onOpen(app) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepairOption(number: Int, title: String, detail: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp).background(Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.titleSmall)
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
            }
        }
    }
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(TimeFormat)
