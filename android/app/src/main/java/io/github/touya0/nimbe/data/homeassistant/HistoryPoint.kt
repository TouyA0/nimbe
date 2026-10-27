package io.github.touya0.nimbe.data.homeassistant

import org.json.JSONObject

/** Une mesure passée : heure (ms) et valeur. */
data class HistoryPoint(val at: Long, val value: Double)

/** Une série prête à tracer : points moyennés, et la plus haute valeur avec son heure. */
data class HistorySeries(val points: List<HistoryPoint>, val max: HistoryPoint?)

/** Historique de Home Assistant : lecture et réduction à quelques centaines de points pour la courbe. */
object HistorySeriesBuilder {

    /**
     * Moyenne par tranche de [bucketMs] entre [from] et [to]. Un capteur mesuré toutes les 30 s donne près de
     * 3 000 valeurs par jour : 288 tranches de 5 minutes suffisent pour une courbe de 500 dp.
     * Le maximum est pris sur les valeurs brutes : c'est lui qu'on affiche (« max 58 °C · 16 h »).
     */
    fun build(raw: List<HistoryPoint>, from: Long, to: Long, bucketMs: Long = 5 * 60_000L): HistorySeries {
        val inRange = raw.filter { it.at in from..to }
        val points = inRange
            .groupBy { (it.at - from) / bucketMs }
            .toSortedMap()
            .map { (bucket, values) -> HistoryPoint(from + bucket * bucketMs + bucketMs / 2, values.map { it.value }.average()) }
        return HistorySeries(points, inRange.maxByOrNull { it.value })
    }

    /** Réponse de history/history_during_period : par entité, des états compressés (« s » valeur, « lu » / « lc » heure). */
    fun parse(result: JSONObject, entityId: String): List<HistoryPoint> {
        val list = result.optJSONArray(entityId) ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val entry = list.optJSONObject(index) ?: return@mapNotNull null
            val value = entry.optString("s").toDoubleOrNull() ?: return@mapNotNull null
            val seconds = entry.optDouble("lu").takeUnless { it.isNaN() } ?: entry.optDouble("lc").takeUnless { it.isNaN() } ?: return@mapNotNull null
            HistoryPoint((seconds * 1000).toLong(), value)
        }.sortedBy { it.at }
    }
}

