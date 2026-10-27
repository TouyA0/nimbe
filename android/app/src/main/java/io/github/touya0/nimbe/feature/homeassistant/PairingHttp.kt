package io.github.touya0.nimbe.feature.homeassistant

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.security.SecureRandom

/** Une requête HTTP reçue par la page de configuration, réduite au strict nécessaire. */
data class HttpRequest(val method: String, val path: String, val body: String)

/** Ce que le téléphone envoie : le code affiché sur la TV, l'adresse de Home Assistant et le token. */
data class PairingForm(val code: String, val address: String, val token: String)

/**
 * Lecture minimale du HTTP/1.1 : ligne de requête, en-têtes, corps de taille annoncée.
 * Les tailles sont bornées : la page n'attend qu'un petit formulaire.
 */
object PairingHttp {

    private const val MAX_HEADER_BYTES = 8 * 1024
    const val MAX_BODY_BYTES = 16 * 1024

    /** null si la requête est mal formée ou trop grosse. */
    fun read(input: InputStream): HttpRequest? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val parts = lines.first().split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/")) return null
        val length = lines.drop(1)
            .firstOrNull { it.substringBefore(':').trim().equals("Content-Length", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        if (length !in 0..MAX_BODY_BYTES) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        return HttpRequest(method = parts[0], path = parts[1].substringBefore('?'), body = String(body, Charsets.UTF_8))
    }

    /** Corps application/x-www-form-urlencoded. */
    fun form(body: String): PairingForm {
        val fields = body.split('&').filter { '=' in it }.associate { pair ->
            decode(pair.substringBefore('=')) to decode(pair.substringAfter('='))
        }
        return PairingForm(
            code = fields["code"].orEmpty().filter(Char::isDigit),
            address = fields["address"].orEmpty().trim(),
            // Un token copié depuis Home Assistant peut emporter des espaces ou un retour à la ligne.
            token = fields["token"].orEmpty().filterNot(Char::isWhitespace),
        )
    }

    fun response(status: String, html: String): ByteArray {
        val body = html.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            // La page porte un token : ni cache, ni envoi de l'adresse à un autre site.
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Frame-Options: DENY\r\n" +
            "Connection: close\r\n\r\n"
        return head.toByteArray(Charsets.US_ASCII) + body
    }

    private fun readHead(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (matched < 4) {
            val byte = input.read()
            if (byte < 0 || buffer.size() >= MAX_HEADER_BYTES) return null
            buffer.write(byte)
            matched = when {
                byte == END[matched].code -> matched + 1
                byte == '\r'.code -> 1
                else -> 0
            }
        }
        return buffer.toString(Charsets.US_ASCII.name()).removeSuffix("\r\n\r\n")
    }

    private fun decode(text: String): String = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (_: IllegalArgumentException) {
        ""
    }

    private const val END = "\r\n\r\n"
}

/**
 * Code à six chiffres affiché sur la TV et demandé par la page : seul quelqu'un qui voit l'écran
 * peut envoyer un token. Après [MAX_ATTEMPTS] codes faux, un nouveau code est tiré.
 */
class PairingCode(private val random: SecureRandom = SecureRandom()) {

    var value: String = draw()
        private set

    private var failures = 0

    /** true si [attempt] est le bon code. Un code faux compte comme un essai. */
    fun check(attempt: String): Boolean {
        if (attempt == value) return true
        failures++
        if (failures >= MAX_ATTEMPTS) renew()
        return false
    }

    fun renew() {
        value = draw()
        failures = 0
    }

    private fun draw(): String = "%06d".format(random.nextInt(1_000_000))

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
