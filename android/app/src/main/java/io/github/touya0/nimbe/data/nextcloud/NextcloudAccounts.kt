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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private val Context.nextcloudStore by preferencesDataStore(name = "nextcloud")

/**
 * La galerie reliée à un profil. Rien de secret ici : le mot de passe d'application est dans le coffre.
 * [folders] : dossiers montrés, chemins depuis la racine du compte (« Photos/Vacances ») ; vide : tout le compte.
 * [localUrl] : le même Nextcloud joint directement sur le réseau de la maison (« http://192.168.1.182:7580 »),
 * utilisé quand il répond : bien plus rapide qu'un tunnel par internet, et il marche sans internet.
 */
data class NextcloudLink(
    val serverUrl: String,
    val loginName: String,
    val userId: String,
    val displayName: String,
    val folders: List<String> = emptyList(),
    val localUrl: String? = null,
)

/** Lecture et écriture d'un lien, sans Android, pour les tests. Un lien illisible est ignoré. */
object NextcloudLinkCodec {
    fun encode(link: NextcloudLink): String = JSONObject()
        .put("server", link.serverUrl)
        .put("login", link.loginName)
        .put("user", link.userId)
        .put("name", link.displayName)
        .put("folders", JSONArray(link.folders))
        .apply { link.localUrl?.let { put("local", it) } }
        .toString()

    fun decode(text: String): NextcloudLink? = try {
        val json = JSONObject(text)
        val folders = json.optJSONArray("folders")?.let { array -> List(array.length()) { array.getString(it) } }.orEmpty()
        NextcloudLink(
            json.getString("server"),
            json.getString("login"),
            json.getString("user"),
            json.optString("name"),
            folders,
            json.optString("local").ifEmpty { null },
        )
    } catch (_: JSONException) {
        null
    }
}

/**
 * Les comptes Nextcloud de la TV, un par profil adulte : chacun relie sa propre galerie.
 * Liens dans DataStore (clé « link.<profil> »), mots de passe d'application dans le coffre chiffré.
 * L'adresse du serveur, la même pour toute la maison, est retenue pour pré-remplir le profil suivant.
 */
class NextcloudAccounts(
    context: Context,
    private val vault: SecretVault,
    /** Après la déconnexion d'un profil : son index de galerie ne doit pas rester sur la TV. */
    private val onDisconnected: suspend (profileId: String) -> Unit = {},
) {

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
        return NextcloudAuth(server(link), link.loginName, vault.get(secret(profileId)) ?: return null)
    }

    /** Adresse à utiliser pour [link] : celle de la maison si elle a répondu récemment, sinon l'adresse publique. */
    suspend fun server(link: NextcloudLink): String {
        val local = link.localUrl ?: return link.serverUrl
        val now = System.currentTimeMillis()
        val known = localChecks[local]
        val reachable = if (known != null && now - known.second < LOCAL_CHECK_MS) known.first else api.answers(local).also { localChecks[local] = it to now }
        return if (reachable) local else link.serverUrl
    }

    /** L'adresse de la maison est-elle utilisée en ce moment par ce profil ? */
    suspend fun usingLocal(profileId: String): Boolean {
        val link = links.first()[profileId] ?: return false
        return link.localUrl != null && server(link) == link.localUrl
    }

    /**
     * Vérifie qu'à [localUrl] répond bien le même compte, avec le même mot de passe d'application, puis l'enregistre
     * pour toutes les galeries de ce serveur (toute la maison a le même). Lève [NextcloudException] sinon.
     */
    suspend fun setLocalUrl(profileId: String, localUrl: String?) {
        val link = links.first()[profileId] ?: return
        if (localUrl != null) {
            val password = vault.get(secret(profileId)) ?: throw NextcloudException(NextcloudException.Kind.Unauthorized, "Relie d'abord la galerie")
            val user = api.user(NextcloudAuth(localUrl, link.loginName, password))
            if (user.id != link.userId) throw NextcloudException(NextcloudException.Kind.NotNextcloud, "Ce n'est pas le même Nextcloud")
            localChecks[localUrl] = true to System.currentTimeMillis()
        }
        store.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(LINK_PREFIX) }.forEach { key ->
                @Suppress("UNCHECKED_CAST")
                val typed = key as Preferences.Key<String>
                val other = prefs[typed]?.let(NextcloudLinkCodec::decode) ?: return@forEach
                if (other.serverUrl == link.serverUrl) prefs[typed] = NextcloudLinkCodec.encode(other.copy(localUrl = localUrl))
            }
        }
    }

    /** Résultat du dernier essai de chaque adresse de la maison, et quand : on ne réessaie pas à chaque image. */
    private val localChecks = java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, Long>>()

    suspend fun rememberServer(serverUrl: String) {
        store.edit { it[LAST_SERVER] = serverUrl }
    }

    /** Relier à nouveau un profil remplace l'ancien accès, qu'on révoque d'abord pour ne pas le laisser traîner. */
    suspend fun save(profileId: String, auth: NextcloudAuth, user: NextcloudUser) {
        auth(profileId)?.takeIf { it.appPassword != auth.appPassword }?.let { old -> runCatching { api.revoke(old) } }
        // Le même compte relié à nouveau garde ses dossiers ; un autre compte repart de tout le compte.
        val previous = links.first()[profileId]?.takeIf { it.userId == user.id && it.serverUrl == auth.server }
        val folders = previous?.folders.orEmpty()
        // Le mot de passe d'abord : un lien enregistré sans lui ferait croire à une connexion.
        vault.put(secret(profileId), auth.appPassword)
        store.edit {
            it[link(profileId)] = NextcloudLinkCodec.encode(NextcloudLink(auth.server, auth.loginName, user.id, user.displayName, folders, previous?.localUrl))
            it[LAST_SERVER] = auth.server
        }
    }

    /** Dossiers montrés par la galerie du profil (vide : tout le compte). Sans effet si le profil n'est pas relié. */
    suspend fun setFolders(profileId: String, folders: List<String>) {
        store.edit { prefs ->
            val current = prefs[link(profileId)]?.let(NextcloudLinkCodec::decode) ?: return@edit
            prefs[link(profileId)] = NextcloudLinkCodec.encode(current.copy(folders = NextcloudFolders.normalize(folders)))
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
        onDisconnected(profileId)
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
        /** L'adresse de la maison est réessayée au plus toutes les 60 s (Pi redémarré, TV emportée ailleurs). */
        const val LOCAL_CHECK_MS = 60_000L
        val LAST_SERVER = stringPreferencesKey("last_server")
    }
}
