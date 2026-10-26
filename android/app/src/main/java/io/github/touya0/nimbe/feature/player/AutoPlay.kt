package io.github.touya0.nimbe.feature.player

/**
 * Épisode suivant : la carte apparaît au début du générique (segment « Outro » de Jellyfin,
 * fourni par un plugin comme Intro Skipper), sinon 30 s avant la fin, puis lance la suite après 10 s.
 */
object NextUpRule {

    const val COUNTDOWN_SECONDS = 10
    private const val FALLBACK_BEFORE_END_MS = 30_000L

    /** Après 3 épisodes d'affilée sans toucher la télécommande, on demande « Tu regardes toujours ? ». */
    const val STILL_WATCHING_AFTER = 3

    /** Position (ms) où proposer la suite, ou null si la durée est inconnue. */
    fun showAtMs(durationMs: Long, outroStartMs: Long?): Long? {
        if (durationMs <= 0) return null
        val outro = outroStartMs?.takeIf { it in 1 until durationMs }
        return outro ?: (durationMs - FALLBACK_BEFORE_END_MS).coerceAtLeast(0)
    }

    /**
     * Épisodes vus d'affilée sans aucune touche, celui-ci compris. [previous] vient de l'épisode
     * précédent ; une seule touche pendant celui-ci remet le compte à zéro.
     */
    fun untouched(previous: Int, touchedThisEpisode: Boolean): Int = if (touchedThisEpisode) 0 else previous + 1

    fun askStillWatching(untouched: Int): Boolean = untouched >= STILL_WATCHING_AFTER
}

/** Minuteur de veille : à une heure donnée, ou à la fin du contenu en cours. */
sealed interface SleepTimer {
    data class At(val epochMs: Long, val minutes: Int) : SleepTimer
    data object EndOfItem : SleepTimer

    companion object {
        val MINUTES = listOf(15, 30, 45, 60)

        /** On prévient 60 s avant l'arrêt. */
        const val WARNING_MS = 60_000L

        fun inMinutes(minutes: Int, now: Long = System.currentTimeMillis()) = At(now + minutes * 60_000L, minutes)

        /** Temps restant (ms) d'un minuteur à heure fixe ; null pour « fin du contenu ». */
        fun remainingMs(timer: SleepTimer, now: Long = System.currentTimeMillis()): Long? =
            (timer as? At)?.let { (it.epochMs - now).coerceAtLeast(0) }
    }
}
