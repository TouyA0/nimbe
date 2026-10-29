package io.github.touya0.nimbe.homekey

import io.github.touya0.nimbe.crashguard.StartupMode

/** Où mène un appui sur la touche Accueil. */
enum class HomeTarget {
    Nimbe,

    /** L'accueil d'origine du stick (Google TV). */
    Stock,
}

/** Règles de la touche Accueil, sans dépendance à Android pour pouvoir les tester simplement. */
object HomeKeyPolicy {

    /**
     * Nimbe ne prend la touche que s'il va bien. En Secours ou en mode sans échec,
     * elle reste à Android, qui ouvre l'accueil d'origine : on n'est jamais coincé dans un Nimbe cassé.
     */
    fun takesHomeKey(mode: StartupMode): Boolean = mode == StartupMode.Normal

    /**
     * Appui court : Nimbe. Appui long : l'accueil d'origine, la sortie de secours à la télécommande,
     * sauf sur un profil Enfant ([childActive]) : ce serait une sortie sans le PIN parent.
     * Le secours reste entier : si Nimbe va mal, [takesHomeKey] laisse la touche à Android.
     */
    fun target(heldMs: Long, longPressMs: Long, childActive: Boolean = false): HomeTarget =
        if (heldMs >= longPressMs && !childActive) HomeTarget.Stock else HomeTarget.Nimbe

    /**
     * Au démarrage du stick, Android ouvre l'accueil d'origine : Nimbe passe devant une fois par démarrage.
     * Si le service est relancé dans le même démarrage (après un plantage), on ne rouvre rien.
     */
    fun opensAtBoot(bootCount: Int?, lastOpenedBoot: Int?, mode: StartupMode): Boolean =
        takesHomeKey(mode) && bootCount != null && bootCount != lastOpenedBoot
}
