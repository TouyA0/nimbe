package io.github.touya0.nimbe.data.homeassistant

import org.json.JSONObject

/**
 * Événements de subscribe_entities, au format compressé de Home Assistant :
 * « a » entités ajoutées (état complet), « c » changements (« + » valeurs nouvelles, « - » attributs retirés),
 * « r » entités retirées. Dans chaque état : « s » état, « a » attributs, « lc » / « lu » heures en secondes.
 */
object CompressedStates {

    fun apply(current: Map<String, HaEntity>, event: JSONObject): Map<String, HaEntity> {
        val next = current.toMutableMap()
        event.optJSONObject("a")?.let { added ->
            added.keys().forEach { id ->
                val state = added.optJSONObject(id) ?: return@forEach
                next[id] = HaEntity.of(id, state.optString("s"), state.optJSONObject("a") ?: JSONObject(), time(state))
            }
        }
        event.optJSONObject("c")?.let { changed ->
            changed.keys().forEach { id ->
                val base = next[id] ?: return@forEach
                val diff = changed.optJSONObject(id) ?: return@forEach
                var entity = base
                diff.optJSONObject("+")?.let { plus ->
                    val attributes = plus.optJSONObject("a")
                    entity = entity.copy(
                        state = if (plus.has("s")) plus.optString("s") else entity.state,
                        name = attributes?.optString("friendly_name")?.ifBlank { null } ?: entity.name,
                        unit = attributes?.optString("unit_of_measurement")?.ifBlank { null } ?: entity.unit,
                        deviceClass = attributes?.optString("device_class")?.ifBlank { null } ?: entity.deviceClass,
                        updatedAt = time(plus) ?: entity.updatedAt,
                    )
                }
                diff.optJSONObject("-")?.optJSONArray("a")?.let { removed ->
                    val keys = (0 until removed.length()).map { removed.optString(it) }.toSet()
                    if ("unit_of_measurement" in keys) entity = entity.copy(unit = null)
                    if ("device_class" in keys) entity = entity.copy(deviceClass = null)
                }
                next[id] = entity
            }
        }
        event.optJSONArray("r")?.let { removed -> (0 until removed.length()).forEach { next.remove(removed.optString(it)) } }
        return next
    }

    /** « lu » (dernière mise à jour) s'il est là, sinon « lc » (dernier changement), en ms. */
    private fun time(state: JSONObject): Long? {
        val seconds = state.optDouble("lu").takeUnless { it.isNaN() } ?: state.optDouble("lc").takeUnless { it.isNaN() } ?: return null
        return (seconds * 1000).toLong()
    }
}
