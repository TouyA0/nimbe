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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import io.github.touya0.nimbe.data.homeassistant.HistoryPoint
import io.github.touya0.nimbe.data.homeassistant.HistorySeries
import io.github.touya0.nimbe.data.homeassistant.HistorySeriesBuilder
import io.github.touya0.nimbe.ui.components.Spinner
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import kotlin.math.roundToInt
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
fun ServerStatusScreen(
    verdict: ServerVerdict,
    ha: HaLive,
    jellyfin: JellyfinWatch,
    loadHistory: suspend (String) -> List<HistoryPoint>,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
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
    val watched = measures.filter { live && Watch.needed(it) }
    val ok = verdict.level == ServerLevel.Ok

    Column(
        Modifier.fillMaxSize().background(Nuit).verticalScroll(rememberScrollState()).padding(horizontal = 56.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("HOME ASSISTANT · RASPBERRY PI", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
                Text("État du serveur", style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp))
            }
            if (ok) AllGoodCard(watched)
        }
        if (!ok) Banner(verdict, ha, jellyfin, followed, onRetry, onClose, action)

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Panel(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Raspberry Pi", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    val last = followed.mapNotNull { it.updatedAt }.maxOrNull()
                    Text(
                        when {
                            live -> "En direct"
                            last != null -> "Reçues à ${hour(last)}, ${ago(now - last)}"
                            else -> "Pas encore de mesure"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = LuneAttenuee,
                    )
                }
                if (measures.isEmpty()) {
                    Text(
                        "Aucune mesure du Pi suivie. Choisis-les dans Réglages → Serveurs et comptes → Home Assistant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LuneAttenuee,
                    )
                }
                measures.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { MeasureTile(it, live, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                measures.firstOrNull { SensorCatalog.kindOf(it) == SensorKind.Temperature }?.let { temperature ->
                    TemperatureHistory(temperature, loadHistory)
                }
            }
            Panel(Modifier.weight(1f)) {
                Text("Services", style = MaterialTheme.typography.headlineSmall)
                // Le premier service prend le focus quand il n'y a pas de bandeau : OK revérifie tout.
                var first = ok
                fun focusFirst(): Modifier = if (first) Modifier.focusRequester(action).also { first = false } else Modifier
                if (jellyfin.linked) {
                    val health = jellyfin.health
                    ServiceRow(
                        "J", JellyfinColor, "Jellyfin",
                        if (health.reachable) "Répond directement" else "Ne répond pas" + since(health.since),
                        if (health.reachable) Status.Ok else Status.Down,
                        onRetry, focusFirst(),
                    )
                }
                services.filterNot { jellyfin.linked && it.serviceName.equals("Jellyfin", ignoreCase = true) }.forEach { entity ->
                    val (detail, status) = when {
                        !live -> "Dernière valeur : ${entity.displayValue.lowercase()}" + (entity.updatedAt?.let { " à ${hour(it)}" } ?: "") to Status.Unknown
                        entity.unknown -> "Home Assistant ne sait pas" to Status.Unknown
                        entity.state == "on" -> "En ligne · vu par Home Assistant" to Status.Ok
                        else -> "Hors ligne · vu par Home Assistant" to Status.Down
                    }
                    ServiceRow(entity.serviceName.take(1).uppercase(), PanneauClair, entity.serviceName, detail, status, onRetry, focusFirst())
                }
                when (val connection = ha.connection) {
                    is HaConnection.Live -> ServiceRow("H", HomeAssistantColor, "Home Assistant", "En ligne · mesures en direct", Status.Ok, onRetry, focusFirst())
                    is HaConnection.Lost -> ServiceRow(
                        "H", HomeAssistantColor, "Home Assistant",
                        if (connection.refused) "Token refusé" else "Pas de réponse depuis ${hour(connection.since)}",
                        Status.Down, onRetry, focusFirst(),
                    )
                    HaConnection.Connecting -> ServiceRow("H", HomeAssistantColor, "Home Assistant", "Connexion…", Status.Unknown, onRetry, focusFirst())
                    HaConnection.Off -> Unit
                }
                watched.filter { SensorCatalog.kindOf(it) == SensorKind.Disk }.forEach { disk ->
                    ServiceRow("D", Focus.copy(alpha = 0.7f), "Stockage", "Disque rempli à ${disk.displayValue}", Status.Watch, onRetry, focusFirst())
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Hint("OK", "vérifier maintenant")
            Hint("Retour", "revenir à l'accueil")
            val connection = ha.connection
            if (connection is HaConnection.Lost) {
                Text(
                    if (connection.refused) "Le jeton d'accès a pu expirer → Réglages → Serveurs et comptes"
                    else "Si ça dure : vérifie que le conteneur homeassistant tourne sur le Pi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LuneAttenuee,
                )
            } else {
                Text("Voyant de l'accueil : orange ou rouge en cas de souci", style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
            }
        }
    }
}

/** Carte verte en haut à droite, quand tout va bien ; signale les mesures à surveiller. */
@Composable
private fun AllGoodCard(watched: List<HaEntity>) {
    Row(
        Modifier
            .background(EnLigne.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .border(1.dp, EnLigne.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).background(EnLigne, CircleShape), contentAlignment = Alignment.Center) {
            Text("✓", style = MaterialTheme.typography.titleSmall, color = Nuit)
        }
        Column {
            Text("Tous les services sont en ligne", style = MaterialTheme.typography.titleMedium)
            val names = watched.map { (MeasureNames[SensorCatalog.kindOf(it)] ?: it.label).lowercase() }
            Text(
                when (names.size) {
                    0 -> "Mesures reçues en direct"
                    1 -> "1 point à surveiller : ${names.first()} · en direct"
                    else -> "${names.size} points à surveiller : ${names.joinToString(", ")} · en direct"
                },
                style = MaterialTheme.typography.labelMedium,
                color = LuneAttenuee,
            )
        }
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .background(Panneau, RoundedCornerShape(20.dp))
            .border(1.dp, PanneauClair, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun Hint(key: String, action: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(key, style = MaterialTheme.typography.titleSmall)
        Text(action, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
    }
}

/** Courbe des 24 dernières heures, demandée à Home Assistant à l'ouverture de l'écran. */
@Composable
private fun TemperatureHistory(temperature: HaEntity, loadHistory: suspend (String) -> List<HistoryPoint>) {
    val series by produceState<Result<HistorySeries>?>(null, temperature.id) {
        val to = System.currentTimeMillis()
        value = runCatching { HistorySeriesBuilder.build(loadHistory(temperature.id), to - DAY_MS, to) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text("Température sur 24 h", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, modifier = Modifier.weight(1f))
            series?.getOrNull()?.max?.let { max ->
                Text("max ${max.value.roundToInt()} °C · ${spokenHourOnly(max.at)}", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
            }
        }
        val current = series
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
            when {
                current == null -> Spinner()
                current.isFailure -> Text("Historique indisponible : Home Assistant ne répond pas", style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
                current.getOrThrow().points.size < 2 -> Text("Pas encore assez de mesures", style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
                else -> HistoryChart(current.getOrThrow(), Modifier.fillMaxSize())
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
                ". Films et Séries sont en pause ; la TV et tes applis marchent normalement.")
        ServerLevel.ServiceDown -> (verdict.down.singleOrNull()?.let { "$it ne répond pas" } ?: "${verdict.down.size} services ne répondent pas") to
            ("Le Raspberry Pi est allumé : Home Assistant répond. En panne : ${verdict.down.joinToString(", ")}. " +
                "Vérifie que le conteneur tourne sur le Pi.")
        ServerLevel.NoInternet -> "La box n'a plus internet" to
            "Ce qui est chez toi (Jellyfin, Home Assistant) marche toujours. La TV et les applis en ligne reviendront avec la connexion."
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
            Text(icon, style = MaterialTheme.typography.headlineSmall, color = color)
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

/** Une mesure du Pi avec sa jauge ; grisée quand elle n'est plus confirmée en direct, ambre à surveiller. */
@Composable
private fun MeasureTile(entity: HaEntity, live: Boolean, modifier: Modifier) {
    val kind = SensorCatalog.kindOf(entity)
    val fresh = live && !entity.unknown
    val number = entity.state.toDoubleOrNull()
    Column(
        modifier.background(PanneauClair.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(MeasureNames[kind] ?: entity.label, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, modifier = Modifier.weight(1f), maxLines = 1)
            if (!fresh) entity.updatedAt?.let { Text("à ${hour(it)}", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee) }
        }
        Text(entity.displayValue, style = MaterialTheme.typography.displaySmall, color = if (fresh) Lune else LuneAttenuee)
        // Jauge : pourcentage, ou température sur 0-85 °C (le Pi 5 ralentit vers 85 °C).
        val fraction = number?.let { if (kind == SensorKind.Temperature) it / 85.0 else it / 100.0 }?.coerceIn(0.0, 1.0)
        Box(Modifier.fillMaxWidth().height(5.dp).background(PanneauClair, CircleShape)) {
            if (fraction != null) {
                val color = when {
                    !fresh -> LuneAttenuee
                    Watch.needed(entity) -> Focus
                    else -> EnLigne
                }
                Box(Modifier.fillMaxWidth(fraction.toFloat()).height(5.dp).background(color, CircleShape))
            }
        }
    }
}

private enum class Status(val label: String) {
    Ok("OK"),
    Down("En panne"),
    Unknown("Inconnu"),
    Watch("À surveiller");

    /** Lue à l'affichage : le gris suit le réglage « contraste élevé ». */
    val color: Color
        @Composable get() = when (this) {
            Ok -> EnLigne
            Down -> Alerte
            Unknown -> LuneAttenuee
            Watch -> Focus
        }
}

/** Une ligne de service ; OK revérifie tout (Jellyfin et Home Assistant). */
@Composable
private fun ServiceRow(
    letter: String,
    letterColor: Color,
    title: String,
    detail: String,
    status: Status,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PanneauClair.copy(alpha = 0.5f),
            contentColor = Lune,
            focusedContainerColor = PanneauClair,
            focusedContentColor = Lune,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ServiceInitial(letter, letterColor)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("● ${status.label}", style = MaterialTheme.typography.labelMedium, color = status.color)
        }
    }
}

private val HourFormat = DateTimeFormatter.ofPattern("HH:mm")
private val SpokenHourFormat = DateTimeFormatter.ofPattern("H 'h' mm")

private fun hour(epochMs: Long): String = HourFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

private fun spokenHour(epochMs: Long): String = SpokenHourFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

private val HourOnlyFormat = DateTimeFormatter.ofPattern("H 'h'")

private fun spokenHourOnly(epochMs: Long): String = HourOnlyFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

private const val DAY_MS = 24 * 60 * 60 * 1000L

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
