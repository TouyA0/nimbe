package io.github.touya0.nimbe.feature.streaming

import io.github.touya0.nimbe.ui.components.NimbeIcons

import androidx.compose.runtime.Composable
import io.github.touya0.nimbe.data.jellyfin.MediaItem
import io.github.touya0.nimbe.data.streaming.StreamingService
import io.github.touya0.nimbe.data.streaming.StreamingTitle
import io.github.touya0.nimbe.data.streaming.TitleKind
import io.github.touya0.nimbe.ui.components.PillButton

/**
 * Le même film ou la même série sur les plateformes du profil, pour une fiche Jellyfin ; null tant
 * qu'on ne sait pas, ou s'il n'y est pas. Rien n'est demandé sans plateforme choisie.
 */
@Composable
fun rememberAlsoOn(item: MediaItem, kind: TitleKind): Pair<StreamingTitle, List<StreamingService>>? {
    val ui = LocalStreaming.current ?: return null
    if (ui.services.isEmpty()) return null
    // Pas de clé de mémoire : la recherche garde ses réponses sous une autre forme.
    val found = rememberStreaming(null, item.id, ui.services) { find(item.name, item.year, item.tmdbId, kind, ui.services) }.value ?: return null
    val services = found.services.filter { it in ui.services }
    return if (services.isEmpty()) null else found to services
}

/** « ▶  Aussi sur Disney+ » : ouvre directement l'appli sur le titre (et le note pour « Continuer à regarder »). */
@Composable
fun AlsoOnButtons(alsoOn: Pair<StreamingTitle, List<StreamingService>>?) {
    val ui = LocalStreaming.current ?: return
    val (title, services) = alsoOn ?: return
    services.forEach { service -> PillButton("Aussi sur ${service.label}", icon = NimbeIcons.OpenInNew, onClick = { ui.onLaunch(title, service, false) }) }
}
