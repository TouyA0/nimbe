package io.github.touya0.nimbe.core.storage

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffrement des secrets en AES-256-GCM : confidentialité et intégrité.
 * Le nom de l'entrée est lié au chiffré (données associées) : on ne peut pas recopier
 * le jeton Nextcloud à la place du jeton Jellyfin sans que le déchiffrement échoue.
 * La clé est fournie de l'extérieur : Android Keystore dans l'appli, clé logicielle dans les tests.
 */
class SecretCodec(private val key: () -> SecretKey) {

    /** Format enregistré : base64(IV de 12 octets + chiffré avec son étiquette). */
    fun encrypt(name: String, plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Pas d'IV fourni : c'est au fournisseur d'en tirer un neuf à chaque fois (le Keystore l'impose).
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "IV inattendu" }
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.size + sealed.size).put(iv).put(sealed).array())
    }

    /** null si le chiffré est abîmé, n'appartient pas à cette entrée, ou si la clé a changé. */
    fun decrypt(name: String, stored: String): String? = try {
        val bytes = Base64.getDecoder().decode(stored)
        if (bytes.size <= IV_BYTES) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
