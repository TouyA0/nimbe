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
 * (Ping, un par service). Les noms d'entités de System Monitor ont changé selon les versions
 * (« processor_use », « system_monitor_processor_use »…) : on reconnaît des morceaux de nom, avec l'unité.
 */
object SensorCatalog {

    fun kindOf(entity: HaEntity): SensorKind? {
        val name = entity.id.substringAfter('.').lowercase()
        return when (entity.domain) {
            "binary_sensor" -> SensorKind.Service.takeIf { entity.deviceClass == "connectivity" }
            "sensor" -> when {
                (entity.deviceClass == "temperature" || entity.unit == "°C") && ("processor" in name || "cpu" in name) ->
                    SensorKind.Temperature
                entity.unit != "%" -> null
                "processor" in name || "cpu" in name -> SensorKind.Processor
                "memory" in name -> SensorKind.Memory
                "disk" in name -> SensorKind.Disk
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
