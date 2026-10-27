package io.github.touya0.nimbe.data.homeassistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySeriesTest {

    @Test
    fun `réponse de history_during_period`() {
        val result = JSONObject(
            """{"sensor.temp":[
                 {"s":"52.5","lu":1793096100.0},
                 {"s":"unavailable","lu":1793096130.0},
                 {"s":"53","lc":1793096160.5},
                 {"s":"54"}
               ]}""",
        )
        assertEquals(
            listOf(HistoryPoint(1_793_096_100_000L, 52.5), HistoryPoint(1_793_096_160_500L, 53.0)),
            HistorySeriesBuilder.parse(result, "sensor.temp"),
        )
        assertEquals(emptyList<HistoryPoint>(), HistorySeriesBuilder.parse(JSONObject("{}"), "sensor.temp"))
    }

    @Test
    fun `moyenne par tranche, maximum sur les valeurs brutes`() {
        val minute = 60_000L
        val raw = listOf(
            HistoryPoint(0, 50.0),
            HistoryPoint(1 * minute, 54.0),
            HistoryPoint(6 * minute, 58.0),
            HistoryPoint(7 * minute, 56.0),
            HistoryPoint(20 * minute, 99.0),
        )
        val series = HistorySeriesBuilder.build(raw, from = 0, to = 10 * minute, bucketMs = 5 * minute)
        assertEquals(listOf(HistoryPoint(150_000, 52.0), HistoryPoint(450_000, 57.0)), series.points)
        assertEquals(HistoryPoint(6 * minute, 58.0), series.max)
    }
}
