package io.github.touya0.nimbe.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

private val Context.vaultStore by preferencesDataStore(name = "vault")

/**
 * Coffre des secrets de la TV : jetons Jellyfin, mot de passe d'application Nextcloud, jeton
 * Home Assistant, jetons Spotify. Chiffrés avec une clé AES de l'Android Keystore, qui ne quitte
 * jamais le stick. La sauvegarde Android est désactivée : un secret ne part pas dans le cloud.
 *
 * On n'utilise pas EncryptedSharedPreferences, dépréciée par Google.
 * Ne jamais journaliser une valeur lue ici.
 */
class SecretVault(context: Context) {

    private val store = context.applicationContext.vaultStore
    private val codec = SecretCodec(::keystoreKey)

    suspend fun put(name: String, value: String) {
        val sealed = codec.encrypt(name, value)
        store.edit { it[key(name)] = sealed }
    }

    /** null si absent, ou illisible (clé changée, données abîmées) : il faudra se reconnecter. */
    suspend fun get(name: String): String? {
        val sealed = store.data.first()[key(name)] ?: return null
        return codec.decrypt(name, sealed) ?: run {
            // Un secret qu'on ne peut plus déchiffrer ne servira plus : on le retire.
            remove(name)
            null
        }
    }

    suspend fun remove(name: String) {
        store.edit { it.remove(key(name)) }
    }

    /** « Tout remettre à zéro » : efface tous les secrets de cette TV. */
    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun key(name: String) = stringPreferencesKey("secret.$name")

    private companion object {
        const val ALIAS = "nimbe.vault"

        /** Clé AES-256 créée une fois dans le Keystore, non exportable, réservée au chiffrement GCM. */
        @Synchronized
        fun keystoreKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}
