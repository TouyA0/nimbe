package io.github.touya0.nimbe.feature.homeassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.SecureRandom

class PairingHttpTest {

    private fun read(raw: String) = PairingHttp.read(ByteArrayInputStream(raw.toByteArray()))

    @Test
    fun `formulaire envoyé par le téléphone`() {
        val body = "code=482+913&address=http%3A%2F%2F192.168.1.182%3A8123&token=eyJh.eyJp%0D%0A.sig+"
        val request = read("POST /?x=1 HTTP/1.1\r\nHost: tv\r\ncontent-length: ${body.length}\r\n\r\n$body")!!
        assertEquals("POST", request.method)
        assertEquals("/", request.path)
        assertEquals(PairingForm("482913", "http://192.168.1.182:8123", "eyJh.eyJp.sig"), PairingHttp.form(request.body))
    }

    @Test
    fun `requêtes mal formées ou trop grosses`() {
        assertNull(read("GET /\r\n\r\n"))
        assertNull(read("GET / HTTP/1.1\r\nHost: tv\r\n"))
        assertNull(read("POST / HTTP/1.1\r\nContent-Length: ${PairingHttp.MAX_BODY_BYTES + 1}\r\n\r\n"))
        assertNull(read("POST / HTTP/1.1\r\nContent-Length: 10\r\n\r\ncourt"))
        assertNull(read("GET / HTTP/1.1\r\nX: ${"a".repeat(9000)}\r\n\r\n"))
        assertEquals("GET", read("GET / HTTP/1.1\r\n\r\n")?.method)
    }

    @Test
    fun `nouveau code après cinq erreurs`() {
        val code = PairingCode(SecureRandom())
        val first = code.value
        assertTrue(first.matches(Regex("""\d{6}""")))
        val wrong = if (first == "000000") "000001" else "000000"
        repeat(PairingCode.MAX_ATTEMPTS - 1) { assertFalse(code.check(wrong)) }
        assertTrue(code.check(first))
        // Le cinquième code faux tire un nouveau code : l'ancien ne marche plus.
        assertFalse(code.check(wrong))
        assertNotEquals(first, code.value)
        assertFalse(code.check(first))
    }

    @Test
    fun `la page échappe ce qu'elle réaffiche`() {
        val html = PairingPage.form(address = "\"><script>", error = "<b>")
        assertFalse("<script>" in html)
        assertTrue("&quot;&gt;&lt;script&gt;" in html)
        assertTrue("&lt;b&gt;" in html)
    }
}
