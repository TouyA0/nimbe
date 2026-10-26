package io.github.touya0.nimbe.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fenêtre « Mise à jour disponible », ou « n'a pas été installée » après un échec.
 * Elle occupe tout l'écran : le focus ne peut pas s'échapper vers l'accueil derrière.
 */
@Composable
fun UpdateScreen(state: UpdateState, controller: UpdateController, onClose: () -> Unit) {
    val available = state.available?.takeIf(controller::isNewer)
    val failure = state.failure?.takeIf { available == null || it.versionName == available.versionName }

    Box(Modifier.fillMaxSize().background(Nuit), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(760.dp)
                .background(Panneau, RoundedCornerShape(20.dp))
                .border(1.dp, if (failure != null) Alerte.copy(alpha = 0.35f) else PanneauClair, RoundedCornerShape(20.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when {
                failure != null -> FailureContent(failure, available, controller, onClose)
                available != null -> AvailableContent(available, state.ready, controller, onClose)
                // Plus rien à proposer (installée entre-temps, ou canal changé) : on referme.
                else -> LaunchedEffect(Unit) { onClose() }
            }
        }
    }
}

@Composable
private fun AvailableContent(manifest: UpdateManifest, ready: Boolean, controller: UpdateController, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val progress = controller.progress

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Badge("↓", PanneauClair, Lune)
        Column(Modifier.weight(1f)) {
            Text("Mise à jour disponible", style = MaterialTheme.typography.displaySmall)
            Text(
                listOfNotNull(
                    "GitHub Releases",
                    "canal bêta".takeIf { manifest.channel == UpdateChannel.Beta },
                    formatPublished(manifest.publishedAt)?.let { "publiée le $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = LuneAttenuee,
            )
        }
        // Les numéros de bêta sont longs : plus petits, pour laisser la place au titre.
        val installedName = controller.installed.versionName.orEmpty()
        val versionStyle = if (installedName.length + manifest.versionName.length > 14) {
            MaterialTheme.typography.titleMedium
        } else {
            MaterialTheme.typography.headlineSmall
        }
        Text(installedName, style = versionStyle, color = LuneAttenuee)
        Text("→", style = versionStyle, color = Focus)
        Text(manifest.versionName, style = versionStyle, color = Focus)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nouveautés", style = MaterialTheme.typography.titleMedium)
            manifest.notes.take(6).forEach { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = Focus)
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vérifications", style = MaterialTheme.typography.titleMedium)
            val checking = progress == UpdateProgress.Verifying
            CheckCard("Même signature", "Clé identique à la version installée", ready, checking)
            CheckCard("Empreinte SHA-256", "Correspond au fichier publié", ready, checking)
            CheckCard("Version plus récente", "Pas de retour en arrière possible", ready, checking)
        }
    }

    DownloadBar(manifest, ready, progress)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton(
            text = if (progress == null) "Installer maintenant" else "Préparation…",
            onClick = { controller.installNow(manifest) },
            modifier = Modifier.focusRequester(first),
        )
        PillButton("Plus tard", onClick = onClose)
        Spacer(Modifier.weight(1f))
        Text(
            "Android peut demander une\nconfirmation selon sa version",
            style = MaterialTheme.typography.labelMedium,
            color = LuneAttenuee,
        )
    }
}

@Composable
private fun DownloadBar(manifest: UpdateManifest, ready: Boolean, progress: UpdateProgress?) {
    val size = formatSize(manifest.size)
    val (label, fraction) = when {
        progress is UpdateProgress.Downloading ->
            "Téléchargement · ${formatSize(progress.received)} sur $size" to progress.received.toFloat() / progress.total
        progress == UpdateProgress.Verifying -> "Vérification du fichier…" to 1f
        ready -> "Téléchargé et vérifié · $size" to 1f
        else -> "À télécharger · $size" to 0f
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, modifier = Modifier.weight(1f))
            Text("${(fraction * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(PanneauClair, CircleShape)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(EnLigne, CircleShape))
        }
    }
}

@Composable
private fun CheckCard(title: String, detail: String, ok: Boolean, checking: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(EnLigne.copy(alpha = if (ok) 0.08f else 0f), RoundedCornerShape(10.dp))
            .border(1.dp, PanneauClair, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            ok -> StepIcon("✓", EnLigne)
            checking -> StepIcon("…", Focus)
            else -> StepIcon("–", LuneAttenuee)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(if (ok || checking) detail else "Contrôlé avant l'installation", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
        }
    }
}

@Composable
private fun FailureContent(failure: UpdateFailure, available: UpdateManifest?, controller: UpdateController, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    val close = {
        controller.dismissFailure()
        onClose()
    }
    BackHandler(onBack = close)
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val installedName = controller.installed.versionName.orEmpty()

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Badge("!", Alerte.copy(alpha = 0.18f), Alerte)
        Column {
            Text("La version ${failure.versionName} n'a pas été installée", style = MaterialTheme.typography.displaySmall)
            Text(
                "Rien n'a changé : tu utilises toujours la version $installedName, qui fonctionne normalement.",
                style = MaterialTheme.typography.bodyMedium,
                color = LuneAttenuee,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ce qui s'est passé", style = MaterialTheme.typography.titleMedium)
        UpdateStep.entries.forEach { step ->
            val status = step.ordinal.compareTo(failure.failedAt.ordinal)
            val detail = when {
                status > 0 -> "Pas faite"
                status == 0 -> "Échec"
                else -> when (step) {
                    UpdateStep.Found -> "GitHub Releases · ${failure.versionName}"
                    UpdateStep.Download -> "Fichier reçu"
                    UpdateStep.Sha256 -> "Correspond au fichier publié"
                    UpdateStep.Signature -> "Clé identique à la version installée"
                    UpdateStep.Version -> "Plus récente que $installedName"
                    UpdateStep.Install -> ""
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (status == 0) Alerte.copy(alpha = 0.10f) else PanneauClair.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    status < 0 -> StepIcon("✓", EnLigne)
                    status == 0 -> StepIcon("!", Alerte)
                    else -> StepIcon("–", LuneAttenuee)
                }
                Text(step.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(220.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
            }
        }
    }

    Text(
        "ⓘ  ${failure.reason}",
        style = MaterialTheme.typography.bodyMedium,
        color = LuneAttenuee,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PanneauClair, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (available != null) {
            PillButton(
                "Réessayer maintenant",
                onClick = {
                    controller.dismissFailure()
                    controller.installNow(available)
                },
                modifier = Modifier.focusRequester(first),
            )
            PillButton("Fermer", onClick = close)
        } else {
            PillButton("Fermer", onClick = close, modifier = Modifier.focusRequester(first))
        }
        Spacer(Modifier.weight(1f))
        Text("Sinon, Nimbe réessaiera cette nuit", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
    }
}

@Composable
private fun Badge(symbol: String, background: Color, color: Color) {
    Box(Modifier.size(52.dp).background(background, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Text(symbol, style = MaterialTheme.typography.headlineSmall, color = color)
    }
}

@Composable
private fun StepIcon(symbol: String, color: Color) {
    Box(Modifier.size(20.dp).background(color.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
        Text(symbol, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** « 8,4 Mo » : les APK font quelques Mo, une décimale aide à voir la progression. */
fun formatSize(bytes: Long): String = String.format(Locale.FRENCH, "%.1f Mo", bytes / (1024.0 * 1024.0))

private val PublishedFormat = DateTimeFormatter.ofPattern("d MMMM 'à' HH:mm", Locale.FRENCH)

private fun formatPublished(iso: String): String? =
    runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).format(PublishedFormat) }.getOrNull()
