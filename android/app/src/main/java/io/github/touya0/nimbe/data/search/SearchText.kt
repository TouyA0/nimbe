package io.github.touya0.nimbe.data.search

import java.text.Normalizer

/** Comparaison de textes de la recherche : sans accents ni majuscules, pour une saisie à la télécommande. */
object SearchText {

    const val EXACT = 3
    const val WORD_START = 2
    const val CONTAINS = 1
    const val NONE = 0

    private val MARKS = Regex("\\p{Mn}+")
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    /** Mots qu'on dit à la dictée sans vouloir les chercher : « ouvre YouTube » cherche « YouTube ». */
    private val SPOKEN_PREFIX = Regex("^(ouvre|ouvrir|lance|lancer|mets|mettre|reprends|reprendre|cherche|chercher|photos? de|vidéos? de)\\s+", RegexOption.IGNORE_CASE)

    fun normalize(text: String): String =
        MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().trim()

    private fun words(text: String): List<String> = normalize(text).split(SEPARATORS).filter { it.isNotEmpty() }

    /** Classement : texte identique, puis début d'un mot, puis tous les mots présents quelque part ; sinon [NONE]. */
    fun score(text: String, query: String): Int {
        val wanted = words(query)
        if (wanted.isEmpty()) return NONE
        val found = words(text)
        val joined = found.joinToString(" ")
        val phrase = wanted.joinToString(" ")
        return when {
            joined == phrase -> EXACT
            joined.startsWith(phrase) || joined.contains(" $phrase") -> WORD_START
            wanted.all { joined.contains(it) } -> CONTAINS
            else -> NONE
        }
    }

    /** Ce qu'il reste à chercher d'une phrase dictée. */
    fun cleanSpoken(text: String): String = SPOKEN_PREFIX.replace(text.trim(), "").trim()

    /** Position du texte cherché dans [text], pour le colorer ; null s'il n'y est pas tel quel. */
    fun highlight(text: String, query: String): IntRange? {
        val plain = normalize(text)
        // La position n'est valable que si retirer les accents n'a pas changé la longueur.
        if (plain.length != text.trim().length) return null
        val needle = normalize(query)
        if (needle.isEmpty()) return null
        val start = plain.indexOf(needle)
        if (start < 0) return null
        val offset = text.length - text.trimStart().length
        return (start + offset) until (start + offset + needle.length)
    }
}
