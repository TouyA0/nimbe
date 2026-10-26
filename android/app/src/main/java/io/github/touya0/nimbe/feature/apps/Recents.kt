package io.github.touya0.nimbe.feature.apps

/** « Récemment utilisées » : les dernières applis ouvertes depuis Nimbe, encore installées et non masquées. */
object Recents {
    const val SIZE = 6

    fun resolve(recentPackages: List<String>, apps: List<InstalledApp>, hidden: Set<String>): List<InstalledApp> {
        val byPackage = apps.associateBy { it.packageName }
        return recentPackages
            .filterNot { it in hidden }
            .mapNotNull { byPackage[it] }
            .take(SIZE)
    }
}
