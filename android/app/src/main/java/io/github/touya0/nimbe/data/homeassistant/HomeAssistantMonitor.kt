package io.github.touya0.nimbe.data.homeassistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

/** Où en est la connexion en direct à Home Assistant. */
sealed interface HaConnection {
    /** Aucun Home Assistant relié à cette TV. */
    data object Off : HaConnection

    /** Première connexion en cours : on ne sait encore rien, sans pour autant crier à la panne. */
    data object Connecting : HaConnection

    data class Live(val since: Long) : HaConnection

    /** [since] : première tentative ratée ; [refused] : le token n'est plus accepté. */
    data class Lost(val since: Long, val refused: Boolean) : HaConnection
}

/**
 * Ce que la TV sait de Home Assistant à cet instant. [entities] garde les dernières valeurs reçues
 * même quand la connexion est perdue : l'écran « État inconnu » les montre grisées, avec leur heure.
 */
data class HaLive(
    val connection: HaConnection = HaConnection.Connecting,
    val sensors: List<String> = emptyList(),
    val entities: Map<String, HaEntity> = emptyMap(),
    /** Prochain essai automatique après une perte de connexion. */
    val retryAt: Long? = null,
)

/**
 * Suivi en direct des capteurs choisis, par le WebSocket (subscribe_entities) : Home Assistant envoie
 * chaque changement, sans requête en boucle. La connexion n'est ouverte que tant qu'un écran regarde
 * [live] ; perdue, elle est retentée toutes les 30 secondes, ou tout de suite avec [retryNow].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeAssistantMonitor(private val account: HomeAssistantAccount, scope: CoroutineScope) {

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val live: StateFlow<HaLive> = account.link
        .flatMapLatest { link -> if (link == null) flowOf(HaLive(HaConnection.Off)) else watch(link) }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), HaLive())

    /** Les 24 dernières heures de [entityId], par une connexion à part (l'écran qui la demande attend la réponse). */
    suspend fun history(entityId: String, now: Long = System.currentTimeMillis()): List<HistoryPoint> {
        val auth = account.auth() ?: throw HomeAssistantException(HomeAssistantException.Kind.Unauthorized, "Token illisible")
        return account.api.session(auth.server, auth.token) { history(entityId, now - DAY_MS) }
    }

    fun retryNow() {
        retries.tryEmit(Unit)
    }

    private fun watch(link: HomeAssistantLink): Flow<HaLive> = channelFlow {
        var state = HaLive(HaConnection.Connecting, link.sensors)
        send(state)
        while (true) {
            try {
                val auth = account.auth() ?: throw HomeAssistantException(HomeAssistantException.Kind.Unauthorized, "Token illisible")
                account.api.session(auth.server, auth.token) {
                    val registry = registry()
                    val subscription = subscribeEntities(link.sensors)
                    state = state.copy(connection = HaConnection.Live(System.currentTimeMillis()), retryAt = null)
                    send(state)
                    while (true) {
                        val received = CompressedStates.apply(state.entities, nextEvent(subscription))
                        val entities = received.mapValues { (id, entity) ->
                            registry[id]?.let { entity.copy(platform = it.platform, translationKey = it.translationKey) } ?: entity
                        }
                        state = state.copy(entities = entities)
                        send(state)
                    }
                }
            } catch (e: HomeAssistantException) {
                val now = System.currentTimeMillis()
                val since = (state.connection as? HaConnection.Lost)?.since ?: now
                state = state.copy(
                    connection = HaConnection.Lost(since, refused = e.kind == HomeAssistantException.Kind.Unauthorized),
                    retryAt = now + RETRY_MS,
                )
                send(state)
            }
            withTimeoutOrNull(RETRY_MS) { retries.first() }
        }
    }

    private companion object {
        const val RETRY_MS = 30_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Un retour rapide (réglages ouverts puis refermés) ne coupe pas la connexion. */
        const val STOP_DELAY_MS = 5_000L
    }
}
