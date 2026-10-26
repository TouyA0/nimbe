package io.github.touya0.nimbe.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.DisplaySettings
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Les rubriques déjà construites ; les autres de la maquette arriveront avec leurs fonctions. */
enum class SettingsSection(val label: String) {
    Display("Affichage et accessibilité"),
    About("À propos et diagnostic"),
}

/** Liens vers Android, fournis par l'activité. */
class SettingsLinks(
    val accessibility: () -> Unit,
    val androidSettings: () -> Unit,
    val stockLauncher: () -> Unit,
)

@Composable
fun SettingsScreen(
    display: DisplaySettings,
    onDisplayChange: (DisplaySettings) -> Unit,
    links: SettingsLinks,
    onClose: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(SettingsSection.Display) }
    val firstSection = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { runCatching { firstSection.requestFocus() } }

    // Recalculé à chaque changement de rubrique, et après « Vider le cache ».
    var aboutVersion by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val about by produceState<AboutInfo?>(null, aboutVersion, section) { value = loadAbout(context) }
    // Au retour des réglages Android (TalkBack, par exemple), on relit l'état du stick.
    LifecycleResumeEffect(Unit) {
        aboutVersion++
        onPauseOrDispose {}
    }

    Row(Modifier.fillMaxSize().background(Nuit)) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(Panneau).padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NavItem("‹  Retour à l'accueil", selected = false, onClick = onClose)
            Text("Réglages", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(vertical = 12.dp))
            Column(Modifier.focusRestorer(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsSection.entries.forEachIndexed { index, item ->
                    NavItem(
                        label = item.label,
                        selected = item == section,
                        onClick = { section = item },
                        onFocus = { section = item },
                        modifier = if (index == 0) Modifier.focusRequester(firstSection) else Modifier,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            about?.let { Text("Nimbe ${it.version}", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee) }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (section) {
                SettingsSection.Display -> DisplayPage(display, onDisplayChange, about, links)
                SettingsSection.About -> AboutPage(about, links, onCacheCleared = { aboutVersion++ })
            }
        }
    }
}

@Composable
private fun DisplayPage(display: DisplaySettings, onChange: (DisplaySettings) -> Unit, about: AboutInfo?, links: SettingsLinks) {
    PageTitle("Affichage et accessibilité", "Lire Nimbe facilement de loin. Pour toute la TV.")
    SettingsGroupTitle("Lisibilité")
    TextSizeRow(display.textSize) { onChange(display.copy(textSize = it)) }
    ToggleRow("Contraste élevé", "Textes en blanc plein, focus blanc épais", display.highContrast) {
        onChange(display.copy(highContrast = it))
    }
    ToggleRow("Réduire les animations", "Pas d'agrandissement au focus", display.reduceMotion) {
        onChange(display.copy(reduceMotion = it))
    }
    ToggleRow("Rappels de touches OK", "En bas des écrans", display.keyHints) {
        onChange(display.copy(keyHints = it))
    }
    LinkRow(
        "Lecteur d'écran (TalkBack)",
        "Chaque élément est lu à voix haute · réglé dans Android",
        value = if (about?.talkBackOn == true) "Activé" else "Désactivé",
        onClick = links.accessibility,
    )
}

@Composable
private fun AboutPage(about: AboutInfo?, links: SettingsLinks, onCacheCleared: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    PageTitle("À propos et diagnostic", "Version de Nimbe, état du stick et aide au dépannage.")
    if (about == null) {
        Text("Chargement…", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
        return
    }
    SettingsGroupTitle("Ce stick")
    LinkRow("Nimbe ${about.version}", about.device, value = about.android, onClick = links.androidSettings)
    SettingRow(
        title = "Stockage",
        detail = "Cache de Nimbe ${formatBytes(about.cacheBytes)} · libre ${formatBytes(about.freeBytes)} " +
            "sur ${formatBytes(about.totalBytes)}",
        onClick = { scope.launch { clearCache(context); onCacheCleared() } },
        trailing = { Text("Vider le cache", style = MaterialTheme.typography.titleSmall) },
    )

    SettingsGroupTitle("Dépannage")
    SettingRow(
        title = "Journal des plantages",
        detail = about.lastError?.let { "Dernier le ${formatDate(it.at)} : ${it.summary}" } ?: "Aucun plantage enregistré",
        onClick = {},
    )
    LinkRow("Accueil d'origine du stick", "Toujours installé, c'est le secours", value = "Ouvrir", onClick = links.stockLauncher)
    LinkRow("Réglages Android", "Réseau, son, applis, mises à jour du stick", value = "Ouvrir", onClick = links.androidSettings)
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.displaySmall)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun NavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Lune else Color.Transparent,
            contentColor = if (selected) Nuit else Lune,
            focusedContainerColor = if (selected) Lune else PanneauClair,
            focusedContentColor = if (selected) Nuit else Lune,
            pressedContainerColor = Lune,
            pressedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

private val DateFormat = DateTimeFormatter.ofPattern("d MMMM 'à' HH:mm", Locale.FRENCH)

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateFormat)
