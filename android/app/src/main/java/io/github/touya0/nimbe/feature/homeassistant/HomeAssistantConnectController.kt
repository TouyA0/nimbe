package io.github.touya0.nimbe.feature.homeassistant

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.touya0.nimbe.data.homeassistant.HaEntity
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantAccount
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantAuth
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantException
import io.github.touya0.nimbe.data.homeassistant.HomeAssistantUser
import io.github.touya0.nimbe.data.homeassistant.SensorCatalog
import io.github.touya0.nimbe.data.jellyfin.ServerAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un token vérifié : Home Assistant l'a accepté. Pas encore enregistré tant qu'on n'a pas fait « Continuer ». */
data class ReceivedToken(val auth: HomeAssistantAuth, val user: HomeAssistantUser)

sealed interface SensorsState {
    data object NoToken : SensorsState
    data object Loading : SensorsState
    data class Failed(val message: String) : SensorsState

    /** [candidates] dans l'ordre d'affichage ; [selected] : identifiants cochés. */
    data class Loaded(val candidates: List<HaEntity>, val selected: Set<String>) : SensorsState
}

/** Où le téléphone peut joindre la page de configuration ; [address] null si le stick n'a pas de réseau. */
data class PairingEndpoint(val address: String?, val port: Int?) {
    val url: String? get() = if (address != null && port != null) "http://$address:$port" else null
}

/**
 * Connexion à Home Assistant : la TV sert une page, le téléphone y envoie le code affiché, l'adresse
 * et un token longue durée. La TV vérifie le token, montre les capteurs détectés, et n'enregistre
 * le tout qu'à « Continuer ». Le token ne se tape jamais à la télécommande.
 */
@Stable
class HomeAssistantConnectController(
    private val account: HomeAssistantAccount,
    private val scope: CoroutineScope,
    /** Adresse proposée sur la page quand rien n'est enregistré (le Pi de Jellyfin, port 8123). */
    private val fallbackAddress: String,
) {
    private val pairingCode = PairingCode()

    var code by mutableStateOf(pairingCode.value)
        private set
    var endpoint by mutableStateOf<PairingEndpoint?>(null)
        private set
    var received by mutableStateOf<ReceivedToken?>(null)
        private set

    /** Dernier envoi refusé (code faux, token refusé…), affiché sur la TV aussi. */
    var lastRejection by mutableStateOf<String?>(null)
        private set
    var sensors by mutableStateOf<SensorsState>(SensorsState.NoToken)
        private set

    private var storedSensors: List<String> = emptyList()
    private var loadJob: Job? = null

    val server = PairingServer(suggestedAddress = { received?.auth?.server ?: fallbackAddress }, onSubmit = ::submit)

    /** À l'ouverture : la page de configuration, et les capteurs du compte déjà relié s'il y en a un. */
    fun start(localAddress: String?) {
        endpoint = PairingEndpoint(localAddress, server.start(scope))
        scope.launch {
            val link = account.link.first() ?: return@launch
            val auth = account.auth() ?: return@launch
            storedSensors = link.sensors
            if (received == null) {
                received = ReceivedToken(auth, HomeAssistantUser(link.userName, link.isAdmin))
                loadSensors()
            }
        }
    }

    fun stop() = server.stop()

    fun retry() = loadSensors()

    fun toggle(id: String) {
        val loaded = sensors as? SensorsState.Loaded ?: return
        sensors = loaded.copy(selected = if (id in loaded.selected) loaded.selected - id else loaded.selected + id)
    }

    /** Enregistre le token et les capteurs cochés. Renvoie false s'il n'y a rien à enregistrer. */
    suspend fun save(): Boolean {
        val token = received ?: return false
        val loaded = sensors as? SensorsState.Loaded ?: return false
        val chosen = loaded.candidates.map { it.id }.filter { it in loaded.selected }
        if (chosen.isEmpty()) return false
        account.save(token.auth, token.user, chosen)
        return true
    }

    /** Appelé par le serveur local pour chaque formulaire envoyé. */
    private suspend fun submit(form: PairingForm): PairingReply = withContext(Dispatchers.Main) {
        if (!pairingCode.check(form.code)) {
            val renewed = pairingCode.value != code
            code = pairingCode.value
            val message = if (renewed) "Trop d'essais : un nouveau code est affiché sur la TV" else "Code incorrect : recopie celui de la TV"
            lastRejection = message
            return@withContext PairingReply.Rejected(message, form.address, code = "")
        }
        val url = ServerAddress.parse(form.address, HOME_ASSISTANT_PORT).getOrElse {
            return@withContext reject(it.message.orEmpty(), form)
        }
        if (form.token.isEmpty()) return@withContext reject("Colle le token d'accès", form)

        val (user, entities) = try {
            account.api.session(url, form.token) { currentUser() to states() }
        } catch (e: HomeAssistantException) {
            val message = if (e.kind == HomeAssistantException.Kind.Unreachable) "Home Assistant injoignable à cette adresse" else e.message.orEmpty()
            return@withContext reject(message, form)
        }
        // Un code ne sert qu'une fois : le prochain envoi demandera le nouveau.
        pairingCode.renew()
        code = pairingCode.value
        lastRejection = null
        loadJob?.cancel()
        received = ReceivedToken(HomeAssistantAuth(url, form.token), user)
        sensors = loaded(entities)
        PairingReply.Accepted(user.name)
    }

    private fun reject(message: String, form: PairingForm): PairingReply {
        lastRejection = message
        return PairingReply.Rejected(message, form.address, form.code)
    }

    private fun loadSensors() {
        val token = received ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            sensors = SensorsState.Loading
            sensors = try {
                loaded(account.api.session(token.auth.server, token.auth.token) { states() })
            } catch (e: HomeAssistantException) {
                SensorsState.Failed(e.message.orEmpty())
            }
        }
    }

    /**
     * Capteurs reconnus, plus ceux déjà suivis que Home Assistant connaît encore.
     * Cochés : la sélection enregistrée, sinon tous les capteurs reconnus.
     */
    private fun loaded(entities: List<HaEntity>): SensorsState.Loaded {
        val recognized = SensorCatalog.candidates(entities)
        val kept = entities.filter { it.id in storedSensors && it !in recognized }
        val candidates = recognized + kept
        val selected = storedSensors.filter { id -> candidates.any { it.id == id } }.toSet()
        return SensorsState.Loaded(candidates, selected.ifEmpty { recognized.map { it.id }.toSet() })
    }

    companion object {
        const val HOME_ASSISTANT_PORT = 8123
    }
}
