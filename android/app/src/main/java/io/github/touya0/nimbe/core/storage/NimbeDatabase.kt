package io.github.touya0.nimbe.core.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Base locale du launcher (Room). Elle accueillera le cache des contenus (Jellyfin, guide TV…) pour
 * afficher l'accueil tout de suite et tenir quand le Pi est coupé. Pour l'instant : les lancements d'applis.
 */
@Database(entities = [AppLaunch::class], version = 1, exportSchema = true)
abstract class NimbeDatabase : RoomDatabase() {
    abstract fun appLaunches(): AppLaunchDao

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
