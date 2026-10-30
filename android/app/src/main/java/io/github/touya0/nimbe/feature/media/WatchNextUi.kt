package io.github.touya0.nimbe.feature.media

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.streaming.StreamingService
import io.github.touya0.nimbe.data.watchnext.WatchNextProgram
import io.github.touya0.nimbe.data.watchnext.WatchNextReader
import io.github.touya0.nimbe.data.watchnext.WatchNextRules
import io.github.touya0.nimbe.feature.apps.tileColor
import io.github.touya0.nimbe.feature.streaming.StreamingImage
import io.github.touya0.nimbe.ui.theme.FilmTitleStyle
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "watch_next"
private const val ASKED = "permission_asked"

/**
 * Les contenus en cours des autres applis, relus à chaque retour sur Nimbe. Android demande l'accord une fois, à la
 * première ouverture de l'accueil par un profil qui a cette rangée ; refusé, la rangée reste celle de Jellyfin.
 * [enabled] à false (Invité, Enfant, rangée masquée) : rien n'est demandé ni lu, cette liste est celle de toute la maison.
 */
@Composable
fun rememberWatchNext(enabled: Boolean, hidden: Set<String>): List<WatchNextProgram> {
    val context = LocalContext.current
    val reader = remember(context) { WatchNextReader(context.applicationContext) }
    var programs by remember { mutableStateOf(emptyList<WatchNextProgram>()) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { attempt++ }

    LaunchedEffect(enabled) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (enabled && !reader.allowed() && !prefs.getBoolean(ASKED, false)) {
            prefs.edit().putBoolean(ASKED, true).apply()
            permission.launch(WatchNextReader.PERMISSION)
        }
    }
    LifecycleResumeEffect(enabled, attempt, hidden) {
        val job = scope.launch {
            programs = if (enabled && reader.allowed()) {
                withContext(Dispatchers.IO) { WatchNextRules.select(reader.read(), context.packageName, hidden) }
            } else {
                emptyList()
            }
        }
        onPauseOrDispose { job.cancel() }
    }
    return programs
}

/** La couleur de la plateforme si Nimbe la connaît, sinon celle de la tuile de l'appli. */
private fun badgeColor(program: WatchNextProgram): Color =
    StreamingService.entries.firstOrNull { it.packageName == program.packageName }?.let { Color(it.color) } ?: tileColor(program.packageName)

private fun detail(program: WatchNextProgram): String = when {
    program.next -> "À suivre sur ${program.appLabel}"
    program.positionMs > 0 -> "${MediaText.duration(program.remainingMinutes.coerceAtLeast(1))} restantes sur ${program.appLabel}"
    else -> "Sur ${program.appLabel}"
}

/** Vignette 16:9 d'un contenu en cours dans une autre appli : image, appli d'origine, progression. */
@Composable
fun WatchNextCard(program: WatchNextProgram, onFocus: () -> Unit, modifier: Modifier = Modifier, width: Dp = 196.dp) {
    val context = LocalContext.current
    val line = detail(program)
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MediaSurface(
            onClick = {
                if (!WatchNextReader(context.applicationContext).open(program)) {
                    Toast.makeText(context, "Impossible d'ouvrir ${program.appLabel}", Toast.LENGTH_SHORT).show()
                }
            },
            onFocus = onFocus,
            restoreFocus = false,
            onRestored = {},
            label = listOfNotNull(program.title, program.episodeLine, line).joinToString(", "),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            dimmed = false,
        ) {
            Box(Modifier.fillMaxSize().background(PanneauClair)) {
                Text(
                    program.title.uppercase(),
                    style = FilmTitleStyle.copy(fontSize = 24.sp, lineHeight = 24.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                )
                StreamingImage(program.image, Modifier.fillMaxSize())
                SourceBadge(program.appLabel.uppercase(), Modifier.align(Alignment.TopStart).padding(8.dp), color = badgeColor(program))
                if (program.positionMs > 0) {
                    ProgressBar(program.progress, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp))
                }
            }
        }
        Text(program.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(line, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Haut d'écran pour un contenu Watch Next. Les applis ne donnent pas de résumé : titre, épisode et progression. */
@Composable
fun WatchNextHero(program: WatchNextProgram, modifier: Modifier = Modifier) {
    Column(modifier.width(HeroWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceBadge(program.appLabel.uppercase(), color = badgeColor(program))
            Kicker(if (program.next) "ÉPISODE SUIVANT" else CONTINUE_KICKER)
        }
        Text(program.title.uppercase(), style = titleStyle(program.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
        program.episodeLine?.let { Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (program.positionMs > 0) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(program.progress, Modifier.width(220.dp))
                Text("${MediaText.duration(program.remainingMinutes.coerceAtLeast(1))} restantes", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
