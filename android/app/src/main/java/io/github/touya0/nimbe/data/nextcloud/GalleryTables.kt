package io.github.touya0.nimbe.data.nextcloud

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Index local d'une galerie : une ligne par photo ou vidéo d'un profil. Sert à afficher la grille
 * tout de suite, et quand le serveur ne répond pas. Rien de secret : ni mot de passe ni contenu,
 * seulement des noms de fichiers et des dates (les aperçus sont dans le cache d'images).
 */
@Entity(
    tableName = "gallery_items",
    primaryKeys = ["profileId", "fileId"],
    indices = [Index(value = ["profileId", "takenAt"])],
)
data class GalleryItem(
    val profileId: String,
    val fileId: Long,
    val path: String,
    val mime: String,
    val takenAt: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val hasPreview: Boolean,
) {
    val isVideo: Boolean get() = mime.startsWith("video/")
}

/** Dernière synchronisation réussie d'une galerie. */
@Entity(tableName = "gallery_syncs")
data class GallerySync(@PrimaryKey val profileId: String, val syncedAt: Long)

data class GalleryCounts(val photos: Int, val videos: Int)

@Dao
interface GalleryDao {

    /** Remplace tout l'index d'un profil d'un coup : la grille ne voit jamais une synchronisation à moitié faite. */
    @Transaction
    suspend fun replace(profileId: String, items: List<GalleryItem>, syncedAt: Long) {
        deleteItems(profileId)
        items.chunked(500).forEach { insert(it) }
        putSync(GallerySync(profileId, syncedAt))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<GalleryItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSync(sync: GallerySync)

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN mime LIKE 'video/%' THEN 0 ELSE 1 END), 0) AS photos, " +
            "COALESCE(SUM(CASE WHEN mime LIKE 'video/%' THEN 1 ELSE 0 END), 0) AS videos " +
            "FROM gallery_items WHERE profileId = :profileId",
    )
    fun counts(profileId: String): Flow<GalleryCounts>

    @Query("SELECT syncedAt FROM gallery_syncs WHERE profileId = :profileId")
    fun syncedAt(profileId: String): Flow<Long?>

    /** Des plus récentes aux plus anciennes (date de prise de vue), par pages. */
    @Query("SELECT * FROM gallery_items WHERE profileId = :profileId ORDER BY takenAt DESC, fileId DESC LIMIT :limit OFFSET :offset")
    suspend fun page(profileId: String, offset: Int, limit: Int): List<GalleryItem>

    @Query("SELECT * FROM gallery_items WHERE profileId = :profileId ORDER BY takenAt DESC, fileId DESC LIMIT :limit")
    fun recent(profileId: String, limit: Int): Flow<List<GalleryItem>>

    @Query("DELETE FROM gallery_items WHERE profileId = :profileId")
    suspend fun deleteItems(profileId: String)

    @Query("DELETE FROM gallery_syncs WHERE profileId = :profileId")
    suspend fun deleteSync(profileId: String)

    /** Galerie déconnectée ou profil supprimé : on oublie aussi son index. */
    @Transaction
    suspend fun forget(profileId: String) {
        deleteItems(profileId)
        deleteSync(profileId)
    }
}
