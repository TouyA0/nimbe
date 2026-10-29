package io.github.touya0.nimbe.feature.layout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.layout.HomeRow
import io.github.touya0.nimbe.data.layout.ProfileLayout
import io.github.touya0.nimbe.shell.Tab
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair

fun homeRowLabel(key: String): String = HomeRow.fromKey(key)?.label ?: key

fun tabLabel(key: String): String = Tab.fromKey(key)?.label ?: key

/** Miniature de l'accueil, mise à jour à chaque changement : onglets en haut, puis les rangées affichées. */
@Composable
fun HomePreview(layout: ProfileLayout, width: Dp = 280.dp) {
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Aperçu", style = MaterialTheme.typography.titleSmall, color = LuneAttenuee)
        Column(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Nuit, RoundedCornerShape(10.dp))
                .border(1.dp, PanneauClair, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                layout.visibleTabs.joinToString("  ") { tabLabel(it) },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = LuneAttenuee,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Le haut de l'écran : le contenu mis en avant.
            Box(Modifier.fillMaxWidth().height(18.dp).background(PanneauClair.copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
            layout.visibleRows.forEach { row ->
                Column {
                    Text(row.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Lune, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(5) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(if (row == HomeRow.Latest) 16.dp else 10.dp)
                                    .background(if (row == HomeRow.Apps) Focus.copy(alpha = 0.35f) else PanneauClair, RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
            }
            if (layout.visibleRows.isEmpty()) {
                Text("Aucune rangée", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = LuneAttenuee)
            }
        }
    }
}

/**
 * Mode « Modifier l'accueil » : les rangées en grand, à déplacer ou masquer directement,
 * avec l'aperçu à côté. Chaque changement est enregistré tout de suite pour le profil actif.
 */
@Composable
fun HomeEditScreen(layout: ProfileLayout, profileName: String, onChange: (ProfileLayout) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(Nuit).padding(horizontal = 56.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Modifier l'accueil", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Profil $profileName. OK pour prendre une rangée, Haut et Bas pour la déplacer, OK pour la poser. " +
                        "OK maintenu pour la masquer.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LuneAttenuee,
                )
            }
            PillButton("Terminer", onClick = onClose)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            LayoutEditor(
                items = layout.homeRows,
                label = ::homeRowLabel,
                onChange = { onChange(layout.copy(homeRows = it)) },
                modifier = Modifier
                    .weight(1f)
                    .background(Panneau, RoundedCornerShape(20.dp))
                    // La marge dans la zone qui défile : l'anneau de focus de la première rangée n'est pas coupé.
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .focusRequester(first)
                    .focusGroup(),
                rowHeight = 72.dp,
            )
            HomePreview(layout, width = 340.dp)
        }
    }
}
