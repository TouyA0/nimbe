package io.github.touya0.nimbe.data.homeassistant

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.touya0.nimbe.core.storage.SecretVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.homeAssistantStore by preferencesDataStore(name = "homeassistant")

/** Le Home Assistant relié à cette TV et les capteurs suivis. Rien de secret ici : le jeton est dans le coffre. */
data class HomeAssistantLink(
    val serverUrl: String,
    val userName: String,
    val isAdmin: Boolean,
    /** Identifiants d'entités, dans l'ordre d'affichage. */
    val sensors: List<String>,
)

/** De quoi ouvrir une connexion à Home Assistant. */
data class HomeAssistantAuth(val server: String, val token: String)

/**
 * Connexion de la TV à Home Assistant : adresse, compte et capteurs dans DataStore,
 * jeton longue durée dans le coffre chiffré.
 */
class HomeAssistantAccount(context: Context, private val vault: SecretVault) {

    private val store = context.applicationContext.homeAssistantStore

    val api = HomeAssistantApi()

    val link: Flow<HomeAssistantLink?> = store.data.map { prefs ->
        val url = prefs[SERVER_URL] ?: return@map null
        HomeAssistantLink(
            serverUrl = url,
            userName = prefs[USER_NAME].orEmpty(),
            isAdmin = prefs[IS_ADMIN] ?: false,
            sensors = prefs[SENSORS].orEmpty().split(',').filter { it.isNotBlank() },
        )
    }

    /** null si la TV n'est pas reliée, ou si le jeton est devenu illisible. */
    suspend fun auth(): HomeAssistantAuth? {
        val current = link.first() ?: return null
        return HomeAssistantAuth(current.serverUrl, vault.get(TOKEN) ?: return null)
    }

    suspend fun save(auth: HomeAssistantAuth, user: HomeAssistantUser, sensors: List<String>) {
        // Le jeton d'abord : un compte enregistré sans jeton ferait croire à une connexion.
        vault.put(TOKEN, auth.token)
        store.edit {
            it[SERVER_URL] = auth.server
            it[USER_NAME] = user.name
            it[IS_ADMIN] = user.isAdmin
            it[SENSORS] = sensors.joinToString(",")
        }
    }

    /** Révoque le jeton dans Home Assistant si le Pi répond, puis l'oublie (même si le Pi est coupé). */
    suspend fun disconnect() {
        auth()?.let { current ->
            HomeAssistantApi.refreshTokenId(current.token)?.let { id ->
                runCatching { api.session(current.server, current.token) { deleteRefreshToken(id) } }
            }
        }
        vault.remove(TOKEN)
        store.edit { prefs -> listOf(SERVER_URL, USER_NAME, SENSORS).forEach { prefs.remove(it) }; prefs.remove(IS_ADMIN) }
    }

    private companion object {
        const val TOKEN = "homeassistant.token"
        val SERVER_URL = stringPreferencesKey("server_url")
        val USER_NAME = stringPreferencesKey("user_name")
        val IS_ADMIN = booleanPreferencesKey("is_admin")
        val SENSORS = stringPreferencesKey("sensors")
    }
}
