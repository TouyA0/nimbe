package io.github.touya0.nimbe.feature.apps

/** Applis du dock par défaut, dans l'ordre de la maquette, si elles sont installées. */
private val PreferredDock = listOf(
    "org.jellyfin.androidtv",
    "com.google.android.youtube.tv",
    "com.netflix.ninja",
    "com.disney.disneyplus",
    "com.spotify.tv.android",
)

/**
 * Règles de « Mes applis ». Le dock est enregistré comme une liste de noms de paquets ;
 * null veut dire « jamais personnalisé », et on propose alors un dock par défaut.
 */
object Dock {
    const val DEFAULT_SIZE = 6

    /** Les applis à afficher : installées, non masquées, dans l'ordre choisi. */
    fun resolve(saved: List<String>?, apps: List<InstalledApp>, hidden: Set<String>): List<InstalledApp> {
        val visible = apps.filterNot { it.packageName in hidden }
        if (saved == null) return defaults(visible)
        val byPackage = visible.associateBy { it.packageName }
        return saved.mapNotNull { byPackage[it] }
    }

    /** Les préférées installées, complétées par ordre alphabétique. */
    fun defaults(apps: List<InstalledApp>): List<InstalledApp> {
        val byPackage = apps.associateBy { it.packageName }
        val preferred = PreferredDock.mapNotNull { byPackage[it] }
        return (preferred + apps.filterNot { it in preferred }).take(DEFAULT_SIZE)
    }

    fun add(dock: List<String>, pkg: String): List<String> = if (pkg in dock) dock else dock + pkg

    fun remove(dock: List<String>, pkg: String): List<String> = dock - pkg

    /** Déplace d'une case ; sans effet au bord. */
    fun move(dock: List<String>, pkg: String, delta: Int): List<String> {
        val from = dock.indexOf(pkg)
        val to = from + delta
        if (from < 0 || to !in dock.indices) return dock
        return dock.toMutableList().apply { add(to, removeAt(from)) }
    }
}
