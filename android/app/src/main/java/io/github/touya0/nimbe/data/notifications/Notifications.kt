package io.github.touya0.nimbe.data.notifications

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Critique : reste jusqu'à fermeture, même pendant un film. Normal : hors lecture. Discret : jamais pendant un film. */
enum class NotificationLevel { Critical, Normal, Discreet }

/** Ce que la cloche ou le bandeau montre pour chaque type, réglable par profil. */
enum class DeliveryMode(val label: String) { Banner("Bandeau"), Bell("Cloche seule"), Never("Jamais") }

/** [choices] : un serveur en panne ne se coupe pas tout à fait, il va au moins dans la cloche. */
enum class NotificationType(
    val key: String,
    val label: String,
    val detail: String,
    val defaultMode: DeliveryMode,
    val choices: List<DeliveryMode> = DeliveryMode.entries,
) {
    Server("server", "Serveur ou service en panne", "Critique · Le Pi ou Jellyfin ne répond plus", DeliveryMode.Banner, listOf(DeliveryMode.Banner, DeliveryMode.Bell)),
    Disk("disk", "Disque presque plein", "Normal · Disque du Pi au-delà de 80 %", DeliveryMode.Banner),
    Home("home", "Alertes de Home Assistant", "Selon l'automatisation · Événement nimbe_notify", DeliveryMode.Banner),
    Episode("episode", "Nouvel épisode d'une série suivie", "Discret · Une série que tu as commencée", DeliveryMode.Bell),
    Update("update", "Mise à jour de Nimbe", "Discret · Une nouvelle version est prête", DeliveryMode.Bell),

    /** Pour le seul profil Enfant : pas de ligne dans les réglages, l'enfant n'en a pas. */
    ScreenTime("screen_time", "Fin du temps d'écran", "Normal · Prévenir 5 min avant", DeliveryMode.Banner),
    ;

    companion object {
        val Settable = entries - ScreenTime
        fun fromKey(key: String): NotificationType = entries.firstOrNull { it.key == key } ?: Home
    }
}

/**
 * Une notification enregistrée. [key] l'identifie : la même alerte ne revient pas deux fois (redémarrage,
 * reconnexion). « Tout effacer » la cache sans la retirer, pour la même raison ; le ménage se fait au bout d'un mois.
 * Rien de secret : seulement ce qu'affiche l'écran.
 */
@Entity(tableName = "notifications")
data class NotificationEntry(
    @PrimaryKey val key: String,
    val type: String,
    val level: String,
    /** « ALERTE SERVEUR », « NOUVEL ÉPISODE »… */
    val kicker: String,
    val title: String,
    val body: String?,
    val at: Long,
    /** Où mène OK : voir [NotificationTarget]. */
    val target: String?,
    val read: Boolean = false,
    val cleared: Boolean = false,
) {
    val notificationType: NotificationType get() = NotificationType.fromKey(type)
    val notificationLevel: NotificationLevel get() = NotificationLevel.entries.firstOrNull { it.name == level } ?: NotificationLevel.Normal
}

object NotificationTarget {
    const val STATUS = "status"
    const val UPDATE = "update"
    private const val ITEM = "item:"

    fun item(id: String) = ITEM + id
    fun itemId(target: String?): String? = target?.takeIf { it.startsWith(ITEM) }?.removePrefix(ITEM)
}

@Dao
interface NotificationDao {

    /** -1 si cette alerte existe déjà. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: NotificationEntry): Long

    @Query("SELECT * FROM notifications WHERE cleared = 0 ORDER BY at DESC LIMIT 50")
    fun visible(): Flow<List<NotificationEntry>>

    @Query("UPDATE notifications SET read = 1 WHERE read = 0")
    suspend fun markAllRead()

    @Query("UPDATE notifications SET cleared = 1, read = 1 WHERE cleared = 0")
    suspend fun clearAll()

    @Query("DELETE FROM notifications WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)
}
