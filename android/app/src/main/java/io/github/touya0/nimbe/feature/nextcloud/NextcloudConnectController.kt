package io.github.touya0.nimbe.feature.nextcloud

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.touya0.nimbe.data.jellyfin.ServerAddress
import io.github.touya0.nimbe.data.nextcloud.LoginFlow
import io.github.touya0.nimbe.data.nextcloud.NextcloudAccounts
import io.github.touya0.nimbe.data.nextcloud.NextcloudException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface NextcloudStep {
    data object Checking : NextcloudStep
    data class Address(val notice: String?) : NextcloudStep

    /** [trouble] : le serveur ne répond plus pendant l'attente, on continue d'essayer. */
    data class Qr(val server: String, val loginUrl: String, val expiresAt: Long, val trouble: String? = null) : NextcloudStep
    data class Failed(val server: String, val message: String) : NextcloudStep
    data class Done(val displayName: String, val server: String) : NextcloudStep
}

/**
 * Relier la galerie Nextcloud d'un profil : vérifier l'adresse, afficher un QR code (Login Flow v2),
 * attendre l'autorisation sur le téléphone. Le serveur crée alors un mot de passe d'application
 * propre à cette TV ; le vrai mot de passe ne passe jamais par la TV.
 */
@Stable
class NextcloudConnectController(
    private val accounts: NextcloudAccounts,
    private val profileId: String,
    private val scope: CoroutineScope,
) {
    var step by mutableStateOf<NextcloudStep>(NextcloudStep.Checking)
        private set

    var checking by mutableStateOf(false)
        private set
    var addressError by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    /** Adresse déjà connue (un autre profil, ou ce profil avant) : droit au QR code ; sinon, on la demande. */
    fun start(knownServer: String?) {
        if (knownServer == null) {
            step = NextcloudStep.Address(null)
            return
        }
        launchStep {
            step = NextcloudStep.Checking
            try {
                accounts.api.status(knownServer)
            } catch (e: NextcloudException) {
                step = NextcloudStep.Address("${e.message} à ${knownServer.substringAfter("://")}")
                return@launchStep
            }
            login(knownServer)
        }
    }

    fun enterAddress() {
        job?.cancel()
        checking = false
        addressError = null
        step = NextcloudStep.Address(null)
    }

    fun tryAddress(input: String) {
        val url = ServerAddress.parse(input, defaultPort = 80, secureByDefault = true).getOrElse {
            addressError = it.message
            return
        }
        addressError = null
        launchStep {
            checking = true
            try {
                accounts.api.status(url)
            } catch (e: NextcloudException) {
                addressError = "${e.message} à cette adresse"
                return@launchStep
            } finally {
                checking = false
            }
            accounts.rememberServer(url)
            login(url)
        }
    }

    fun retry(server: String) = launchStep { login(server) }

    /** Une seule tâche à la fois : changer d'étape arrête l'attente précédente. */
    private fun launchStep(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }

    private suspend fun login(server: String) {
        val api = accounts.api
        // Une demande vit 20 minutes sur le serveur ; on en redemande une, sans attendre indéfiniment.
        repeat(MAX_FLOWS) {
            val flow: LoginFlow = try {
                api.startLogin(server)
            } catch (e: NextcloudException) {
                step = NextcloudStep.Failed(server, e.message.orEmpty())
                return
            }
            val expiresAt = System.currentTimeMillis() + FLOW_LIFETIME_MS
            step = NextcloudStep.Qr(server, flow.loginUrl, expiresAt)
            while (System.currentTimeMillis() < expiresAt) {
                delay(POLL_MS)
                val auth = try {
                    api.pollLogin(server, flow)
                } catch (_: NextcloudException) {
                    (step as? NextcloudStep.Qr)?.let { step = it.copy(trouble = "Le serveur ne répond plus, nouvel essai…") }
                    continue
                }
                (step as? NextcloudStep.Qr)?.takeIf { it.trouble != null }?.let { step = it.copy(trouble = null) }
                if (auth != null) {
                    step = try {
                        val user = api.user(auth)
                        accounts.save(profileId, auth, user)
                        NextcloudStep.Done(user.displayName, server)
                    } catch (e: NextcloudException) {
                        // Le mot de passe vient d'être créé : on ne le laisse pas actif sur le serveur.
                        runCatching { api.revoke(auth) }
                        NextcloudStep.Failed(server, e.message.orEmpty())
                    }
                    return
                }
            }
        }
        step = NextcloudStep.Failed(server, "La demande a expiré sans être autorisée")
    }

    private companion object {
        const val POLL_MS = 3_000L
        const val FLOW_LIFETIME_MS = 20 * 60_000L
        const val MAX_FLOWS = 3
    }
}
