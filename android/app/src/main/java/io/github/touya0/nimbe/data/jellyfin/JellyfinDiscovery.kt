package io.github.touya0.nimbe.data.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException

/**
 * Serveur qui a répondu à la découverte. [addresses] sont à essayer dans l'ordre : celle qu'il annonce,
 * puis celle d'où vient sa réponse (dans Docker, il annonce souvent l'adresse interne du conteneur).
 */
data class DiscoveredServer(val id: String, val name: String, val addresses: List<String>)

/** Découverte des serveurs Jellyfin du réseau : un message en broadcast UDP, port 7359. */
class JellyfinDiscovery {

    suspend fun find(timeoutMs: Int = 2_500): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, DiscoveredServer>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMs
                val message = QUESTION.toByteArray(Charsets.US_ASCII)
                socket.send(DatagramPacket(message, message.size, InetAddress.getByName("255.255.255.255"), PORT))
                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(4096)
                while (true) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) break
                    socket.soTimeout = remaining.toInt()
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    val reply = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    parseReply(reply, packet.address.hostAddress)?.let { found.putIfAbsent(it.id, it) }
                }
            }
        } catch (_: IOException) {
            // Pas de réseau, ou broadcast refusé : on passera à la saisie de l'adresse.
        }
        found.values.toList()
    }

    companion object {
        const val PORT = 7359
        const val QUESTION = "who is JellyfinServer?"

        /** Réponse attendue : {"Address":"http://192.168.1.20:8096","Id":"…","Name":"…"}. */
        fun parseReply(reply: String, senderIp: String?): DiscoveredServer? {
            val json = try {
                JSONObject(reply)
            } catch (_: JSONException) {
                return null
            }
            val id = json.optString("Id").ifEmpty { return null }
            val announced = json.optString("Address")
            val addresses = buildList {
                ServerAddress.parse(announced).getOrNull()?.let(::add)
                // Même port et même chemin, mais à l'adresse d'où vient la réponse.
                if (senderIp != null) {
                    val port = try {
                        URI(announced).port.takeIf { it != -1 }
                    } catch (_: URISyntaxException) {
                        null
                    } ?: ServerAddress.DEFAULT_PORT
                    val host = if (':' in senderIp) "[$senderIp]" else senderIp
                    ServerAddress.parse("http://$host:$port").getOrNull()?.let(::add)
                }
            }.distinct()
            if (addresses.isEmpty()) return null
            return DiscoveredServer(id = id, name = json.optString("Name", "Jellyfin"), addresses = addresses)
        }
    }
}
