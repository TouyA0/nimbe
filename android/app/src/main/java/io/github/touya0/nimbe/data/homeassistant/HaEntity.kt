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
) {
    val domain: String get() = id.substringBefore('.')

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

        fun fromState(json: JSONObject): HaEntity? {
            val id = json.optString("entity_id").takeIf { '.' in it } ?: return null
            val attributes = json.optJSONObject("attributes") ?: JSONObject()
            return HaEntity(
                id = id,
                name = attributes.optString("friendly_name").ifBlank { id.substringAfter('.') },
                state = json.optString("state"),
                unit = attributes.optString("unit_of_measurement").ifBlank { null },
                deviceClass = attributes.optString("device_class").ifBlank { null },
                updatedAt = json.optString("last_updated").ifBlank { null }?.let(::parseTime),
            )
        }

        private fun parseTime(text: String): Long? = try {
            OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/** Ce que mesure un capteur suivi par la TV. L'ordre est celui de l'affichage. */
enum class SensorKind { Processor, Temperature, Memory, Disk, Service }

/**
 * Repère, parmi toutes les entités de Home Assistant, celles qui disent si le Pi va bien :
 * celles de System Monitor (processeur, température, mémoire, disque) et les capteurs de connectivité
 * (un par service). Les noms de System Monitor ont changé selon les versions (« processor_use »,
 * « system_monitor_processor_use »…) : on reconnaît la fin du nom, avec l'unité. Les noms sont stricts :
 * d'autres intégrations (Docker, par exemple) ont des dizaines de capteurs « cpu » ou « memory_percent ».
 */
object SensorCatalog {

    private val PROCESSOR = Regex("""(^|_)(processor_use|cpu_use|cpu_usage)(_percent)?$""")
    private val TEMPERATURE = Regex("""(^|_)(processor|cpu)_temperature$""")
    private val MEMORY = Regex("""(^|_)memory_(use_percent|usage)$""")
    private val DISK = Regex("""(^|_)disk_(use_percent|usage)(_.+)?$""")

    fun kindOf(entity: HaEntity): SensorKind? {
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
