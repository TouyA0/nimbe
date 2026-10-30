package io.github.touya0.nimbe.core.storage

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import io.github.touya0.nimbe.data.nextcloud.GalleryDao
import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import io.github.touya0.nimbe.data.nextcloud.GallerySync
import io.github.touya0.nimbe.data.notifications.NotificationDao
import io.github.touya0.nimbe.data.notifications.NotificationEntry
import io.github.touya0.nimbe.data.streaming.StreamingHistoryDao
import io.github.touya0.nimbe.data.streaming.StreamingOpen
import kotlinx.coroutines.flow.Flow

/**
 * Base locale du launcher (Room) : lancements d'applis, dernières réponses de Jellyfin et des plateformes,
 * titres ouverts sur les plateformes et index des galeries Nextcloud, pour tenir quand le Pi est coupé. Chaque version ajoute sa migration, testée sur le schéma exporté.
 */
@Database(
    entities = [AppLaunch::class, CachedResponse::class, GalleryItem::class, GallerySync::class, StreamingOpen::class, NotificationEntry::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
abstract class NimbeDatabase : RoomDatabase() {
    abstract fun appLaunches(): AppLaunchDao
    abstract fun cachedResponses(): CachedResponseDao
    abstract fun gallery(): GalleryDao
    abstract fun streamingHistory(): StreamingHistoryDao
    abstract fun notifications(): NotificationDao

    companion object {
        fun create(context: Context): NimbeDatabase =
            Room.databaseBuilder(context.applicationContext, NimbeDatabase::class.java, "nimbe.db").build()
    }
}

/** Dernier lancement d'une appli depuis Nimbe, pour « Récemment utilisées ». */
@Entity(tableName = "app_launches")
data class AppLaunch(
    @PrimaryKey val packageName: String,
    val lastLaunchedAt: Long,
    val launchCount: Int,
)

@Dao
interface AppLaunchDao {

    @Query("SELECT * FROM app_launches ORDER BY lastLaunchedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<AppLaunch>>

    // Pas de « INSERT … ON CONFLICT DO UPDATE » : SQLite ne le connaît qu'à partir d'Android 11,
    // et le launcher doit tourner dès Android 9. D'où une mise à jour, puis une insertion si rien n'existait.
    @Transaction
    suspend fun recordLaunch(packageName: String, at: Long) {
        if (bump(packageName, at) == 0) insert(AppLaunch(packageName, at, launchCount = 1))
    }

    @Query("UPDATE app_launches SET lastLaunchedAt = :at, launchCount = launchCount + 1 WHERE packageName = :packageName")
    suspend fun bump(packageName: String, at: Long): Int

    @Insert
    suspend fun insert(launch: AppLaunch)

    @Query("DELETE FROM app_launches WHERE packageName = :packageName")
    suspend fun forget(packageName: String)
}

/**
 * Dernière réponse JSON d'une requête de contenu (liste de films, fiche…), telle que le serveur l'a envoyée.
 * [key] commence par le serveur et l'utilisateur : un autre compte ne lit jamais ces réponses.
 * Rien de secret ici : ni jeton ni adresse de flux.
 */
@Entity(tableName = "cached_responses")
data class CachedResponse(
    @PrimaryKey val key: String,
    val json: String,
    val savedAt: Long,
)

@Dao
interface CachedResponseDao {

    @Query("SELECT * FROM cached_responses WHERE `key` = :key")
    suspend fun get(key: String): CachedResponse?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(response: CachedResponse)

    /** Ménage : les réponses trop vieilles ne servent plus à rien. */
    @Query("DELETE FROM cached_responses WHERE savedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    /** Tout ce qui commence par [prefix] (les albums d'un profil Nextcloud, par exemple). */
    @Query("DELETE FROM cached_responses WHERE `key` LIKE :prefix || '%'")
    suspend fun deletePrefix(prefix: String)

    /** À la déconnexion : on oublie tout ce qui venait du compte. */
    @Query("DELETE FROM cached_responses")
    suspend fun clear()
}
