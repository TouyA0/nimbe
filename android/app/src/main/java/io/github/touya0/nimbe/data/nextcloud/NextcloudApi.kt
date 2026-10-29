package io.github.touya0.nimbe.data.nextcloud

import io.github.touya0.nimbe.data.jellyfin.ServerAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit

data class NextcloudStatus(val version: String, val productName: String)

/** Connexion en attente : [loginUrl] s'ouvre sur le téléphone (QR code), [pollToken] reste sur la TV. */
data class LoginFlow(val loginUrl: String, val pollEndpoint: String, val pollToken: String)

/**
 * De quoi faire une requête au nom d'un compte. [appPassword] est un mot de passe d'application :
 * révocable dans Nextcloud (Paramètres → Sécurité → Appareils et sessions), jamais le vrai mot de passe.
 */
data class NextcloudAuth(val server: String, val loginName: String, val appPassword: String) {
    /** Hors du toString généré : un mot de passe ne doit jamais finir dans un message d'erreur. */
    override fun toString() = "NextcloudAuth(server=$server, loginName=$loginName)"
}

/** [id] sert dans les chemins WebDAV ; il peut différer du nom de connexion (une adresse e-mail, par exemple). */
data class NextcloudUser(val id: String, val displayName: String)

class NextcloudException(val kind: Kind, message: String) : IOException(message) {
    /** [NotFound] : 404, aussi la réponse normale d'une connexion pas encore autorisée. */
    enum class Kind { Unreachable, NotNextcloud, NotFound, Maintenance, Unauthorized, Server }
}

/**
 * Client Nextcloud, sans bibliothèque : connexion par Login Flow v2, compte, révocation.
 * Le mot de passe d'application passe dans l'en-tête Authorization, jamais dans une adresse.
 * Ne jamais journaliser un mot de passe d'application ni un jeton de connexion.
 *
 * [userAgent] : Nextcloud en tire le nom de l'appareil affiché dans les sessions (« Nimbe (Salon) »).
 */
class NextcloudApi(private val userAgent: () -> String) {

