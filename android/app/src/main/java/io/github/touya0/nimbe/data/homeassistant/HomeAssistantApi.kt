package io.github.touya0.nimbe.data.homeassistant

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Le compte dont le jeton a été donné à la TV. Un compte admin marche, mais on conseille un compte dédié. */
data class HomeAssistantUser(val name: String, val isAdmin: Boolean)

class HomeAssistantException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind { Unreachable, NotHomeAssistant, Unauthorized, Server }
}

/**
 * Client du WebSocket de Home Assistant (/api/websocket) : une connexion, l'authentification par jeton,
 * puis des commandes numérotées. Le jeton ne passe que dans le message d'authentification.
 * Ne jamais journaliser un jeton.
 */
class HomeAssistantApi(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build(),
) {

    /** Ouvre une connexion authentifiée, exécute [block], puis la ferme quoi qu'il arrive. */
    suspend fun <T> session(server: String, token: String, block: suspend HomeAssistantSession.() -> T): T {
        val session = connect(server, token)
        try {
            return session.block()
        } finally {
            session.close()
        }
    }

    suspend fun connect(server: String, token: String): HomeAssistantSession {
        val incoming = Channel<JSONObject>(Channel.UNLIMITED)
        val request = try {
            Request.Builder().url(websocketUrl(server)).build()
        } catch (_: IllegalArgumentException) {
            throw HomeAssistantException(HomeAssistantException.Kind.Unreachable, "Adresse invalide")
        }
        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = try {
                    JSONObject(text)
                } catch (_: JSONException) {
                    return
                }
                incoming.trySend(message)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Une réponse HTTP sans passage en WebSocket : quelque chose répond, mais pas Home Assistant.
                incoming.close(if (response != null) notHomeAssistant() else unreachable())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                incoming.close(unreachable())
            }
        })
        val session = HomeAssistantSession(socket, incoming)
        try {
            session.authenticate(token)
        } catch (e: Throwable) {
            session.close()
            throw e
        }
        return session
    }

    companion object {
        /** http://pi:8123 → ws://pi:8123/api/websocket (wss:// derrière un proxy en https). */
        fun websocketUrl(server: String): String {
            val base = server.trimEnd('/')
            val scheme = if (base.startsWith("https://")) "wss://" else "ws://"
            return scheme + base.substringAfter("://") + "/api/websocket"
        }

        /**
         * Identifiant du jeton longue durée côté Home Assistant : le champ « iss » du JWT.
         * Sert à révoquer ce jeton-là en se déconnectant. null si ce n'est pas un JWT lisible.
         */
        fun refreshTokenId(token: String): String? {
            val payload = token.split('.').takeIf { it.size == 3 }?.get(1) ?: return null
            return try {
                val json = JSONObject(String(Base64.getUrlDecoder().decode(payload.trimEnd('=')), Charsets.UTF_8))
                json.optString("iss").takeIf { it.isNotBlank() }
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: JSONException) {
                null
            }
        }

        /** Réponse de config/entity_registry/list_for_display : « ei » identifiant, « pl » intégration, « tk » clé. */
        fun parseRegistry(result: JSONObject): Map<String, RegistryInfo> {
            val list = result.optJSONArray("entities") ?: return emptyMap()
            return (0 until list.length()).mapNotNull { index ->
                val entry = list.optJSONObject(index) ?: return@mapNotNull null
                val id = entry.optString("ei").ifBlank { return@mapNotNull null }
                id to RegistryInfo(platform = entry.optString("pl"), translationKey = entry.optString("tk").ifBlank { null })
            }.toMap()
        }

        internal fun unreachable() = HomeAssistantException(HomeAssistantException.Kind.Unreachable, "Home Assistant injoignable")
        internal fun notHomeAssistant() =
            HomeAssistantException(HomeAssistantException.Kind.NotHomeAssistant, "Ce n'est pas un serveur Home Assistant")
    }
}

