package io.github.touya0.nimbe.update

import java.io.File
import java.security.MessageDigest

object Sha256 {
    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    fun of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun isValidHex(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

/** Les étapes d'une mise à jour, dans l'ordre où elles s'enchaînent. */
enum class UpdateStep(val label: String) {
    Found("Version trouvée"),
    Download("Téléchargement"),
    Sha256("Empreinte SHA-256"),
    Signature("Même signature"),
    Version("Version plus récente"),
    Install("Installation"),
}

/**
 * Rapport d'une tentative ratée, pour l'écran « n'a pas été installée ».
 * Les étapes avant [failedAt] ont réussi, celles d'après n'ont pas eu lieu.
 */
data class UpdateFailure(val versionName: String, val failedAt: UpdateStep, val reason: String, val at: Long) {
    fun encode(): String = listOf(versionName, failedAt.name, at.toString(), reason).joinToString("\n")

    companion object {
        fun decode(raw: String?): UpdateFailure? {
            val parts = raw?.split("\n", limit = 4) ?: return null
            if (parts.size < 4) return null
            val step = UpdateStep.entries.firstOrNull { it.name == parts[1] } ?: return null
            return UpdateFailure(parts[0], step, parts[3], parts[2].toLongOrNull() ?: 0L)
        }
    }
}

/** Échec d'une étape, avec un message en français prêt à afficher. */
class UpdateException(val step: UpdateStep, message: String, cause: Throwable? = null) : Exception(message, cause)

object UpdateRules {
    /**
     * Mêmes certificats de signature que la version installée. Comparés par leur empreinte ;
     * un ensemble vide ne correspond jamais (APK non signé ou illisible).
     */
    fun sameSigners(installed: Set<String>, candidate: Set<String>): Boolean =
        installed.isNotEmpty() && installed == candidate

    /** Après « Plus tard », on ne repropose pas avant ce délai. */
    const val SNOOZE_MS = 12 * 60 * 60 * 1000L
}
