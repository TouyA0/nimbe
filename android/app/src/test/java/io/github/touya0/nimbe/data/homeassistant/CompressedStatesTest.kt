package io.github.touya0.nimbe.data.homeassistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompressedStatesTest {

    @Test
    fun `état initial, changements et retraits`() {
        val added = JSONObject(
            """{"a":{
                 "sensor.cpu":{"s":"12","a":{"friendly_name":"Processeur","unit_of_measurement":"%"},"c":"x","lc":1793096100.5},
                 "binary_sensor.jellyfin":{"s":"on","a":{"friendly_name":"Jellyfin joignable","device_class":"connectivity"},"lc":1793096000,"lu":1793096050}
               }}""",
        )
        var states = CompressedStates.apply(emptyMap(), added)
        assertEquals("12", states.getValue("sensor.cpu").state)
        assertEquals(1_793_096_100_500L, states.getValue("sensor.cpu").updatedAt)
        assertEquals(1_793_096_050_000L, states.getValue("binary_sensor.jellyfin").updatedAt)
        assertEquals("Jellyfin", states.getValue("binary_sensor.jellyfin").serviceName)

        states = CompressedStates.apply(states, JSONObject("""{"c":{"binary_sensor.jellyfin":{"+":{"s":"off","lc":1793096200}}}}"""))
        val jellyfin = states.getValue("binary_sensor.jellyfin")
        assertEquals("off", jellyfin.state)
        assertEquals("connectivity", jellyfin.deviceClass)
        assertEquals(1_793_096_200_000L, jellyfin.updatedAt)

        states = CompressedStates.apply(states, JSONObject("""{"c":{"sensor.cpu":{"+":{"a":{"friendly_name":"CPU"}},"-":{"a":["unit_of_measurement"]}}}}"""))
        assertEquals("CPU", states.getValue("sensor.cpu").name)
        assertEquals("12", states.getValue("sensor.cpu").state)
        assertNull(states.getValue("sensor.cpu").unit)

        // Un changement pour une entité inconnue est ignoré ; un retrait l'enlève.
        states = CompressedStates.apply(states, JSONObject("""{"c":{"sensor.autre":{"+":{"s":"1"}}},"r":["sensor.cpu"]}"""))
        assertEquals(setOf("binary_sensor.jellyfin"), states.keys)
    }
}