    /** Répond sans connexion : sert à vérifier qu'une adresse mène bien à un Nextcloud. */
    /** Répond-il vite ? Essai court (1,5 s), pour choisir entre l'adresse de la maison et l'adresse publique. */
    suspend fun answers(server: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("$server/status.php").openConnection() as HttpURLConnection).apply {
                connectTimeout = 1_500
                readTimeout = 1_500
                instanceFollowRedirects = false
            }
            try {
                connection.responseCode == HttpURLConnection.HTTP_OK
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    suspend fun status(server: String): NextcloudStatus {
        val json = call("GET", "$server/status.php")
        if (!json.optBoolean("installed")) throw notNextcloud()
        if (json.optBoolean("maintenance")) {
            throw NextcloudException(NextcloudException.Kind.Maintenance, "Nextcloud est en maintenance, réessaie dans un moment")
        }
        return NextcloudStatus(json.optString("versionstring"), json.optString("productname", "Nextcloud"))
    }

    suspend fun startLogin(server: String): LoginFlow {
        val json = call("POST", "$server/index.php/login/v2")
        return try {
            val poll = json.getJSONObject("poll")
            LoginFlow(
                loginUrl = safeUrl(json.getString("login")),
                pollEndpoint = safeUrl(poll.getString("endpoint")),
                pollToken = poll.getString("token"),
            )
        } catch (_: JSONException) {
            throw notNextcloud()
        }
    }

    /** null tant que la connexion n'a pas été autorisée sur le téléphone. Le serveur oublie la demande après 20 minutes. */
    suspend fun pollLogin(server: String, flow: LoginFlow): NextcloudAuth? {
        val body = "token=" + URLEncoder.encode(flow.pollToken, "UTF-8")
        val json = try {
            call("POST", flow.pollEndpoint, form = body)
        } catch (e: NextcloudException) {
            // 404 : pas encore autorisée (ou demande expirée, que l'appelant compte lui-même).
            if (e.kind == NextcloudException.Kind.NotFound) return null else throw e
        }
        return try {
            // On garde l'adresse saisie, vérifiée : celle que renvoie le serveur dépend de sa configuration.
            NextcloudAuth(server, json.getString("loginName"), json.getString("appPassword"))
        } catch (_: JSONException) {
            throw notNextcloud()
        }
    }

    suspend fun user(auth: NextcloudAuth): NextcloudUser {
        val json = call("GET", "${auth.server}/ocs/v2.php/cloud/user?format=json", auth)
        return try {
            val data = json.getJSONObject("ocs").getJSONObject("data")
            val id = data.getString("id")
            NextcloudUser(id, data.optString("display-name").ifBlank { data.optString("displayname").ifBlank { id } })
        } catch (_: JSONException) {
            throw notNextcloud()
        }
    }

    /**
     * Une page de photos et vidéos du compte, des plus récemment modifiées aux plus anciennes.
     * Rend aussi le nombre de réponses lues (écartées comprises) : moins que [limit], c'était la dernière page.
     * WebDAV SEARCH passe par OkHttp : HttpURLConnection refuse les méthodes qu'il ne connaît pas.
     */
    suspend fun searchMedia(auth: NextcloudAuth, userId: String, folder: String?, offset: Int, limit: Int): Pair<List<GalleryFile>, Int> {
        var read = 0
        val files = dav(auth, "SEARCH", "${auth.server}/remote.php/dav/", WebDav.searchBody(userId, folder, offset, limit)) {
            WebDav.parseSearch(it, count = { count -> read = count })
        }
        return files to read
    }

    /** Sous-dossiers de [folder] (null : racine du compte), pour choisir ce que montre la galerie. */
    suspend fun folders(auth: NextcloudAuth, userId: String, folder: String?): List<String> =
        dav(auth, "PROPFIND", "${auth.server}/remote.php/dav${WebDav.filesPath(userId, folder)}/", WebDav.FOLDERS_BODY, depth = "1") {
            WebDav.parseFolders(it, folder)
        }

    /**
     * Albums de l'appli Photos du compte ([shared] : ceux que d'autres lui ont partagés).
     * Sans l'appli Photos sur le serveur, l'adresse n'existe pas : simplement aucun album.
     */
    suspend fun albums(auth: NextcloudAuth, userId: String, shared: Boolean): List<NextcloudAlbum> = try {
        val folder = if (shared) "sharedalbums" else "albums"
        dav(auth, "PROPFIND", "${auth.server}/remote.php/dav/photos/${WebDav.encodeSegment(userId)}/$folder/", WebDav.ALBUMS_BODY, depth = "1") {
            WebDav.parseAlbums(it, shared)
        }
    } catch (e: NextcloudException) {
        if (e.kind == NextcloudException.Kind.NotFound) emptyList() else throw e
    }

    /** Photos et vidéos d'un album. */
    suspend fun albumItems(auth: NextcloudAuth, album: NextcloudAlbum): List<GalleryFile> =
        dav(auth, "PROPFIND", davUrl(auth.server, album.davPath) + "/", WebDav.ALBUM_ITEMS_BODY, depth = "1") {
            WebDav.parseSearch(it, pathOf = WebDav::albumItemPath)
        }

    /** Requête WebDAV dont la réponse XML est lue au fil de l'eau par [parse]. */
    private suspend fun <T> dav(
        auth: NextcloudAuth,
        method: String,
        url: String,
        body: String,
        depth: String? = null,
        parse: (java.io.InputStream) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .method(method, body.toRequestBody(XML))
                .header("Authorization", basic(auth))
                .header("User-Agent", userAgent())
                .apply { depth?.let { header("Depth", it) } }
                .build()
            val response = try {
                http.newCall(request).execute()
            } catch (_: IOException) {
                throw unreachable()
            }
            response.use {
                when (it.code) {
                    in 200..299 -> Unit
                    HttpURLConnection.HTTP_UNAUTHORIZED ->
                        throw NextcloudException(NextcloudException.Kind.Unauthorized, "Accès refusé par Nextcloud")
                    HttpURLConnection.HTTP_UNAVAILABLE ->
                        throw NextcloudException(NextcloudException.Kind.Maintenance, "Nextcloud est en maintenance, réessaie dans un moment")
                    HttpURLConnection.HTTP_NOT_FOUND ->
                        throw NextcloudException(NextcloudException.Kind.NotFound, "Ce dossier n'existe plus sur Nextcloud")
                    else -> throw NextcloudException(NextcloudException.Kind.Server, "Nextcloud a répondu ${it.code}")
                }
                try {
                    parse(it.body!!.byteStream())
                } catch (_: IOException) {
                    throw unreachable()
                } catch (_: Exception) {
                    // SAXException, réponse coupée ou qui n'est pas du WebDAV.
                    throw notNextcloud()
                }
            }
        }

    /** Supprime le mot de passe d'application sur le serveur : la TV disparaît des appareils du compte. */
    suspend fun revoke(auth: NextcloudAuth) {
        call("DELETE", "${auth.server}/ocs/v2.php/core/apppassword?format=json", auth)
    }

    private suspend fun call(method: String, url: String, auth: NextcloudAuth? = null, form: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = try {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 5_000
                    readTimeout = 15_000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", userAgent())
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("OCS-APIRequest", "true")
                    auth?.let { setRequestProperty("Authorization", basic(it)) }
                }
            } catch (_: IOException) {
                throw unreachable()
            }
            try {
                if (form != null || method == "POST") {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    connection.outputStream.use { it.write((form ?: "").toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                when {
                    code == HttpURLConnection.HTTP_UNAUTHORIZED ->
                        throw NextcloudException(NextcloudException.Kind.Unauthorized, "Accès refusé par Nextcloud")
                    code == HttpURLConnection.HTTP_NOT_FOUND ->
                        throw NextcloudException(NextcloudException.Kind.NotFound, "Ce n'est pas un serveur Nextcloud")
                    code == HttpURLConnection.HTTP_UNAVAILABLE ->
                        throw NextcloudException(NextcloudException.Kind.Maintenance, "Nextcloud est en maintenance, réessaie dans un moment")
                    code !in 200..299 -> throw NextcloudException(NextcloudException.Kind.Server, "Nextcloud a répondu $code")
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (e: NextcloudException) {
                throw e
            } catch (_: JSONException) {
                throw notNextcloud()
            } catch (_: IOException) {
                throw unreachable()
            } finally {
                connection.disconnect()
            }
        }

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    companion object {
        private val XML = "application/xml; charset=utf-8".toMediaType()

        /** Aperçu d'un fichier calculé par le serveur ; [crop] : rempli et recadré à [width]×[height], sinon contenu dedans. */
        fun previewUrl(server: String, fileId: Long, width: Int, height: Int, crop: Boolean): String =
            "$server/index.php/core/preview?fileId=$fileId&x=$width&y=$height&a=${if (crop) 0 else 1}&forceIcon=0"

        /** Adresse WebDAV du fichier d'origine, pour la lecture des vidéos (le mot de passe part en en-tête). */
        fun fileUrl(server: String, userId: String, path: String): String =
            if (path.startsWith(WebDav.DAV_PREFIX)) davUrl(server, path.removePrefix(WebDav.DAV_PREFIX))
            else "$server/remote.php/dav${WebDav.filesPath(userId, path)}"

        /** « photos/quentin/albums/Bretagne 2025 » → adresse complète, chaque morceau encodé. */
        fun davUrl(server: String, davPath: String): String =
            "$server/remote.php/dav/" + davPath.split('/').filter { it.isNotEmpty() }.joinToString("/") { WebDav.encodeSegment(it) }

        fun basic(auth: NextcloudAuth): String =
            "Basic " + Base64.getEncoder().encodeToString("${auth.loginName}:${auth.appPassword}".toByteArray(Charsets.UTF_8))

        /**
         * Adresse renvoyée par le serveur : même règle que pour une adresse saisie, le HTTP sans
         * chiffrement seulement vers la maison. Un mot de passe ne doit pas traverser internet en clair.
         */
        internal fun safeUrl(url: String): String {
            val uri = try {
                URI(url)
            } catch (_: Exception) {
                throw notNextcloud()
            }
            val host = uri.host ?: throw notNextcloud()
            return when (uri.scheme?.lowercase()) {
                "https" -> url
                "http" -> if (ServerAddress.isLocalHost(host)) url else throw insecure()
                else -> throw notNextcloud()
            }
        }

        private fun unreachable() = NextcloudException(NextcloudException.Kind.Unreachable, "Serveur injoignable")
        private fun notNextcloud() = NextcloudException(NextcloudException.Kind.NotNextcloud, "Ce n'est pas un serveur Nextcloud")
        private fun insecure() = NextcloudException(
            NextcloudException.Kind.Server,
            "Nextcloud propose une adresse en http:// hors de la maison : règle overwriteprotocol sur https",
        )
    }
}
