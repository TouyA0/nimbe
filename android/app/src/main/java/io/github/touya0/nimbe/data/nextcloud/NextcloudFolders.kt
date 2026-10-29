package io.github.touya0.nimbe.data.nextcloud

/** Règles des dossiers d'une galerie, sans Android (testées sur le PC). */
object NextcloudFolders {

    /**
     * Chemins sans barre au début ni à la fin, sans doublon, triés ; un dossier déjà couvert par
     * un dossier parent choisi est retiré (« Photos » couvre « Photos/2024 »).
     */
    fun normalize(folders: List<String>): List<String> {
        val clean = folders.map { it.trim('/') }.filter { it.isNotEmpty() }.distinct().sorted()
        return clean.filter { folder -> clean.none { other -> other != folder && folder.startsWith("$other/") } }
    }

    /** « Fichiers Quentin/Gallerie » → « Gallerie », et « Tout le compte » sans dossier. */
    fun label(folders: List<String>): String = when (folders.size) {
        0 -> "Tout le compte"
        1 -> folders.single().substringAfterLast('/')
        else -> "${folders.size} dossiers"
    }
}
