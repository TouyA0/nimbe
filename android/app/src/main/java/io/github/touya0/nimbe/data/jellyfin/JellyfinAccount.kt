package io.github.touya0.nimbe.data.jellyfin

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.touya0.nimbe.core.storage.SecretVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.jellyfinStore by preferencesDataStore(name = "jellyfin")

/** Le serveur et le compte reliés à cette TV. Rien de secret ici : le jeton est dans le coffre. */
data class JellyfinLink(
    val serverUrl: String,
    val serverId: String,
    val serverName: String,
    val userId: String,
    val userName: String,
)

/**
 * Connexion de la TV à Jellyfin : adresse et compte dans DataStore, jeton dans le coffre chiffré.
 * L'identifiant d'appareil est tiré une fois : Jellyfin remplace alors l'ancienne session de cette TV
 * à chaque reconnexion, au lieu d'empiler les appareils.
 */
class JellyfinAccount(context: Context, private val vault: SecretVault) {

    private val appContext = context.applicationContext
    private val store = appContext.jellyfinStore

    val api = JellyfinApi(::clientInfo)

    val link: Flow<JellyfinLink?> = store.data.map { prefs ->
        val url = prefs[SERVER_URL] ?: return@map null
        JellyfinLink(
            serverUrl = url,
            serverId = prefs[SERVER_ID].orEmpty(),
            serverName = prefs[SERVER_NAME].orEmpty(),
            userId = prefs[USER_ID].orEmpty(),
            userName = prefs[USER_NAME].orEmpty(),
        )
    }

    /** null si la TV n'est pas connectée, ou si le jeton est devenu illisible. */
    suspend fun token(): String? = vault.get(TOKEN)

    suspend fun auth(): JellyfinAuth? {
        val current = link.first() ?: return null
        return JellyfinAuth(current.serverUrl, current.userId, token() ?: return null)
    }

    suspend fun save(serverUrl: String, server: ServerInfo, session: JellyfinSession) {
        // Le jeton d'abord : un compte enregistré sans jeton ferait croire à une connexion.
        vault.put(TOKEN, session.token)
        store.edit {
            it[SERVER_URL] = serverUrl
            it[SERVER_ID] = server.id
            it[SERVER_NAME] = server.name
            it[USER_ID] = session.userId
            it[USER_NAME] = session.userName
        }
    }

    /** Révoque le jeton sur le serveur si possible, puis oublie le compte (même si le Pi est coupé). */
    suspend fun disconnect() {
        val url = link.first()?.serverUrl
        val token = token()
        if (url != null && token != null) runCatching { api.logout(url, token) }
        vault.remove(TOKEN)
        store.edit { prefs ->
            listOf(SERVER_URL, SERVER_ID, SERVER_NAME, USER_ID, USER_NAME).forEach { prefs.remove(it) }
        }
    }

    private suspend fun clientInfo(): ClientInfo = ClientInfo(
        deviceName = deviceName(),
        deviceId = deviceId(),
        version = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "0.0.0",
    )

    private suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.let { return it }
        val created = UUID.randomUUID().toString()
        var kept = created
        store.edit { prefs -> kept = prefs[DEVICE_ID] ?: created.also { prefs[DEVICE_ID] = it } }
        return kept
    }

    /** Le nom donné à la TV dans Android (« Salon »), sinon son modèle. */
    private fun deviceName(): String =
        Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private companion object {
        const val TOKEN = "jellyfin.token"
        val SERVER_URL = stringPreferencesKey("server_url")
        val SERVER_ID = stringPreferencesKey("server_id")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
