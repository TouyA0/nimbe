package io.github.touya0.nimbe.feature.homeassistant

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/** Réponse de l'écran au formulaire envoyé par le téléphone. */
sealed interface PairingReply {
    data class Accepted(val userName: String) : PairingReply

    /** [message] s'affiche sur la page ; [code] est le code à pré-remplir (vide si le code était faux). */
    data class Rejected(val message: String, val address: String, val code: String) : PairingReply
}

/**
 * Petit serveur web local, ouvert seulement tant que l'écran « Connecter Home Assistant » est affiché.
 * Une connexion à la fois : la page n'a qu'un utilisateur, et cela freine qui essaierait des codes.
 */
class PairingServer(
    private val suggestedAddress: () -> String,
    private val onSubmit: suspend (PairingForm) -> PairingReply,
) {
    private var socket: ServerSocket? = null
    private var job: Job? = null

    /** Ouvre le port et répond en tâche de fond. Renvoie le port, ou null si aucun n'est libre. */
    fun start(scope: CoroutineScope): Int? {
        val server = PORTS.firstNotNullOfOrNull { port ->
            try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
            } catch (_: IOException) {
                null
            }
        } ?: return null
        socket = server
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                client.use { runCatching { serve(it) } }
            }
        }
        return server.localPort
    }

    fun stop() {
        job?.cancel()
        runCatching { socket?.close() }
        socket = null
    }

    private suspend fun serve(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        val request = PairingHttp.read(client.getInputStream()) ?: return
        val (status, html) = when {
            request.path != "/" -> "404 Not Found" to PairingPage.notFound()
            request.method == "GET" -> "200 OK" to PairingPage.form(suggestedAddress())
            request.method == "POST" -> {
                val form = PairingHttp.form(request.body)
                // L'écran vérifie le token auprès de Home Assistant ; la page attend sa réponse.
                when (val reply = onSubmit(form)) {
                    is PairingReply.Accepted -> "200 OK" to PairingPage.accepted(reply.userName)
                    is PairingReply.Rejected -> "200 OK" to PairingPage.form(reply.address, reply.code, reply.message)
                }
            }
            else -> "405 Method Not Allowed" to PairingPage.notFound()
        }
        client.getOutputStream().apply {
            write(PairingHttp.response(status, html))
            flush()
        }
    }

    companion object {
        /** 8765 comme sur la maquette ; les suivants si une autre appli l'occupe. */
        private val PORTS = 8765..8769
        private const val READ_TIMEOUT_MS = 10_000

        /** Adresse IPv4 du stick sur le réseau de la maison, pour le QR code. null sans réseau. */
        fun localAddress(context: Context): String? {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val properties = manager.getLinkProperties(manager.activeNetwork) ?: return null
            return properties.linkAddresses
                .map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }
    }
}
