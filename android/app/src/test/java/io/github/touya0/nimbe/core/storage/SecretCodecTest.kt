package io.github.touya0.nimbe.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator

/** Avec une clé AES logicielle : le Keystore d'Android n'existe pas dans les tests sur PC. */
class SecretCodecTest {

    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val key = newKey()
    private val codec = SecretCodec { key }

    @Test
    fun `aller-retour`() {
        val sealed = codec.encrypt("jellyfin.token", "abc123")
        assertEquals("abc123", codec.decrypt("jellyfin.token", sealed))
    }

    @Test
    fun `le secret n'apparaît pas en clair et chaque chiffrement est différent`() {
        val first = codec.encrypt("jellyfin.token", "abc123")
        val second = codec.encrypt("jellyfin.token", "abc123")
        assertFalse(String(Base64.getDecoder().decode(first)).contains("abc123"))
        assertNotEquals(first, second)
    }

    @Test
    fun `un chiffré recopié sous un autre nom est refusé`() {
        val sealed = codec.encrypt("nextcloud.password", "secret")
        assertNull(codec.decrypt("jellyfin.token", sealed))
    }

    @Test
    fun `un chiffré modifié est refusé`() {
        val bytes = Base64.getDecoder().decode(codec.encrypt("ha.token", "secret"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertNull(codec.decrypt("ha.token", Base64.getEncoder().encodeToString(bytes)))
    }

    @Test
    fun `une autre clé ne déchiffre pas`() {
        val sealed = codec.encrypt("ha.token", "secret")
        assertNull(SecretCodec { newKey() }.decrypt("ha.token", sealed))
    }

    @Test
    fun `des données illisibles donnent null sans planter`() {
        assertNull(codec.decrypt("ha.token", "pas du base64 !"))
        assertNull(codec.decrypt("ha.token", Base64.getEncoder().encodeToString(ByteArray(5))))
    }
}
