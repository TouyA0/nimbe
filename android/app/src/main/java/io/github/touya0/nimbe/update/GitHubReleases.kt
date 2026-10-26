package io.github.touya0.nimbe.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lecture des releases publiques du dépôt, sans jeton : 60 requêtes par heure suffisent largement.
 * Tout passe en HTTPS, par github.com et ses serveurs de fichiers.
 */
class GitHubReleases(private val userAgent: String) {

    /** La dernière version publiée pour ce canal, ou null si le dépôt n'en a aucune. */
    suspend fun latest(channel: UpdateChannel): UpdateManifest? = withContext(Dispatchers.IO) {
        val releases = Releases.parseList(get("https://api.github.com/repos/${Releases.REPO}/releases?per_page=15"))
        val release = Releases.pick(releases, channel) ?: return@withContext null
        val manifestUrl = release.assets.getValue(Releases.MANIFEST_ASSET)
        if (!Releases.isTrustedUrl(manifestUrl)) throw IOException("Adresse de téléchargement inattendue")
        val manifest = UpdateManifest.parse(get(manifestUrl), apkUrl = "")
        val apkUrl = release.assets[manifest.apkName]
            ?: throw IOException("La release ${release.tag} ne contient pas ${manifest.apkName}")
        if (!Releases.isTrustedUrl(apkUrl)) throw IOException("Adresse de téléchargement inattendue")
        manifest.copy(apkUrl = apkUrl)
    }

    /**
     * Télécharge [url] dans [target]. Si un morceau est déjà là (coupure, stick éteint),
     * on reprend où on en était. [onProgress] reçoit les octets reçus au total.
     */
    suspend fun download(url: String, target: File, expectedSize: Long, onProgress: (Long) -> Unit) =
        withContext(Dispatchers.IO) {
            var already = if (target.exists()) target.length() else 0L
            if (already > expectedSize) {
                target.delete()
                already = 0L
            }
            if (already == expectedSize) return@withContext

            val connection = open(url).apply {
                if (already > 0) setRequestProperty("Range", "bytes=$already-")
            }
            try {
                val code = connection.responseCode
                // 206 : le serveur reprend ; 200 : il renvoie tout, on repart de zéro.
                val append = code == HttpURLConnection.HTTP_PARTIAL && already > 0
                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                    throw IOException("GitHub a répondu $code")
                }
                var received = if (append) already else 0L
                FileOutputStream(target, append).use { out ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            received += read
                            if (received > expectedSize) throw IOException("Fichier plus gros qu'annoncé")
                            onProgress(received)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun get(url: String): String {
        val connection = open(url).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("GitHub a répondu $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Seul HTTPS est accepté" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            // Les fichiers des releases sont servis par une redirection vers un autre domaine GitHub, en HTTPS.
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
        }
    }
}
