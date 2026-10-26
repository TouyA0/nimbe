package io.github.touya0.nimbe.data.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Comment Nimbe se présente à Jellyfin : le serveur affiche ces valeurs dans ses appareils. */
data class ClientInfo(val deviceName: String, val deviceId: String, val version: String)

data class ServerInfo(val id: String, val name: String, val version: String)

/** Code à saisir sur le téléphone ; le secret reste sur la TV pour suivre la demande. */
data class QuickConnectRequest(val code: String, val secret: String)

/** De quoi faire une requête au nom de l'utilisateur connecté. */
data class JellyfinAuth(val server: String, val userId: String, val token: String)

/** Ce que renvoie une connexion réussie. Le jeton se révoque depuis Jellyfin (Tableau de bord → Appareils). */
data class JellyfinSession(val token: String, val userId: String, val userName: String)

class JellyfinException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind { Unreachable, NotJellyfin, QuickConnectDisabled, Expired, Unauthorized, Server }
}

/**
 * Client de l'API Jellyfin, sans bibliothèque : quelques requêtes JSON suffisent.
 * Le jeton passe toujours dans l'en-tête Authorization, jamais dans une adresse.
 * Ne jamais journaliser un jeton ni un secret Quick Connect.
 */
class JellyfinApi(private val client: suspend () -> ClientInfo) {

    /** Répond sans connexion : sert à vérifier qu'une adresse mène bien à un Jellyfin. */
    suspend fun publicInfo(server: String): ServerInfo {
        val json = call("GET", "$server/System/Info/Public", token = null)
        return try {
            ServerInfo(id = json.getString("Id"), name = json.optString("ServerName", "Jellyfin"), version = json.optString("Version"))
        } catch (_: JSONException) {
            throw notJellyfin()
        }
    }

    suspend fun initiateQuickConnect(server: String): QuickConnectRequest {
        val json = try {
            call("POST", "$server/QuickConnect/Initiate", token = null)
        } catch (e: JellyfinException) {
            // Jellyfin répond 401 quand Quick Connect est désactivé dans le tableau de bord.
            if (e.kind == JellyfinException.Kind.Unauthorized) throw quickConnectDisabled() else throw e
        }
        return QuickConnectRequest(code = json.getString("Code"), secret = json.getString("Secret"))
    }

    /** true quand le code a été autorisé sur le téléphone. Un code expiré lève [JellyfinException.Kind.Expired]. */
    suspend fun isQuickConnectAuthorized(server: String, secret: String): Boolean =
        call("GET", "$server/QuickConnect/Connect?secret=${URLEncoder.encode(secret, "UTF-8")}", token = null).optBoolean("Authenticated")

    suspend fun authenticateWithQuickConnect(server: String, secret: String): JellyfinSession {
        val body = JSONObject().put("Secret", secret).toString()
        val json = call("POST", "$server/Users/AuthenticateWithQuickConnect", token = null, body = body)
        val user = json.getJSONObject("User")
        return JellyfinSession(token = json.getString("AccessToken"), userId = user.getString("Id"), userName = user.getString("Name"))
    }

    /** Révoque le jeton côté serveur : la TV disparaît des appareils de l'utilisateur. */
    suspend fun logout(server: String, token: String) {
        call("POST", "$server/Sessions/Logout", token = token)
    }

    /**
     * Requête de lecture authentifiée ; renvoie un JSONObject ou un JSONArray selon la route.
     * Les paramètres null sont omis. Un jeton révoqué lève [JellyfinException.Kind.Unauthorized].
     */
    suspend fun get(auth: JellyfinAuth, path: String, params: Map<String, Any?> = emptyMap()): Any {
        val query = params.filterValues { it != null }.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value.toString(), "UTF-8")}"
        }
        val text = request("GET", "${auth.server}$path" + if (query.isEmpty()) "" else "?$query", auth.token)
        return try {
            JSONTokener(text).nextValue()
        } catch (_: JSONException) {
            throw notJellyfin()
        }
    }

    private suspend fun call(method: String, url: String, token: String?, body: String? = null): JSONObject {
        val text = request(method, url, token, body)
        return try {
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (_: JSONException) {
            throw notJellyfin()
        }
    }

    private suspend fun request(method: String, url: String, token: String?, body: String? = null): String {
        val header = authorization(client(), token)
        return withContext(Dispatchers.IO) {
            val connection = try {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 5_000
                    readTimeout = 15_000
                    instanceFollowRedirects = false
                    setRequestProperty("Authorization", header)
                    setRequestProperty("Accept", "application/json")
                }
            } catch (_: IOException) {
                throw unreachable()
            }
            try {
                if (body != null || method == "POST") {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write((body ?: "").toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                when {
                    code == HttpURLConnection.HTTP_NOT_FOUND && "/QuickConnect/" in url ->
                        throw JellyfinException(JellyfinException.Kind.Expired, "Le code a expiré")
                    code == HttpURLConnection.HTTP_UNAUTHORIZED ->
                        throw JellyfinException(JellyfinException.Kind.Unauthorized, "Accès refusé par Jellyfin")
                    code !in 200..299 -> throw JellyfinException(JellyfinException.Kind.Server, "Jellyfin a répondu $code")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: JellyfinException) {
                throw e
            } catch (_: IOException) {
                throw unreachable()
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        /**
         * En-tête attendu par Jellyfin. Les valeurs sont nettoyées : un guillemet ou une virgule
         * dans le nom de la TV casserait la lecture de l'en-tête par le serveur.
         */
        fun authorization(client: ClientInfo, token: String?): String {
            fun clean(value: String) = value.replace(Regex("""[^\p{L}\p{N} ._()-]"""), "").trim().ifEmpty { "TV" }
            val parts = mutableListOf(
                "Client=\"Nimbe\"",
                "Device=\"${clean(client.deviceName)}\"",
                "DeviceId=\"${clean(client.deviceId)}\"",
                "Version=\"${clean(client.version)}\"",
            )
            if (token != null) parts += "Token=\"$token\""
            return "MediaBrowser " + parts.joinToString(", ")
        }

        private fun unreachable() = JellyfinException(JellyfinException.Kind.Unreachable, "Serveur injoignable")
        private fun notJellyfin() = JellyfinException(JellyfinException.Kind.NotJellyfin, "Ce n'est pas un serveur Jellyfin")
        private fun quickConnectDisabled() = JellyfinException(
            JellyfinException.Kind.QuickConnectDisabled,
            "Quick Connect est désactivé : active-le dans Jellyfin, Tableau de bord → Général",
        )
    }
}