/** Une connexion ouverte et authentifiée. Les commandes sont envoyées une par une. */
class HomeAssistantSession internal constructor(
    private val socket: WebSocket,
    private val incoming: Channel<JSONObject>,
) {
    private var nextId = 1

    var version: String = ""
        private set

    internal suspend fun authenticate(token: String) {
        val hello = receive()
        if (hello.optString("type") != "auth_required") throw HomeAssistantApi.notHomeAssistant()
        version = hello.optString("ha_version")
        send(JSONObject().put("type", "auth").put("access_token", token))
        val answer = receive()
        when (answer.optString("type")) {
            "auth_ok" -> Unit
            "auth_invalid" -> throw HomeAssistantException(
                HomeAssistantException.Kind.Unauthorized,
                "Token refusé par Home Assistant",
            )
            else -> throw HomeAssistantApi.notHomeAssistant()
        }
    }

    /** Envoie une commande et attend son résultat (JSONObject, JSONArray ou null). */
    suspend fun command(type: String, fields: JSONObject = JSONObject()): Any? {
        val id = nextId++
        send(fields.put("id", id).put("type", type))
        while (true) {
            val message = receive()
            if (message.optInt("id") != id || message.optString("type") != "result") continue
            if (message.optBoolean("success")) return message.opt("result").takeUnless { it == JSONObject.NULL }
            val error = message.optJSONObject("error")?.optString("message").orEmpty()
            throw HomeAssistantException(HomeAssistantException.Kind.Server, error.ifBlank { "Home Assistant a refusé la demande" })
        }
    }

    suspend fun currentUser(): HomeAssistantUser {
        val user = command("auth/current_user") as? JSONObject ?: throw HomeAssistantApi.notHomeAssistant()
        return HomeAssistantUser(name = user.optString("name"), isAdmin = user.optBoolean("is_admin"))
    }

    suspend fun states(): List<HaEntity> {
        val list = command("get_states") as? JSONArray ?: throw HomeAssistantApi.notHomeAssistant()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let(HaEntity::fromState) }
    }

    /**
     * Intégration et clé de traduction de chaque entité active. Commande ouverte aux comptes non-admin
     * (l'interface de Home Assistant s'en sert pour tous) ; vide si ce Home Assistant ne la connaît pas.
     */
    suspend fun registry(): Map<String, RegistryInfo> {
        val result = try {
            command("config/entity_registry/list_for_display") as? JSONObject
        } catch (e: HomeAssistantException) {
            if (e.kind == HomeAssistantException.Kind.Server) null else throw e
        } ?: return emptyMap()
        return HomeAssistantApi.parseRegistry(result)
    }

    /** Les états, complétés par le registre : c'est ce que reconnaît [SensorCatalog]. */
    suspend fun entities(): List<HaEntity> {
        val registry = registry()
        return states().map { entity ->
            registry[entity.id]?.let { entity.copy(platform = it.platform, translationKey = it.translationKey) } ?: entity
        }
    }

    /**
     * S'abonne aux états des entités [ids] (format compressé de subscribe_entities).
     * Renvoie le numéro de l'abonnement, à passer à [nextEvent].
     */
    suspend fun subscribeEntities(ids: List<String>): Int {
        val id = nextId
        command("subscribe_entities", JSONObject().put("entity_ids", JSONArray(ids)))
        return id
    }

    /**
     * Attend le prochain événement de l'abonnement [subscription], sans limite de temps :
     * une connexion perdue est repérée par les pings d'OkHttp, qui ferment alors la session.
     */
    suspend fun nextEvent(subscription: Int): JSONObject {
        while (true) {
            val message = receive(timeoutMs = null)
            if (message.optInt("id") == subscription && message.optString("type") == "event") {
                return message.optJSONObject("event") ?: continue
            }
        }
    }

    /** Révoque un jeton longue durée de l'utilisateur connecté. */
    suspend fun deleteRefreshToken(id: String) {
        command("auth/delete_refresh_token", JSONObject().put("refresh_token_id", id))
    }

    fun close() {
        socket.close(1000, null)
        incoming.close()
    }

    private fun send(message: JSONObject) {
        if (!socket.send(message.toString())) throw HomeAssistantApi.unreachable()
    }

    private suspend fun receive(timeoutMs: Long? = TIMEOUT_MS): JSONObject = try {
        if (timeoutMs == null) incoming.receive() else withTimeout(timeoutMs) { incoming.receive() }
    } catch (_: TimeoutCancellationException) {
        throw HomeAssistantApi.unreachable()
    } catch (_: ClosedReceiveChannelException) {
        throw HomeAssistantApi.unreachable()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
