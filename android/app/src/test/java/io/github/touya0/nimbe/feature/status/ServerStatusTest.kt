package io.github.touya0.nimbe.feature.status

import io.github.touya0.nimbe.core.network.NetworkState
import io.github.touya0.nimbe.data.homeassistant.HaConnection
import io.github.touya0.nimbe.data.homeassistant.HaEntity
import io.github.touya0.nimbe.data.homeassistant.HaLive
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerStatusTest {

    private val cpu = HaEntity("sensor.cpu_use", "Processeur", "12", "%", null, 1L)
    private val jellyfinSensor = HaEntity("binary_sensor.jellyfin_joignable", "Jellyfin joignable", "on", null, "connectivity", 1L)
    private val sensors = listOf(cpu.id, jellyfinSensor.id)
    private val live = HaLive(HaConnection.Live(0), sensors, mapOf(cpu.id to cpu, jellyfinSensor.id to jellyfinSensor))
    private val lost = live.copy(connection = HaConnection.Lost(0, refused = false))

    private fun level(network: NetworkState = NetworkState.Online, jellyfinDown: Boolean = false, ha: HaLive = live) =
        ServerStatus.verdict(network, jellyfinDown, ha).level

    @Test
    fun `vert seulement en direct, avec toutes les valeurs`() {
        assertEquals(ServerLevel.Ok, level())
        assertEquals(ServerLevel.Unknown, level(ha = lost))
        assertEquals(ServerLevel.Unknown, level(ha = live.copy(entities = live.entities + (cpu.id to cpu.copy(state = "unavailable")))))
        assertEquals(ServerLevel.Unknown, level(ha = live.copy(entities = mapOf(cpu.id to cpu))))
        // Pas de Home Assistant, ou première connexion en cours : on ne dit rien plutôt que « OK ».
        assertEquals(ServerLevel.Hidden, level(ha = HaLive(HaConnection.Off)))
        assertEquals(ServerLevel.Hidden, level(ha = HaLive(HaConnection.Connecting, sensors)))
        assertEquals(ServerLevel.Hidden, level(ha = live.copy(entities = emptyMap())))
    }

    @Test
    fun `Jellyfin coupé, Pi allumé ou Pi coupé`() {
        val verdict = ServerStatus.verdict(NetworkState.Online, jellyfinDown = true, ha = live)
        assertEquals(ServerVerdict(ServerLevel.ServiceDown, listOf("Jellyfin")), verdict)
        assertEquals(ServerLevel.PiUnreachable, level(jellyfinDown = true, ha = lost))
        assertEquals(ServerLevel.PiUnreachable, level(jellyfinDown = true, ha = HaLive(HaConnection.Off)))
    }

    @Test
    fun `service hors ligne selon Home Assistant, sans doublon avec la vérification directe`() {
        val off = live.copy(entities = live.entities + (jellyfinSensor.id to jellyfinSensor.copy(state = "off")))
        assertEquals(ServerVerdict(ServerLevel.ServiceDown, listOf("Jellyfin")), ServerStatus.verdict(NetworkState.Online, false, off))
        assertEquals(ServerVerdict(ServerLevel.ServiceDown, listOf("Jellyfin")), ServerStatus.verdict(NetworkState.Online, true, off))
        // Connexion perdue : la dernière valeur « off » n'est plus une mesure, c'est « inconnu ».
        assertEquals(ServerLevel.Unknown, level(ha = off.copy(connection = HaConnection.Lost(0, refused = true))))
    }

    @Test
    fun `le réseau du stick passe avant tout`() {
        assertEquals(ServerLevel.NoNetwork, level(network = NetworkState.NoNetwork, jellyfinDown = true, ha = lost))
        assertEquals(ServerLevel.NoInternet, level(network = NetworkState.NoInternet))
        assertEquals(ServerLevel.ServiceDown, level(network = NetworkState.NoInternet, jellyfinDown = true))
    }

    @Test
    fun `seuils à surveiller`() {
        fun disk(value: String) = HaEntity("sensor.disk_use_percent", "Disque", value, "%", null, 1L)
        assertEquals(false, Watch.needed(disk("79.9")))
        assertEquals(true, Watch.needed(disk("81")))
        assertEquals(false, Watch.needed(disk("unavailable")))
        assertEquals(true, Watch.needed(HaEntity("sensor.processor_temperature", "T", "76", "°C", "temperature", 1L)))
        assertEquals(false, Watch.needed(jellyfinSensor))
    }
}
