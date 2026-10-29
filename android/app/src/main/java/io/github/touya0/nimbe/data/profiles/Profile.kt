package io.github.touya0.nimbe.data.profiles

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Type de profil : ce qu'il peut faire viendra avec les étapes Enfant et Invité. */
enum class ProfileKind(val key: String, val label: String, val summary: String) {
    Adult("adult", "Adulte", "Tout Nimbe, réglages compris"),
    Child("child", "Enfant", "Accueil simplifié, sans réglages"),
    Guest("guest", "Invité", "Rien n'est enregistré"),
    ;

    companion object {
        fun fromKey(key: String?): ProfileKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * [color] : indice dans la palette des profils, pour que l'avatar garde sa couleur quel que soit le thème.
 * [hiddenApps] : pour l'invité, les applis masquées (null : celles des comptes de la famille, par défaut).
 * [allowedApps] : pour l'enfant, les seules applis permises (null : celles pour enfants, par défaut).
 * [limits] : pour l'enfant, temps d'écran quotidien et couvre-feu.
 */
data class Profile(
    val id: String,
    val name: String,
    val color: Int,
    val kind: ProfileKind,
    val hiddenApps: Set<String>? = null,
    val allowedApps: Set<String>? = null,
    val limits: ChildLimits = ChildLimits(),
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

/** Ce que Nimbe fait quand la TV s'allume. */
enum class StartChoice(val key: String, val label: String) {
    Ask("ask", "Demander qui regarde"),
    ResumeLast("last", "Reprendre le dernier profil"),
    ;

    companion object {
        fun fromKey(key: String?): StartChoice = entries.firstOrNull { it.key == key } ?: Ask
    }
}

data class ProfilesState(val profiles: List<Profile>, val activeId: String?, val onStart: StartChoice) {
    /** Le profil choisi, sinon le premier : il y en a toujours un dès qu'un profil existe. */
    val active: Profile? get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    /** « Qui regarde ? » n'a de sens que s'il y a le choix. */
    val asksOnStart: Boolean get() = profiles.size > 1 && onStart == StartChoice.Ask
}

/** Règles des profils, vérifiées avant chaque enregistrement. Les messages s'affichent tels quels. */
object ProfileRules {

    const val MAX_NAME = 20
    const val MAX_PROFILES = 8

    /** Nombre de couleurs de la palette (ProfileColors dans le thème). */
    const val COLORS = 6

    fun cleanName(input: String): String = input.trim().replace(Regex("""\s+"""), " ")

    /**
     * Problème qui empêche d'enregistrer [draft] parmi [all] (qui contient son ancienne version s'il existe déjà),
     * ou null si tout va bien.
     */
    fun problem(draft: Profile, all: List<Profile>): String? {
        val name = cleanName(draft.name)
        val others = all.filter { it.id != draft.id }
        val previous = all.firstOrNull { it.id == draft.id }
        return when {
            name.isEmpty() -> "Donne un nom au profil"
            name.length > MAX_NAME -> "$MAX_NAME caractères au plus"
            others.any { it.name.equals(name, ignoreCase = true) } -> "Un profil s'appelle déjà « $name »"
            previous == null && others.size >= MAX_PROFILES -> "$MAX_PROFILES profils au plus"
            draft.kind == ProfileKind.Guest && others.any { it.kind == ProfileKind.Guest } -> "Il y a déjà un profil Invité"
            previous?.kind == ProfileKind.Adult && draft.kind != ProfileKind.Adult && others.none { it.kind == ProfileKind.Adult } ->
                LAST_ADULT
            else -> null
        }
    }

    /** Problème qui empêche de supprimer [profile], ou null. */
    fun deleteProblem(profile: Profile, all: List<Profile>): String? =
        if (profile.kind == ProfileKind.Adult && all.none { it.id != profile.id && it.kind == ProfileKind.Adult }) LAST_ADULT else null

    fun hasAdult(profiles: List<Profile>): Boolean = profiles.any { it.kind == ProfileKind.Adult }

    /** Première couleur libre, pour que deux profils ne se ressemblent pas. */
    fun nextColor(profiles: List<Profile>): Int =
        (0 until COLORS).firstOrNull { color -> profiles.none { it.color == color } } ?: (profiles.size % COLORS)

    /** Nom proposé pour un nouveau profil : vide pour un adulte, sinon le nom du type s'il est libre. */
    fun suggestedName(kind: ProfileKind, profiles: List<Profile>): String =
        if (kind == ProfileKind.Adult || profiles.any { it.name.equals(kind.label, ignoreCase = true) }) "" else kind.label

    private const val LAST_ADULT = "Garde au moins un profil adulte : c'est lui qui règle la TV"
}

/** Profils en JSON, pour DataStore. Une entrée illisible est ignorée, jamais bloquante. */
object ProfileCodec {

    private const val MINUTES_PER_DAY = 24 * 60

    private fun packages(array: JSONArray): Set<String> = (0 until array.length()).mapNotNull { array.optString(it).ifEmpty { null } }.toSet()

    fun encode(profiles: List<Profile>): String = JSONArray().apply {
        profiles.forEach { profile ->
            put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("color", profile.color)
                    .put("kind", profile.kind.key)
                    .apply { profile.hiddenApps?.let { put("hiddenApps", JSONArray(it.sorted())) } }
                    .apply { profile.allowedApps?.let { put("allowedApps", JSONArray(it.sorted())) } }
                    .apply { profile.limits.dailyMinutes?.let { put("dailyMinutes", it) } }
                    .apply { profile.limits.curfew?.let { put("curfewStart", it.start).put("curfewEnd", it.end) } },
            )
        }
    }.toString()

    fun decode(text: String?): List<Profile> {
        if (text.isNullOrBlank()) return emptyList()
        val array = try {
            JSONArray(text)
        } catch (_: JSONException) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            Profile(
                id = item.optString("id").ifEmpty { return@mapNotNull null },
                name = item.optString("name").ifEmpty { return@mapNotNull null },
                color = item.optInt("color", 0).mod(ProfileRules.COLORS),
                kind = ProfileKind.fromKey(item.optString("kind")) ?: return@mapNotNull null,
                hiddenApps = item.optJSONArray("hiddenApps")?.let(::packages),
                allowedApps = item.optJSONArray("allowedApps")?.let(::packages),
                limits = ChildLimits(
                    dailyMinutes = item.optInt("dailyMinutes", -1).takeIf { it > 0 },
                    curfew = if (item.has("curfewStart") && item.has("curfewEnd")) {
                        Curfew(item.optInt("curfewStart").coerceIn(0, MINUTES_PER_DAY - 1), item.optInt("curfewEnd").coerceIn(0, MINUTES_PER_DAY - 1))
                    } else {
                        null
                    },
                ),
            )
        }
    }
}
