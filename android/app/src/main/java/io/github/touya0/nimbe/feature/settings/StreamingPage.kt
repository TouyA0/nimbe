package io.github.touya0.nimbe.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.core.storage.StreamingChoice
import io.github.touya0.nimbe.data.streaming.StreamingService
import io.github.touya0.nimbe.feature.streaming.ServiceBadge
import io.github.touya0.nimbe.ui.theme.LuneAttenuee

/**
 * Réglages → Plateformes de streaming : les abonnements de la maison. Rien n'est coché d'office :
 * beaucoup de sticks ont Netflix ou Prime Video préinstallés, sans abonnement derrière.
 * [installed] : paquets installés, null pendant le chargement.
 */
@Composable
internal fun StreamingPage(choice: StreamingChoice?, installed: Set<String>?, onChange: (Set<String>?) -> Unit) {
    PageTitle(
        "Plateformes de streaming",
        "Coche tes abonnements : leurs films et séries apparaissent dans Films et Séries, à côté de Jellyfin. Pour toute la TV.",
    )
    if (choice == null || installed == null) {
        Text("Chargement…", style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
        return
    }
    val current = choice.codes.orEmpty()
    SettingsGroupTitle("Mes abonnements")
    // Les plateformes dont l'appli est sur le stick d'abord : ce sont les seules qui peuvent s'ouvrir.
    StreamingService.entries.sortedBy { it.packageName !in installed }.forEach { service ->
        val on = service.code in current
        val hasApp = service.packageName in installed
        SettingRow(
            title = service.label,
            detail = when {
                !hasApp && on -> "Appli absente du stick : installe-la depuis le Play Store pour voir son catalogue"
                !hasApp -> "Appli absente du stick"
                on -> "Son catalogue apparaît dans Films et Séries"
                else -> "Appli installée · OK pour l'ajouter si tu es abonné"
            },
            onClick = { onChange(if (on) current - service.code else current + service.code) },
            // Largeur fixe : les noms s'alignent quelle que soit la longueur du badge.
            leading = { Box(Modifier.width(92.dp)) { ServiceBadge(service) } },
            trailing = { Toggle(on) },
        )
    }
    SettingsGroupTitle("Comment ça marche")
    Text(
        "Nimbe ne se connecte pas à tes comptes et ne voit pas ta progression : il demande à JustWatch ce qui est disponible " +
            "en France sur ces plateformes, puis ouvre l'appli sur le titre choisi. Les titres que tu y ouvres reviennent " +
            "dans « Continuer à regarder » pendant un mois. Un profil Enfant ne voit que les titres tous publics de ses applis autorisées ; " +
            "l'invité ne voit pas les comptes de la famille.",
        style = MaterialTheme.typography.bodyMedium,
        color = LuneAttenuee,
        modifier = Modifier.width(640.dp),
    )
}
