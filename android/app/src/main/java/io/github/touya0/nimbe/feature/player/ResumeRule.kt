package io.github.touya0.nimbe.feature.player

import io.github.touya0.nimbe.data.jellyfin.MediaItem

/** Ce que le lecteur doit ouvrir : un contenu et la position de départ, en ticks Jellyfin. */
data class PlayRequest(val item: MediaItem, val startTicks: Long)

/**
 * « Reprendre ou recommencer » n'est proposé qu'entre 2 minutes et 95 % : avant, on recommence
 * sans rien demander ; après, le contenu est quasiment fini et on repart du début.
 */
object ResumeRule {

    private const val MIN_TICKS = 2 * MediaItem.TICKS_PER_MINUTE
    private const val MAX_PROGRESS = 0.95f

    fun shouldAsk(item: MediaItem): Boolean = item.positionTicks >= MIN_TICKS && item.progress in 0f..MAX_PROGRESS

    /** Départ quand on ne demande pas : depuis le début. */
    fun direct(item: MediaItem): PlayRequest = PlayRequest(item, 0)

    fun resume(item: MediaItem): PlayRequest = PlayRequest(item, item.positionTicks)
}
