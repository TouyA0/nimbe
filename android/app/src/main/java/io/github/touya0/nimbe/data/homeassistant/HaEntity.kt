package io.github.touya0.nimbe.data.homeassistant

import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.roundToLong

/** Un état d'entité Home Assistant, réduit à ce qu'affiche la TV. */
data class HaEntity(
    val id: String,
    val name: String,
    val state: String,
    val unit: String?,
    val deviceClass: String?,
    /** Dernière mesure (last_updated), en ms ; null si Home Assistant ne l'a pas donnée. */
    val updatedAt: Long?,
    /** Intégration qui fournit l'entité (« systemmonitor »), lue dans le registre des entités. */
    val platform: String? = null,
    /** Clé de traduction de l'intégration (« processor_use ») : la même quelle que soit la langue. */
    val translationKey: String? = null,
    /** Attribut « temperature » : celle annoncée par une entité météo. */
    val temperature: Double? = null,
    /** Le message porté par le capteur [NOTICE_ENTITY], s'il en a un. */
    val notice: HaNotice? = null,
) {
    val domain: String get() = id.substringBefore('.')

    /** Nom à afficher, sans le nom de l'appareil répété devant (« System Monitor Utilisation du processeur »). */
    val label: String
        get() = name.removePrefix("System Monitor ").replaceFirstChar { it.uppercase() }

    /** Nom du service d'un capteur de connectivité : « Jellyfin joignable » → « Jellyfin ». */
    val serviceName: String
        get() = label.removeSuffix(" joignable").removeSuffix(" en ligne")

    /** La mesure ne dit rien (Home Assistant ne sait pas, ou l'intégration est arrêtée). */
    val unknown: Boolean get() = state in UNKNOWN_STATES

    /** Valeur lisible : « 23 % », « 52 °C », « En ligne ». Une valeur absente n'est jamais présentée comme bonne. */
    val displayValue: String
        get() {
            if (state in UNKNOWN_STATES) return "Inconnu"
            if (domain == "binary_sensor" && deviceClass == "connectivity") return if (state == "on") "En ligne" else "Hors ligne"
            val number = state.toDoubleOrNull() ?: return listOfNotNull(state, unit).joinToString(" ")
            val rounded = if (unit == "%" || unit == "°C") number.roundToLong().toString() else state
            return if (unit.isNullOrBlank()) rounded else "$rounded $unit"
        }

    companion object {
        val UNKNOWN_STATES = setOf("unavailable", "unknown", "")

        /**
         * Capteur qui transmet les événements nimbe_notify (voir deploy/homeassistant) : le compte de la TV
         * n'est pas administrateur, et Home Assistant ne lui laisse pas écouter les événements eux-mêmes.
         */
        const val NOTICE_ENTITY = "sensor.nimbe_notification"

        fun fromState(json: JSONObject): HaEntity? {
            val id = json.optString("entity_id").takeIf { '.' in it } ?: return null
            return of(
                id = id,
                state = json.optString("state"),
                attributes = json.optJSONObject("attributes") ?: JSONObject(),
                updatedAt = json.optString("last_updated").ifBlank { null }?.let(::parseTime),
            )
        }

        fun of(id: String, state: String, attributes: JSONObject, updatedAt: Long?) = HaEntity(
            id = id,
            name = attributes.optString("friendly_name").ifBlank { id.substringAfter('.') },
            state = state,
            unit = attributes.optString("unit_of_measurement").ifBlank { null },
            deviceClass = attributes.optString("device_class").ifBlank { null },
            updatedAt = updatedAt,
            temperature = attributes.optDouble("temperature").takeUnless { it.isNaN() },
            notice = HaNotice.of(attributes),
        )

        private fun parseTime(text: String): Long? = try {
            OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/** Message d'une automatisation Home Assistant : titre, détail, niveau (critique, normal, discret). */
data class HaNotice(val title: String, val detail: String?, val level: String) {
    companion object {
        fun of(attributes: JSONObject): HaNotice? {
            val title = attributes.optString("title").ifBlank { return null }
            return HaNotice(title, attributes.optString("detail").ifBlank { null }, attributes.optString("level").lowercase())
        }
    }
}

/** Ce que mesure un capteur suivi par la TV. L'ordre est celui de l'affichage. */
enum class SensorKind { Processor, Temperature, Memory, Disk, Service }

/** Ce que le registre des entités dit d'une entité : son intégration et sa clé de traduction. */
data class RegistryInfo(val platform: String, val translationKey: String?)

/**
 * Repère, parmi toutes les entités de Home Assistant, celles qui disent si le Pi va bien :
 * celles de System Monitor (processeur, température, mémoire, disque) et les capteurs de connectivité
 * (un par service).
 *
 * D'abord par le registre : intégration « systemmonitor » et clé de traduction, qui ne dépendent pas
 * de la langue (un Home Assistant en français nomme ses entités « …_utilisation_du_processeur »).
 * Sinon, par la fin du nom anglais. Les noms sont stricts : d'autres intégrations (Docker, par exemple)
 * ont des dizaines de capteurs « cpu » ou « memory_percent ».
 */
object SensorCatalog {

    private val PROCESSOR = Regex("""(^|_)(processor_use|cpu_use|cpu_usage)(_percent)?$""")
    private val TEMPERATURE = Regex("""(^|_)(processor|cpu)_temperature$""")
    private val MEMORY = Regex("""(^|_)memory_(use_percent|usage)$""")
    private val DISK = Regex("""(^|_)disk_(use_percent|usage)(_.+)?$""")

    private val SYSTEM_MONITOR_KEYS = mapOf(
        "processor_use" to SensorKind.Processor,
        "processor_temperature" to SensorKind.Temperature,
        "memory_use_percent" to SensorKind.Memory,
        "disk_use_percent" to SensorKind.Disk,
    )

    fun kindOf(entity: HaEntity): SensorKind? {
        if (entity.platform == "systemmonitor") return SYSTEM_MONITOR_KEYS[entity.translationKey]
        val name = entity.id.substringAfter('.').lowercase()
        return when (entity.domain) {
            "binary_sensor" -> SensorKind.Service.takeIf { entity.deviceClass == "connectivity" }
            "sensor" -> when {
                TEMPERATURE.containsMatchIn(name) && entity.unit == "°C" -> SensorKind.Temperature
                entity.unit != "%" -> null
                PROCESSOR.containsMatchIn(name) -> SensorKind.Processor
                MEMORY.containsMatchIn(name) -> SensorKind.Memory
                DISK.containsMatchIn(name) -> SensorKind.Disk
                else -> null
            }
            else -> null
        }
    }

    /** Les capteurs proposés à l'écran, dans l'ordre d'affichage. */
    fun candidates(entities: List<HaEntity>): List<HaEntity> =
        entities
            .mapNotNull { entity -> kindOf(entity)?.let { it to entity } }
            .sortedWith(compareBy({ it.first.ordinal }, { it.second.name.lowercase() }))
            .map { it.second }
}
