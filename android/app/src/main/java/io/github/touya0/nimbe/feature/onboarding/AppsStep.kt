package io.github.touya0.nimbe.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.core.storage.LauncherPrefs
import io.github.touya0.nimbe.feature.apps.AppLauncher
import io.github.touya0.nimbe.feature.apps.AppTile
import io.github.touya0.nimbe.feature.apps.AppsRepository
import io.github.touya0.nimbe.feature.apps.Dock
import io.github.touya0.nimbe.feature.apps.rememberInstalledApps
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.Spinner
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import io.github.touya0.nimbe.update.UpdateController
import kotlinx.coroutines.launch

/**
 * Dernière étape : les applis de « Mes applis », puis la vérification de ce qui se règle hors de Nimbe
 * (touche Accueil, écran de veille…). Rien n'y est obligatoire.
 */
@Composable
internal fun AppsStepContent(
    repository: AppsRepository,
    prefs: LauncherPrefs,
    launcher: AppLauncher,
    updates: UpdateController,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Panel(Modifier.weight(1f)) { DockPicker(repository, prefs) }
        Panel(Modifier.weight(1f)) { Checks(launcher, updates, onFinish, onBack) }
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .background(Panneau, RoundedCornerShape(20.dp))
            .border(1.dp, PanneauClair, RoundedCornerShape(20.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Grille des applis installées : OK ajoute à la fin du dock ou retire ; le numéro donne la place dans le dock. */
@Composable
private fun DockPicker(repository: AppsRepository, prefs: LauncherPrefs) {
    val scope = rememberCoroutineScope()
    val apps = rememberInstalledApps(repository)
    val saved by prefs.dock.collectAsStateWithLifecycle(initialValue = null)
    val hidden by prefs.hidden.collectAsStateWithLifecycle(initialValue = emptySet())
    // Le dock affiché sur l'accueil (celui par défaut tant qu'on n'a rien choisi) sert de point de départ.
    val dock = if (apps == null || saved == null) null else Dock.resolve(saved?.packages, apps, hidden).map { it.packageName }

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mes applis", style = MaterialTheme.typography.headlineSmall)
        Text(
            when (dock?.size) {
                null -> ""
                0 -> "Aucune choisie · OK pour ajouter"
                1 -> "1 choisie · OK pour ajouter ou retirer"
                else -> "${dock.size} choisies · OK pour ajouter ou retirer"
            },
            style = MaterialTheme.typography.labelMedium,
            color = LuneAttenuee,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    val visible = apps?.filterNot { it.packageName in hidden }
    if (visible == null || dock == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spinner()
            Text("Lecture des applis du stick…", style = MaterialTheme.typography.titleSmall)
        }
        return
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        // Place pour l'agrandissement et l'anneau du focus, que la grille couperait.
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(visible, key = { _, app -> app.packageName }) { index, app ->
            val position = dock.indexOf(app.packageName)
            Box {
                AppTile(
                    app,
                    onClick = {
                        val next = if (position >= 0) Dock.remove(dock, app.packageName) else Dock.add(dock, app.packageName)
                        scope.launch { prefs.setDock(next) }
                    },
                    modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                )
                if (position >= 0) {
                    // Au-dessus de la tuile, même quand elle grandit avec le focus.
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .zIndex(2f)
                            .offset(x = 6.dp, y = (-6).dp)
                            .size(26.dp)
                            .background(Focus, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${position + 1}", style = MaterialTheme.typography.labelMedium, color = Nuit)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Checks(launcher: AppLauncher, updates: UpdateController, onFinish: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    // Relu au retour des réglages Android et à chaque « Revérifier ».
    var round by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        round++
        onPauseOrDispose {}
    }
    val checks by produceState<List<SetupCheck>?>(null, round) {
        value = SetupChecks.build(SetupChecks.read(context, updates.canInstall()))
    }

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vérifications", style = MaterialTheme.typography.headlineSmall)
        Text("Rien d'obligatoire", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, modifier = Modifier.padding(bottom = 4.dp))
    }
    // Chaque ligne prend le focus, même sans action : c'est ce qui fait défiler la liste à la télécommande.
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        checks?.forEach { check ->
            CheckRow(check, onClick = when (check.action) {
                CheckAction.Accessibility -> launcher::openAccessibilitySettings
                CheckAction.Screensaver -> launcher::openScreensaverSettings
                CheckAction.InstallPermission -> updates::openInstallPermission
                null -> ({})
            })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Terminer", onClick = onFinish)
        PillButton("Revérifier", onClick = { round++ })
        PillButton("Retour", onClick = onBack)
    }
}

/** Une vérification ; OK ouvre le réglage Android correspondant quand il y en a un. */
@Composable
private fun CheckRow(check: SetupCheck, onClick: () -> Unit) {
    val color = when (check.level) {
        CheckLevel.Ok -> EnLigne
        CheckLevel.ToDo -> Focus
        CheckLevel.Unknown -> LuneAttenuee
    }
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(check.title, style = MaterialTheme.typography.titleSmall)
                Text(check.detail, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
                check.command?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = Lune)
                }
            }
            Text(check.value, style = MaterialTheme.typography.titleSmall, color = color)
        }
    }
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PanneauClair,
            contentColor = Lune,
            focusedContainerColor = PanneauClair,
            focusedContentColor = Lune,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) { content() }
}
