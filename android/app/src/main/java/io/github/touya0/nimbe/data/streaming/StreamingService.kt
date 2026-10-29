package io.github.touya0.nimbe.data.streaming

/**
 * Plateforme de streaming que Nimbe sait afficher et ouvrir. [code] : nom court chez JustWatch,
 * [packageName] : appli Android TV qui reçoit les liens. Les liens de lecture de Disney+ et HBO Max
 * ont été vérifiés sur le stick le 29/10 ; les autres suivent le même principe (liens https de la plateforme).
 * [pageLinks] : l'appli ouvre aussi la fiche d'un titre par lien. Vrai pour Disney+ ; HBO Max ouvre sa page
 * mais affiche « Contenu indisponible » par-dessus ; les autres ne sont pas vérifiées.
 */
enum class StreamingService(
    val code: String,
    val label: String,
    val badge: String,
    val packageName: String,
    /** Couleur de la marque (ARGB), pour les badges ; le texte reste en Lune, contraste supérieur à 4,5:1. */
    val color: Long,
    val pageLinks: Boolean = false,
) {
    Netflix("nfx", "Netflix", "NETFLIX", "com.netflix.ninja", 0xFFB20710),
    Disney("dnp", "Disney+", "DISNEY+", "com.disney.disneyplus", 0xFF1A3C8F, pageLinks = true),
    HboMax("mxx", "HBO Max", "HBO MAX", "com.wbd.stream", 0xFF4A1FB8),
    Prime("prv", "Prime Video", "PRIME", "com.amazon.amazonvideo.livingroom", 0xFF0F6FA8),
    AppleTv("atp", "Apple TV+", "APPLE TV+", "com.apple.atve.androidtv.appletv", 0xFF3A3F47),
    ;

    companion object {
        fun fromCode(code: String?): StreamingService? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Catégories communes aux deux sources : [code] chez JustWatch, [jellyfin] les genres Jellyfin
 * correspondants (métadonnées TMDB en français ou en anglais selon la bibliothèque).
 */
enum class Category(val code: String, val label: String, val jellyfin: List<String>) {
    Action("act", "Action", listOf("Action", "Aventure", "Adventure", "Action & Adventure")),
    Animation("ani", "Animation", listOf("Animation")),
    Comedy("cmy", "Comédie", listOf("Comédie", "Comedy")),
    Drama("drm", "Drame", listOf("Drame", "Drama")),
    Family("fml", "Famille", listOf("Familial", "Family", "Kids", "Enfants")),
    Fantasy("fnt", "Fantastique", listOf("Fantastique", "Fantasy", "Sci-Fi & Fantasy")),
    SciFi("scf", "Science-fiction", listOf("Science-Fiction", "Science Fiction", "Sci-Fi & Fantasy")),
    Thriller("trl", "Thriller", listOf("Thriller", "Mystère", "Mystery")),
    Crime("crm", "Policier", listOf("Crime")),
    Horror("hrr", "Horreur", listOf("Horreur", "Horror")),
    Romance("rma", "Romance", listOf("Romance")),
    Documentary("doc", "Documentaire", listOf("Documentaire", "Documentary")),
    History("hst", "Histoire", listOf("Histoire", "History", "Guerre", "War", "War & Politics")),
    ;

    companion object {
        fun fromCode(code: String?): Category? = entries.firstOrNull { it.code == code }
    }
}

/** Films ou séries : les deux onglets partagent tout le reste. */
enum class TitleKind(val justWatch: String) { Movie("MOVIE"), Show("SHOW") }

/** Tri d'une plateforme. [Trending] sert à la rangée « Tendances » ; les autres sont proposés dans les grilles. */
enum class StreamingSort(val label: String, val justWatch: String) {
    Popular("Populaires", "POPULAR"),
    Recent("Nouveautés", "RELEASE_YEAR"),
    Alphabetical("A–Z", "ALPHABETICAL"),
    Trending("Tendances", "TRENDING"),
    ;

    companion object {
        val pills = listOf(Popular, Recent, Alphabetical)
    }
}
