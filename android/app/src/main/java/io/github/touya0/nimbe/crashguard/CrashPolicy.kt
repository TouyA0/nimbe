package io.github.touya0.nimbe.crashguard

/** Ce que le launcher affiche au démarrage. */
enum class StartupMode {
    /** Tout va bien. */
    Normal,

    /** Trop de plantages récents : écran Secours, puis bascule vers l'accueil d'origine. */
    Rescue,

    /** Après un Secours : seuls les Applis et les options de réparation sont actifs. */
    SafeMode,
}

/** Règles du garde-fou, sans dépendance à Android pour pouvoir les tester simplement. */
object CrashPolicy {
    /** Trois plantages en deux minutes déclenchent le Secours. */
    const val THRESHOLD = 3
    const val WINDOW_MS = 120_000L

    /** Nombre d'horodatages conservés : assez pour la règle, sans grossir indéfiniment. */
    const val MAX_KEPT = 10

    fun recentCount(crashes: List<Long>, now: Long): Int = crashes.count { now - it in 0..WINDOW_MS }

    /** Le Secours passe avant le mode sans échec : si le mode sans échec plante lui aussi, on sort. */
    fun decide(crashes: List<Long>, safeMode: Boolean, now: Long): StartupMode = when {
        recentCount(crashes, now) >= THRESHOLD -> StartupMode.Rescue
        safeMode -> StartupMode.SafeMode
        else -> StartupMode.Normal
    }

    fun append(crashes: List<Long>, at: Long): List<Long> = (crashes + at).takeLast(MAX_KEPT)

    /** Stockage compact dans les préférences : « 123,456,789 ». Les valeurs illisibles sont ignorées. */
    fun encode(crashes: List<Long>): String = crashes.joinToString(",")

    fun decode(raw: String?): List<Long> = raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

    /**
     * Résumé de l'erreur, sans son message : il pourrait contenir une adresse ou un nom.
     * On garde le type d'exception et la première ligne de notre code dans la pile d'appels.
     * Android enveloppe souvent l'erreur (« Unable to start activity ») : on remonte à la cause d'origine.
     */
    fun summarize(error: Throwable, ownPackage: String): String {
        val root = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }.take(10).last()
        val frame = root.stackTrace.firstOrNull { it.className.startsWith(ownPackage) }
        val where = frame?.let { " dans ${it.className.substringAfterLast('.')}.${it.methodName}" }.orEmpty()
        return root.javaClass.simpleName + where
    }
}
