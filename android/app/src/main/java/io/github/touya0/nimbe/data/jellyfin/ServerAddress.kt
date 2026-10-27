package io.github.touya0.nimbe.data.jellyfin

import java.net.URI
import java.net.URISyntaxException

/**
 * Adresse d'un serveur de la maison (Jellyfin, Home Assistant), telle que la saisit l'utilisateur :
 * « 192.168.1.20 », « pi5.lan:8097 » ou « https://films.example.org/jellyfin ».
 *
 * Le HTTP sans chiffrement n'est accepté que vers le réseau de la maison (adresses privées,
 * noms en .lan ou .local) : un jeton ne doit jamais traverser internet en clair.
 */
object ServerAddress {

    const val DEFAULT_PORT = 8096

    /**
     * Adresse normalisée (schéma, hôte, port, chemin sans barre finale), ou une explication en français.
     * [defaultPort] : port ajouté à une adresse en http:// qui n'en a pas (8096 pour Jellyfin, 8123 pour Home Assistant).
     */
    fun parse(input: String, defaultPort: Int = DEFAULT_PORT): Result<String> {
        val text = input.trim()
        if (text.isEmpty()) return failure("Saisis l'adresse du serveur")
        val withScheme = if ("://" in text) text else "http://$text"
        val uri = try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return failure("Adresse invalide")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return failure("L'adresse doit commencer par http:// ou https://")
        val host = uri.host ?: return failure("Adresse invalide")
        if (scheme == "http" && !isLocalHost(host)) return failure("Hors de la maison, utilise une adresse en https://")

        // En http, le port du service ; en https, c'est un proxy (Caddy) sur le port standard.
        val port = when {
            uri.port != -1 -> ":${uri.port}"
            scheme == "http" -> ":$defaultPort"
            else -> ""
        }
        val path = uri.rawPath.orEmpty().trimEnd('/')
        return Result.success("$scheme://$host$port$path")
    }

    /** Hôte du réseau local, d'après son écriture seulement (sans requête DNS). */
    fun isLocalHost(host: String): Boolean {
        val name = host.lowercase().removePrefix("[").removeSuffix("]")
        ipv4(name)?.let { (a, b) ->
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
        }
        if (':' in name) return name == "::1" || name.startsWith("fe80:") || name.startsWith("fc") || name.startsWith("fd")
        // Un nom sans point (« jellyfin », « pi5 ») ne peut être résolu que sur le réseau local.
        return '.' !in name || LOCAL_SUFFIXES.any { name.endsWith(it) }
    }

    /** Deux premiers octets d'une adresse IPv4 écrite en chiffres, sinon null. */
    private fun ipv4(name: String): Pair<Int, Int>? {
        val parts = name.split('.')
        if (parts.size != 4) return null
        val bytes = parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 && part.all(Char::isDigit) } ?: return null }
        return bytes[0] to bytes[1]
    }

    private fun failure(message: String) = Result.failure<String>(IllegalArgumentException(message))

    private val LOCAL_SUFFIXES = listOf(".lan", ".local", ".home.arpa", ".internal", ".localhost", ".home")
}
