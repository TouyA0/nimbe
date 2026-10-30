package io.github.touya0.nimbe.data.notifications

import io.github.touya0.nimbe.data.homeassistant.HaConnection
import io.github.touya0.nimbe.data.homeassistant.HaEntity
import io.github.touya0.nimbe.data.homeassistant.HaLive
import io.github.touya0.nimbe.data.homeassistant.SensorCatalog
import io.github.touya0.nimbe.data.homeassistant.SensorKind

/** Une alerte à enregistrer ; [key] : voir [NotificationEntry.key]. */
data class NewNotification(
    val key: String,
    val type: NotificationType,
    val level: NotificationLevel,
    val kicker: String,
    val title: String,
    val body: String? = null,
    val target: String? = null,
)

/**
 * Transforme l'état du Pi vu par Home Assistant en alertes : panne de plus de 2 minutes, retour à la normale,
 * disque au-delà de 80 %. Garde en mémoire les pannes annoncées, pour annoncer leur fin.
 */
class ServiceAlerts(private val downAfterMs: Long = DOWN_AFTER_MS) {

    /** Pannes annoncées : clé → heure de début. */
    private val announced = mutableMapOf<String, Long>()

    fun evaluate(ha: HaLive, networkUp: Boolean, now: Long): List<NewNotification> {
        val alerts = mutableListOf<NewNotification>()
        when (val connection = ha.connection) {
            is HaConnection.Lost -> {
                // Sans réseau sur le stick, le Pi n'y est pour rien ; un jeton refusé n'est pas une panne.
                if (networkUp && !connection.refused && now - connection.since >= downAfterMs) {
                    down(PI, connection.since, now, "Le Pi ne répond plus", "Home Assistant injoignable", alerts)
                }
            }
            is HaConnection.Live -> {
                restored(PI, now, "Le Pi répond de nouveau", alerts)
                ha.sensors.mapNotNull { ha.entities[it] }.forEach { entity -> service(entity, now, alerts) }
                ha.sensors.mapNotNull { ha.entities[it] }.forEach { entity -> disk(entity, now, alerts) }
            }
            else -> Unit
        }
        return alerts
    }

    private fun service(entity: HaEntity, now: Long, alerts: MutableList<NewNotification>) {
        if (SensorCatalog.kindOf(entity) != SensorKind.Service) return
        val name = entity.serviceName
        when (entity.state) {
            "off" -> {
                val since = entity.updatedAt ?: return
                if (now - since >= downAfterMs) down(entity.id, since, now, "$name ne répond plus", "Coupé depuis ${minutes(now - since)}", alerts)
            }
            "on" -> restored(entity.id, now, "$name est de nouveau en ligne", alerts)
        }
    }

    private fun disk(entity: HaEntity, now: Long, alerts: MutableList<NewNotification>) {
        if (SensorCatalog.kindOf(entity) != SensorKind.Disk) return
        val used = entity.state.toDoubleOrNull() ?: return
        if (used < DISK_LIMIT) return
        // Au plus une fois par semaine : un disque plein ne se vide pas tout seul.
        alerts += NewNotification(
            key = "disk:${entity.id}:${now / WEEK_MS}",
            type = NotificationType.Disk,
            level = NotificationLevel.Normal,
            kicker = "ALERTE SERVEUR",
            title = "Disque du Pi rempli à ${entity.displayValue}",
            body = "Home Assistant · seuil de 80 %",
            target = NotificationTarget.STATUS,
        )
    }

    private fun down(id: String, since: Long, now: Long, title: String, body: String, alerts: MutableList<NewNotification>) {
        announced[id] = since
        alerts += NewNotification(
            key = "down:$id:$since",
            type = NotificationType.Server,
            level = NotificationLevel.Critical,
            kicker = "ALERTE SERVEUR",
            title = title,
            body = body,
            target = NotificationTarget.STATUS,
        )
    }

    private fun restored(id: String, now: Long, title: String, alerts: MutableList<NewNotification>) {
        val since = announced.remove(id) ?: return
        alerts += NewNotification(
            key = "up:$id:$since",
            type = NotificationType.Server,
            level = NotificationLevel.Discreet,
            kicker = "SERVICE RÉTABLI",
            title = title,
            body = "Coupure de ${minutes(now - since)}",
            target = NotificationTarget.STATUS,
        )
    }

    private fun minutes(ms: Long): String = "${(ms / 60_000).coerceAtLeast(1)} min"

    private companion object {
        const val DOWN_AFTER_MS = 2 * 60_000L
        const val DISK_LIMIT = 80.0
        const val WEEK_MS = 7 * 24 * 60 * 60_000L
        const val PI = "pi"
    }
}
