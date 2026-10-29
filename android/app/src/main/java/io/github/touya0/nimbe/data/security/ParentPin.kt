package io.github.touya0.nimbe.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Empreinte du PIN parent : PBKDF2-HMAC-SHA256 avec un sel aléatoire, jamais le PIN lui-même.
 * Format enregistré : « pbkdf2-sha256$itérations$sel$empreinte » (Base64), pour pouvoir changer
 * le nombre d'itérations plus tard sans casser les PIN déjà enregistrés.
 */
object PinHash {

    const val LENGTH = 4

    /** Assez pour ralentir une recherche exhaustive, sans faire attendre plus d'une seconde sur le stick 32 bits. */
    const val ITERATIONS = 60_000

    private const val PREFIX = "pbkdf2-sha256"
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256

    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): String {
        require(isValid(pin)) { "PIN invalide" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val encoder = Base64.getEncoder()
        return listOf(PREFIX, iterations.toString(), encoder.encodeToString(salt), encoder.encodeToString(derive(pin, salt, iterations)))
            .joinToString("$")
    }

    /** Comparaison à temps constant ; une empreinte illisible ne correspond à aucun PIN. */
    fun matches(pin: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX || !isValid(pin)) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val decoder = Base64.getDecoder()
        val (salt, expected) = try {
            decoder.decode(parts[2]) to decoder.decode(parts[3])
        } catch (_: IllegalArgumentException) {
            return false
        }
        return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/**
 * Blocage après des codes faux. [failures] : codes faux depuis le dernier bon ou le dernier blocage ;
 * [lockouts] : blocages successifs, chacun deux fois plus long que le précédent.
 */
data class PinLockout(val failures: Int = 0, val lockouts: Int = 0, val lockedUntil: Long = 0) {

    fun isLocked(now: Long): Boolean = now < lockedUntil

    fun remainingMs(now: Long): Long = (lockedUntil - now).coerceAtLeast(0)

    /** Codes faux encore permis avant le prochain blocage. */
    val triesLeft: Int get() = MAX_FAILURES - failures

    fun afterFailure(now: Long): PinLockout {
        if (failures + 1 < MAX_FAILURES) return copy(failures = failures + 1)
        val duration = (FIRST_LOCK_MS shl lockouts.coerceAtMost(MAX_DOUBLINGS)).coerceAtMost(MAX_LOCK_MS)
        return PinLockout(failures = 0, lockouts = lockouts + 1, lockedUntil = now + duration)
    }

    companion object {
        const val MAX_FAILURES = 5
        const val FIRST_LOCK_MS = 5 * 60_000L
        const val MAX_LOCK_MS = 60 * 60_000L
        private const val MAX_DOUBLINGS = 4

        /** Un bon code efface tout. */
        val Cleared = PinLockout()

        /** « 5 min », « 1 min 30 », « 45 s » : ce qu'il reste à attendre. */
        fun formatWait(ms: Long): String {
            val seconds = (ms + 999) / 1000
            val minutes = seconds / 60
            val rest = seconds % 60
            return when {
                minutes >= 60 -> "${minutes / 60} h ${"%02d".format(minutes % 60)}"
                minutes == 0L -> "$rest s"
                rest == 0L -> "$minutes min"
                else -> "$minutes min ${"%02d".format(rest)}"
            }
        }
    }
}

/** Réponse à un PIN saisi. */
sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val triesLeft: Int) : PinCheck
    data class Locked(val until: Long) : PinCheck

    /** Aucun PIN parent défini : rien à vérifier. */
    data object NotSet : PinCheck
}
