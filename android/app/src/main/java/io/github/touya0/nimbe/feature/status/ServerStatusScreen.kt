package io.github.touya0.nimbe.feature.status

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.homeassistant.HaConnection
import io.github.touya0.nimbe.data.homeassistant.HaEntity
import io.github.touya0.nimbe.data.homeassistant.HaLive
import io.github.touya0.nimbe.data.homeassistant.SensorCatalog
import io.github.touya0.nimbe.data.homeassistant.SensorKind
import io.github.touya0.nimbe.data.jellyfin.JellyfinHealth
import io.github.touya0.nimbe.feature.homeassistant.HomeAssistantColor
import io.github.touya0.nimbe.feature.onboarding.JellyfinColor
import io.github.touya0.nimbe.feature.onboarding.ServiceInitial
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Ce que la TV sait de Jellyfin par elle-même ; [linked] false si aucun Jellyfin n'est relié. */
data class JellyfinWatch(val linked: Boolean, val health: JellyfinHealth)

/**
 * Écran « État du serveur », ouvert par le voyant de l'en-tête. Il dit ce qui va, ce qui ne va pas,
 * et d'où vient l'information : Home Assistant en direct, ou dernières valeurs reçues (grisées, avec leur heure).
 */
@Composable
fun ServerStatusScreen(verdict: ServerVerdict, ha: HaLive, jellyfin: JellyfinWatch, onRetry: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val action = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { action.requestFocus() } }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val live = ha.connection is HaConnection.Live
    val followed = ha.sensors.mapNotNull { ha.entities[it] }
    val measures = followed.filter { SensorCatalog.kindOf(it) != SensorKind.Service }
    val services = followed.filter { SensorCatalog.kindOf(it) == SensorKind.Service }

    Column(
        Modifier.fillMaxSize().background(Nuit).verticalScroll(rememberScrollState()).padding(horizontal = 56.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("HOME ASSISTANT · RASPBERRY PI", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
        Text("État du serveur", style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp))

        Banner(verdict, ha, jellyfin, followed, onRetry, onClose, action)

        if (measures.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (live) "Mesures en direct" else "Dernières valeurs connues", style = MaterialTheme.typography.headlineSmall)
                val last = followed.mapNotNull { it.updatedAt }.maxOrNull()
                if (!live && last != null) {
                    Text("reçues à ${hour(last)}, ${ago(now - last)}", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
                }
            }
            measures.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { MeasureTile(it, live, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Text("Services", style = MaterialTheme.typography.headlineSmall)
        val cards = buildList<@Composable (Modifier) -> Unit> {
            if (jellyfin.linked) add { JellyfinCard(jellyfin.health, it) }
            services.forEach { entity -> add { ServiceCard(entity, live, it) } }
            if (ha.connection != HaConnection.Off) add { HomeAssistantCard(ha.connection, it) }
        }
        cards.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { card -> card(Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        val connection = ha.connection
        if (connection is HaConnection.Lost) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Text("Si ça dure : vérifie que le conteneur homeassistant tourne sur le Pi", style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
                if (connection.refused) {
                    Text("Le jeton d'accès a pu expirer → Réglages → Serveurs et comptes", style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
                }
            }
        }
    }
}

@Composable
private fun Banner(
    verdict: ServerVerdict,
    ha: HaLive,
    jellyfin: JellyfinWatch,
    followed: List<HaEntity>,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    action: FocusRequester,
) {
    val connection = ha.connection
    val jellyfinAnswers = jellyfin.linked && jellyfin.health.reachable
    val (icon, color) = when (verdict.level) {
        ServerLevel.Ok -> "✓" to EnLigne
        ServerLevel.Unknown, ServerLevel.Hidden -> "?" to LuneAttenuee
        ServerLevel.ServiceDown, ServerLevel.NoInternet -> "!" to Focus
        ServerLevel.PiUnreachable, ServerLevel.NoNetwork -> "!" to Alerte
    }
    val (title, detail) = when (verdict.level) {
        ServerLevel.NoNetwork -> "Le stick n'est plus connecté au réseau" to
            "Sans réseau, Nimbe ne peut joindre ni le Pi ni Home Assistant. Vérifie le Wi-Fi ou le câble du stick."
        ServerLevel.PiUnreachable -> "Ton Raspberry Pi ne répond pas" to
            ("Jellyfin ne répond pas" + since(jellyfin.health.since) + (if (connection is HaConnection.Off) "" else ", Home Assistant non plus") +
                ". Films et Séries sont en pause ; la TV, la musique et tes applis marchent normalement.")
        ServerLevel.ServiceDown -> (verdict.down.singleOrNull()?.let { "$it ne répond pas" } ?: "${verdict.down.size} services ne répondent pas") to
            ("Le Raspberry Pi est allumé : Home Assistant répond. En panne : ${verdict.down.joinToString(", ")}. " +
                "Vérifie que le conteneur tourne sur le Pi.")
        ServerLevel.NoInternet -> "La box n'a plus internet" to
            "Ce qui est chez toi (Jellyfin, Home Assistant) marche toujours. La TV et la musique reviendront avec la connexion."
        ServerLevel.Unknown -> when {
            connection is HaConnection.Lost && connection.refused -> "Home Assistant refuse le token de cette TV" to
                (directly(jellyfinAnswers) + "Les valeurs ci-dessous sont les dernières reçues.")
            connection is HaConnection.Lost -> "Home Assistant ne répond plus depuis ${spokenHour(connection.since)}" to
                ("Nimbe ne sait donc pas si tout va bien. " + directly(jellyfinAnswers) + "Les valeurs ci-dessous sont les dernières reçues.")
            else -> {
                val silent = ha.sensors.filter { id -> ha.entities[id]?.unknown != false }
                    .map { id -> ha.entities[id]?.label ?: id }
                "Certaines mesures sont inconnues" to
                    ("Home Assistant répond, mais ${silent.size} capteur${if (silent.size > 1) "s ne donnent" else " ne donne"} pas de valeur : " +
                        silent.joinToString(", ") + ". L'intégration qui les fournit est peut-être arrêtée.")
            }
        }
        ServerLevel.Ok -> "Tous les services sont en ligne" to "Mesures reçues en direct de Home Assistant, ${followed.size} capteurs suivis."
        ServerLevel.Hidden -> if (connection is HaConnection.Off) {
            "Home Assistant n'est pas connecté" to "Pour suivre l'état du Pi : Réglages → Serveurs et comptes → Home Assistant."
        } else {
            "Connexion à Home Assistant…" to "Les mesures arrivent dans un instant."
        }
    }
    val retry = verdict.level != ServerLevel.Ok && verdict.level != ServerLevel.Hidden
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panneau, RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(color.copy(alpha = 0.18f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(icon, style = MaterialTheme.typography.titleLarge, color = color)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (retry) {
                PillButton("Réessayer", onClick = onRetry, modifier = Modifier.focusRequester(action))
                Text("Nouvel essai auto. toutes les 30 s", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
            } else {
                PillButton("Retour", onClick = onClose, modifier = Modifier.focusRequester(action))
            }
        }
    }
}

private fun directly(jellyfinAnswers: Boolean) =
    if (jellyfinAnswers) "Jellyfin répond encore directement : tes films marchent. " else ""

private val MeasureNames = mapOf(
    SensorKind.Processor to "Processeur",
    SensorKind.Temperature to "Température",
    SensorKind.Memory to "Mémoire",
    SensorKind.Disk to "Disque",
)

/** Une mesure du Pi ; grisée quand elle n'est plus confirmée en direct. */
@Composable
private fun MeasureTile(entity: HaEntity, live: Boolean, modifier: Modifier) {
    val kind = SensorCatalog.kindOf(entity)
    val fresh = live && !entity.unknown
    Column(
        modifier.background(Panneau, RoundedCornerShape(14.dp)).border(1.dp, PanneauClair, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(MeasureNames[kind] ?: entity.label, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 1)
        Text(
            entity.displayValue,
            style = MaterialTheme.typography.headlineMedium,
            color = if (fresh) Lune else LuneAttenuee,
        )
        Text(entity.updatedAt?.let { "à ${hour(it)}" } ?: " ", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
    }
}

@Composable
private fun JellyfinCard(health: JellyfinHealth, modifier: Modifier) {
    ServiceRow(
        letter = "J",
        letterColor = JellyfinColor,
        title = "Jellyfin",
        detail = if (health.reachable) "Répond directement · OK" else "Ne répond pas" + since(health.since),
        detailColor = if (health.reachable) EnLigne else Alerte,
        modifier = modifier,
    )
}

@Composable
private fun ServiceCard(entity: HaEntity, live: Boolean, modifier: Modifier) {
    val (detail, color) = when {
        !live -> "Dernière valeur : ${entity.displayValue.lowercase()}" + (entity.updatedAt?.let { " à ${hour(it)}" } ?: "") to LuneAttenuee
        entity.unknown -> "Inconnu" to LuneAttenuee
        entity.state == "on" -> "En ligne · vu par Home Assistant" to EnLigne
        else -> "Hors ligne · vu par Home Assistant" to Focus
    }
    ServiceRow(entity.serviceName.take(1).uppercase(), PanneauClair, entity.serviceName, detail, color, modifier)
}

@Composable
private fun HomeAssistantCard(connection: HaConnection, modifier: Modifier) {
    val (detail, color) = when (connection) {
        is HaConnection.Live -> "En ligne · en direct" to EnLigne
        is HaConnection.Lost -> (if (connection.refused) "Token refusé" else "Pas de réponse depuis ${hour(connection.since)}") to Alerte
        else -> "Connexion…" to LuneAttenuee
    }
    ServiceRow("H", HomeAssistantColor, "Home Assistant", detail, color, modifier)
}

@Composable
private fun ServiceRow(letter: String, letterColor: Color, title: String, detail: String, detailColor: Color, modifier: Modifier) {
    Row(
        modifier.background(Panneau, RoundedCornerShape(14.dp)).border(1.dp, PanneauClair, RoundedCornerShape(14.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServiceInitial(letter, letterColor)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.labelMedium, color = detailColor, maxLines = 2)
        }
    }
}

private val HourFormat = DateTimeFormatter.ofPattern("HH:mm")
private val SpokenHourFormat = DateTimeFormatter.ofPattern("H 'h' mm")

private fun hour(epochMs: Long): String = HourFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

private fun spokenHour(epochMs: Long): String = SpokenHourFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

private fun since(epochMs: Long?): String = epochMs?.let { " depuis ${hour(it)}" }.orEmpty()

/** « à l'instant », « il y a 9 min », « il y a 2 h ». */
private fun ago(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        else -> "il y a ${minutes / 60} h"
    }
}
