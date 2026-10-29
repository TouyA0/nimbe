package io.github.touya0.nimbe.data.nextcloud

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.touya0.nimbe.core.storage.SecretVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONException
import org.json.JSONObject

private val Context.nextcloudStore by preferencesDataStore(name = "nextcloud")

/** La galerie reliée à un profil. Rien de secret ici : le mot de passe d'application est dans le coffre. */
data class NextcloudLink(val serverUrl: String, val loginName: String, val userId: String, val displayName: String)

/** Lecture et écriture d'un lien, sans Android, pour les tests. Un lien illisible est ignoré. */
object NextcloudLinkCodec {
    fun encode(link: NextcloudLink): String = JSONObject()
        .put("server", link.serverUrl)
        .put("login", link.loginName)
        .put("user", link.userId)
        .put("name", link.displayName)
        .toString()

    fun decode(text: String): NextcloudLink? = try {
        val json = JSONObject(text)
        NextcloudLink(json.getString("server"), json.getString("login"), json.getString("user"), json.optString("name"))
    } catch (_: JSONException) {
        null
    }
}

/**
 * Les comptes Nextcloud de la TV, un par profil adulte : chacun relie sa propre galerie.
 * Liens dans DataStore (clé « link.<profil> »), mots de passe d'application dans le coffre chiffré.
 * L'adresse du serveur, la même pour toute la maison, est retenue pour pré-remplir le profil suivant.
 */
class NextcloudAccounts(context: Context, private val vault: SecretVault) {

    private val appContext = context.applicationContext
    private val store = appContext.nextcloudStore

    val api = NextcloudApi(::userAgent)

    /** Lien de chaque profil relié, par identifiant de profil. */
    val links: Flow<Map<String, NextcloudLink>> = store.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            val profileId = key.name.removePrefix(LINK_PREFIX).takeIf { key.name.startsWith(LINK_PREFIX) } ?: return@mapNotNull null
            NextcloudLinkCodec.decode(value as? String ?: return@mapNotNull null)?.let { profileId to it }
        }.toMap()
    }

    /** Dernière adresse vérifiée, pour ne pas la ressaisir à chaque profil. */
    val lastServer: Flow<String?> = store.data.map { it[LAST_SERVER] }

    /** null si le profil n'est pas relié, ou si le mot de passe est devenu illisible (il faudra relier à nouveau). */
    suspend fun auth(profileId: String): NextcloudAuth? {
        val link = links.first()[profileId] ?: return null
        return NextcloudAuth(link.serverUrl, link.loginName, vault.get(secret(profileId)) ?: return null)
    }

    suspend fun rememberServer(serverUrl: String) {
        store.edit { it[LAST_SERVER] = serverUrl }
    }

    /** Relier à nouveau un profil remplace l'ancien accès, qu'on révoque d'abord pour ne pas le laisser traîner. */
    suspend fun save(profileId: String, auth: NextcloudAuth, user: NextcloudUser) {
        auth(profileId)?.takeIf { it.appPassword != auth.appPassword }?.let { old -> runCatching { api.revoke(old) } }
        // Le mot de passe d'abord : un lien enregistré sans lui ferait croire à une connexion.
        vault.put(secret(profileId), auth.appPassword)
        store.edit {
            it[link(profileId)] = NextcloudLinkCodec.encode(NextcloudLink(auth.server, auth.loginName, user.id, user.displayName))
            it[LAST_SERVER] = auth.server
        }
    }

    /**
     * Supprime le mot de passe d'application sur le serveur s'il répond, puis l'oublie, même hors ligne.
     * Rend false si le serveur n'a pas pu le révoquer : il faudra retirer l'appareil dans Nextcloud.
     */
    suspend fun disconnect(profileId: String): Boolean {
        val revoked = auth(profileId)?.let { runCatching { api.revoke(it) }.isSuccess } ?: true
        vault.remove(secret(profileId))
        store.edit { it.remove(link(profileId)) }
        return revoked
    }

    /** Le nom donné à la TV dans Android (« Salon »), sinon son modèle : Nextcloud l'affiche dans les sessions. */
    private fun userAgent(): String {
        val name = Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
        return "Nimbe ($name)"
    }

    private fun secret(profileId: String) = "nextcloud.$profileId"

    private fun link(profileId: String): Preferences.Key<String> = stringPreferencesKey("$LINK_PREFIX$profileId")

    private companion object {
        const val LINK_PREFIX = "link."
        val LAST_SERVER = stringPreferencesKey("last_server")
    }
}
