package io.github.touya0.nimbe.feature.onboarding

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.touya0.nimbe.data.jellyfin.DiscoveredServer
import io.github.touya0.nimbe.data.jellyfin.JellyfinAccount
import io.github.touya0.nimbe.data.jellyfin.JellyfinDiscovery
import io.github.touya0.nimbe.data.jellyfin.JellyfinException
import io.github.touya0.nimbe.data.jellyfin.ServerAddress
import io.github.touya0.nimbe.data.jellyfin.ServerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Un serveur Jellyfin vérifié : il a répondu à cette adresse. */
data class FoundServer(val url: String, val info: ServerInfo)

sealed interface ConnectStep {
    data object Searching : ConnectStep
    data class Found(val servers: List<FoundServer>) : ConnectStep
    data class Manual(val notice: String?) : ConnectStep

    /** [trouble] : le serveur ne répond plus pendant l'attente, on continue d'essayer. */
    data class Code(val server: FoundServer, val code: String, val expiresAt: Long, val trouble: String? = null) : ConnectStep
    data class Failed(val server: FoundServer, val message: String) : ConnectStep
    data class Done(val userName: String, val serverName: String) : ConnectStep
}

/**
 * Connexion à Jellyfin en trois temps : trouver le serveur (découverte, sinon adresse saisie),
 * afficher un code Quick Connect, attendre qu'il soit autorisé sur le téléphone.
 * Aucun mot de passe ne passe par la TV.
 */
@Stable
class JellyfinConnectController(
    private val account: JellyfinAccount,
    private val discovery: JellyfinDiscovery,
    private val scope: CoroutineScope,
) {
    var step by mutableStateOf<ConnectStep>(ConnectStep.Searching)
        private set

    /** Vérification de l'adresse saisie en cours. */
    var checking by mutableStateOf(false)
        private set
    var addressError by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    fun search() = launchStep {
        step = ConnectStep.Searching
        val servers = discovery.find().mapNotNull { resolve(it) }
        step = if (servers.isEmpty()) ConnectStep.Manual("Aucun serveur trouvé tout seul sur ton réseau.") else ConnectStep.Found(servers)
    }

    fun enterAddress() {
        job?.cancel()
        checking = false
        addressError = null
        step = ConnectStep.Manual(null)
    }

    fun tryAddress(input: String) {
        val url = ServerAddress.parse(input).getOrElse {
            addressError = it.message
            return
        }
        addressError = null
        launchStep {
            checking = true
            val info = try {
                account.api.publicInfo(url)
            } catch (e: JellyfinException) {
                addressError = "${e.message} à cette adresse"
                return@launchStep
            } finally {
                checking = false
            }
            quickConnect(FoundServer(url, info))
        }
    }

    fun choose(server: FoundServer) = launchStep { quickConnect(server) }

    /** Une seule tâche à la fois : changer d'étape arrête l'attente précédente. */
    private fun launchStep(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }

    /** La découverte donne des adresses candidates : on garde la première où le même serveur répond. */
    private suspend fun resolve(found: DiscoveredServer): FoundServer? {
        for (url in found.addresses) {
            val info = runCatching { account.api.publicInfo(url) }.getOrNull() ?: continue
            if (info.id == found.id) return FoundServer(url, info)
        }
        return null
    }

    private suspend fun quickConnect(server: FoundServer) {
        val api = account.api
        // Un code vit 10 minutes sur le serveur ; on en redemande un, sans attendre indéfiniment.
        repeat(MAX_CODES) {
            val request = try {
                api.initiateQuickConnect(server.url)
            } catch (e: JellyfinException) {
                step = ConnectStep.Failed(server, e.message.orEmpty())
                return
            }
            val expiresAt = System.currentTimeMillis() + CODE_LIFETIME_MS
            step = ConnectStep.Code(server, request.code, expiresAt)
            while (System.currentTimeMillis() < expiresAt) {
                delay(POLL_MS)
                val authorized = try {
                    api.isQuickConnectAuthorized(server.url, request.secret)
                } catch (e: JellyfinException) {
                    if (e.kind == JellyfinException.Kind.Expired) break
                    (step as? ConnectStep.Code)?.let { step = it.copy(trouble = "Le serveur ne répond plus, nouvel essai…") }
                    continue
                }
                (step as? ConnectStep.Code)?.takeIf { it.trouble != null }?.let { step = it.copy(trouble = null) }
                if (authorized) {
                    step = try {
                        val session = api.authenticateWithQuickConnect(server.url, request.secret)
                        account.save(server.url, server.info, session)
                        ConnectStep.Done(session.userName, server.info.name)
                    } catch (e: JellyfinException) {
                        ConnectStep.Failed(server, e.message.orEmpty())
                    }
                    return
                }
            }
        }
        step = ConnectStep.Failed(server, "Le code a expiré sans être autorisé")
    }

    private companion object {
        const val POLL_MS = 3_000L
        const val CODE_LIFETIME_MS = 10 * 60_000L
        const val MAX_CODES = 3
    }
}
