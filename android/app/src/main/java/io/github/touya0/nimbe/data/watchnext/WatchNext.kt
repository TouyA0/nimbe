package io.github.touya0.nimbe.data.watchnext

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract.WatchNextPrograms
import androidx.core.content.ContextCompat
import java.net.URISyntaxException

/**
 * Un contenu de la liste « Watch Next » d'Android TV, où Disney+, HBO Max, Twitch et d'autres déposent ce qui est en cours.
 * [next] : pas commencé, mais c'est la suite de ce qu'on regarde. La liste est celle du stick, pas celle d'un profil.
 */
data class WatchNextProgram(
    val id: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val episodeTitle: String? = null,
    val season: String? = null,
    val episode: String? = null,
    val image: String? = null,
    val intentUri: String? = null,
    val lastEngagement: Long = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val programType: Int = TYPE_MOVIE,
    val watchNextType: Int = TYPE_CONTINUE,
) {
    val next: Boolean get() = watchNextType == TYPE_NEXT
    val progress: Float get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val remainingMinutes: Int get() = ((durationMs - positionMs).coerceAtLeast(0) / 60_000).toInt()

    /** « S2 É1 · Ouroboros » ; null pour un film. */
    val episodeLine: String?
        get() = listOfNotNull(
            listOfNotNull(season?.let { "S$it" }, episode?.let { "É$it" }).joinToString(" ").ifEmpty { null },
            episodeTitle,
        ).joinToString(" · ").ifEmpty { null }

    companion object {
        const val TYPE_MOVIE = 0
        const val TYPE_CONTINUE = 0
        const val TYPE_NEXT = 1
    }
}

object WatchNextRules {

    /** L'appli Jellyfin y dépose aussi ses reprises : Nimbe a déjà les siennes, venues du serveur. */
    private const val JELLYFIN_APP = "org.jellyfin.androidtv"

    /** Chaîne, morceau, album, artiste, playlist, radio : rien qui se reprenne dans « Continuer à regarder ». */
    private val NOT_VIDEO = setOf(6, 7, 8, 9, 10, 11)

    private const val PER_APP = 4

    /**
     * Ce qu'on affiche, du plus récent au plus ancien : en cours ou à suivre (ni « nouveau » ni liste d'envies),
     * avec une durée (sans elle, c'est du direct : il n'y a rien à reprendre), sans les applis [hidden],
     * une carte par titre, et [PER_APP] par appli pour que Twitch ne remplisse pas la rangée.
     */
    fun select(
        programs: List<WatchNextProgram>,
        ownPackage: String,
        hidden: Set<String> = emptySet(),
        dismissed: Set<String> = emptySet(),
    ): List<WatchNextProgram> =
        programs
            .filterNot { isDismissed(it, dismissed) }
            .filter { it.watchNextType == WatchNextProgram.TYPE_CONTINUE || it.watchNextType == WatchNextProgram.TYPE_NEXT }
            .filter { it.durationMs > 0 && it.title.isNotBlank() && it.programType !in NOT_VIDEO }
            .filter { it.packageName != ownPackage && it.packageName != JELLYFIN_APP && it.packageName !in hidden }
            .sortedByDescending { it.lastEngagement }
            .distinctBy { it.packageName to it.title }
            .groupBy { it.packageName }
            .values
            .flatMap { it.take(PER_APP) }
            .sortedByDescending { it.lastEngagement }

    /**
     * Les applis seules peuvent effacer leurs lignes : Nimbe note ce qu'on a retiré, avec la date de la dernière activité.
     * Le titre revient de lui-même si on le reprend dans l'appli (l'activité est alors plus récente).
     */
    fun dismissEntry(program: WatchNextProgram): String = "${program.lastEngagement}|${program.packageName}|${program.title}"

    /** Remplace une ancienne note du même titre : la liste ne grandit pas à chaque retrait. */
    fun dismiss(entries: Set<String>, program: WatchNextProgram): Set<String> =
        entries.filterNot { it.substringAfter('|') == "${program.packageName}|${program.title}" }.toSet() + dismissEntry(program)

    fun isDismissed(program: WatchNextProgram, entries: Set<String>): Boolean = entries.any { entry ->
        entry.substringAfter('|') == "${program.packageName}|${program.title}" &&
            (entry.substringBefore('|').toLongOrNull() ?: 0) >= program.lastEngagement
    }
}

/** Lit Watch Next et ouvre ses contenus. Tout est local : ça marche serveurs coupés. */
class WatchNextReader(private val context: Context) {

    fun allowed(): Boolean = ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Sans la permission, le système répond par une liste vide : ni erreur, ni rangée, juste Jellyfin. */
    fun read(): List<WatchNextProgram> = try {
        context.contentResolver.query(WatchNextPrograms.CONTENT_URI, null, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.int(WatchNextPrograms.COLUMN_BROWSABLE) != 0) add(program(cursor))
                }
            }
        }.orEmpty()
    } catch (_: SecurityException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    private fun program(cursor: Cursor): WatchNextProgram {
        val packageName = cursor.text(WatchNextPrograms.COLUMN_PACKAGE_NAME).orEmpty()
        return WatchNextProgram(
            id = cursor.long(WatchNextPrograms._ID),
            packageName = packageName,
            appLabel = label(packageName),
            title = cursor.text(WatchNextPrograms.COLUMN_TITLE).orEmpty(),
            episodeTitle = cursor.text(WatchNextPrograms.COLUMN_EPISODE_TITLE),
            season = cursor.text(WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER),
            episode = cursor.text(WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER),
            image = cursor.text(WatchNextPrograms.COLUMN_THUMBNAIL_URI) ?: cursor.text(WatchNextPrograms.COLUMN_POSTER_ART_URI),
            intentUri = cursor.text(WatchNextPrograms.COLUMN_INTENT_URI),
            lastEngagement = cursor.long(WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS),
            positionMs = cursor.long(WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS),
            durationMs = cursor.long(WatchNextPrograms.COLUMN_DURATION_MILLIS),
            programType = cursor.int(WatchNextPrograms.COLUMN_TYPE),
            watchNextType = cursor.int(WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE),
        )
    }

    private fun label(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /**
     * Ouvre le contenu dans son appli. N'importe quelle appli peut écrire dans cette liste : le lien ne peut viser
     * que l'appli qui l'a déposé, sans droit transmis. S'il est refusé, on ouvre l'appli sur son accueil.
     */
    fun open(program: WatchNextProgram): Boolean {
        val pkg = program.packageName
        val content = program.intentUri?.let { uri ->
            try {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                    selector = null
                    component = component?.takeIf { it.packageName == pkg }
                    setPackage(pkg)
                }
            } catch (_: URISyntaxException) {
                null
            }
        }
        val pm = context.packageManager
        return listOfNotNull(content, pm.getLeanbackLaunchIntentForPackage(pkg), pm.getLaunchIntentForPackage(pkg)).any(::start)
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun Cursor.index(column: String) = getColumnIndex(column)
    private fun Cursor.text(column: String): String? = index(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)?.ifBlank { null }
    private fun Cursor.long(column: String): Long = index(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong) ?: 0
    private fun Cursor.int(column: String): Int = index(column).takeIf { it >= 0 && !isNull(it) }?.let(::getInt) ?: 0

    companion object {
        /** Accordée à l'écran par Android (ou par adb) ; c'est elle qui ouvre la liste de toutes les applis, vérifié sur le stick. */
        const val PERMISSION = "android.permission.READ_TV_LISTINGS"
    }
}
