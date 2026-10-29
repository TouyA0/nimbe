package io.github.touya0.nimbe.data.streaming

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Un titre lancé depuis Nimbe sur une plateforme, pour « Continuer à regarder ».
 * Les plateformes ne disent pas où l'on en est : Nimbe retient seulement ce qui a été ouvert, et quand.
 * Un enregistrement par profil et par titre ; le profil Invité n'enregistre rien.
 */
@Entity(tableName = "streaming_opens", primaryKeys = ["profileId", "titleId"])
data class StreamingOpen(
    val profileId: String,
    val titleId: String,
    val kind: String,
    val title: String,
    val service: String,
    val year: Int?,
    val poster: String?,
    val backdrop: String?,
    val tmdbId: String?,
    val openedAt: Long,
) {
    val titleKind: TitleKind get() = TitleKind.entries.firstOrNull { it.name == kind } ?: TitleKind.Movie
    val streamingService: StreamingService? get() = StreamingService.fromCode(service)

    /** Ce que la fiche peut afficher avant d'avoir rechargé le titre. */
    fun toTitle(): StreamingTitle = StreamingTitle(id = titleId, kind = titleKind, title = title, year = year, poster = poster, backdrop = backdrop, tmdbId = tmdbId)

    companion object {
        fun of(profileId: String, title: StreamingTitle, service: StreamingService, at: Long) = StreamingOpen(
            profileId = profileId,
            titleId = title.id,
            kind = title.kind.name,
            title = title.title,
            service = service.code,
            year = title.year,
            poster = title.poster,
            backdrop = title.backdrop,
            tmdbId = title.tmdbId,
            openedAt = at,
        )
    }
}

@Dao
interface StreamingHistoryDao {

    @Query("SELECT * FROM streaming_opens WHERE profileId = :profileId AND openedAt >= :since ORDER BY openedAt DESC LIMIT :limit")
    fun recent(profileId: String, since: Long, limit: Int): Flow<List<StreamingOpen>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(open: StreamingOpen)

    @Query("DELETE FROM streaming_opens WHERE profileId = :profileId AND titleId = :titleId")
    suspend fun forget(profileId: String, titleId: String)

    /** Profil supprimé : son historique part avec lui. */
    @Query("DELETE FROM streaming_opens WHERE profileId = :profileId")
    suspend fun forgetProfile(profileId: String)

    @Query("DELETE FROM streaming_opens WHERE openedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
