package io.github.touya0.nimbe.data.layout

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Un élément d'une liste personnalisable (rangée de l'accueil, onglet), visible ou masqué. */
data class LayoutItem(val id: String, val visible: Boolean)

/** Rangées de l'accueil, dans leur ordre par défaut. Une version qui en ajoute une l'ajoute ici. */
enum class HomeRow(val key: String, val label: String) {
    Continue("continue", "Continuer à regarder"),
    Apps("apps", "Mes applis"),
    Latest("latest", "Ajouté récemment sur Jellyfin"),
    Memories("memories", "Souvenirs"),
    ;

    companion object {
        fun fromKey(key: String): HomeRow? = entries.firstOrNull { it.key == key }
    }
}

/** Identifiants des onglets, dans leur ordre par défaut. L'Accueil reste premier et visible ; Applis reste visible. */
object TabKeys {
    const val HOME = "home"
    const val APPS = "apps"
    val defaults = listOf(HOME, "tv", "movies", "series", "music", "gallery", APPS)

    /** Toujours affichés : l'Accueil (c'est l'écran du stick) et Applis (« Toutes les applis » y mène). */
    val alwaysVisible = setOf(HOME, APPS)
}

/**
 * Mise en page d'un profil : rangées de l'accueil et onglets, chacun dans l'ordre choisi, visible ou non,
 * et l'onglet affiché au démarrage. [customized] : le profil a déjà changé quelque chose.
 */
data class ProfileLayout(
    val homeRows: List<LayoutItem>,
    val tabs: List<LayoutItem>,
    val startTab: String = TabKeys.HOME,
    val customized: Boolean = false,
) {
    val visibleRows: List<HomeRow> get() = homeRows.filter { it.visible }.mapNotNull { HomeRow.fromKey(it.id) }
    val visibleTabs: List<String> get() = tabs.filter { it.visible }.map { it.id }

    companion object {
        val Default = ProfileLayout(
            homeRows = HomeRow.entries.map { LayoutItem(it.key, visible = true) },
            tabs = TabKeys.defaults.map { LayoutItem(it, visible = true) },
        )
    }
}

/**
 * Règles des mises en page : on ne peut jamais « casser » l'interface. Un identifiant inconnu est ignoré,
 * un élément ajouté par une nouvelle version arrive à sa place par défaut (masqué si le profil a déjà
 * personnalisé, pour ne rien lui imposer), l'Accueil reste premier.
 */
object LayoutRules {

    /** Rend [layout] cohérent avec les rangées et onglets que connaît cette version. */
    fun normalize(layout: ProfileLayout): ProfileLayout {
        val rows = reconcile(layout.homeRows, HomeRow.entries.map { it.key }, layout.customized)
        val tabs = reconcile(layout.tabs, TabKeys.defaults, layout.customized)
            .map { if (it.id in TabKeys.alwaysVisible) it.copy(visible = true) else it }
            .sortedBy { if (it.id == TabKeys.HOME) 0 else 1 }
        val start = layout.startTab.takeIf { id -> tabs.any { it.id == id && it.visible } } ?: TabKeys.HOME
        return layout.copy(homeRows = rows, tabs = tabs, startTab = start)
    }

    /** Garde l'ordre enregistré, sans doublons ni inconnus, et insère les nouveaux à leur place par défaut. */
    fun reconcile(saved: List<LayoutItem>, known: List<String>, customized: Boolean): List<LayoutItem> {
        val kept = saved.filter { it.id in known }.distinctBy { it.id }.toMutableList()
        known.forEachIndexed { defaultIndex, id ->
            if (kept.none { it.id == id }) {
                // Juste après l'élément qui le précède dans l'ordre par défaut, s'il est là.
                val before = known.take(defaultIndex).lastOrNull { previous -> kept.any { it.id == previous } }
                val at = before?.let { previous -> kept.indexOfFirst { it.id == previous } + 1 } ?: 0
                kept.add(at, LayoutItem(id, visible = !customized))
            }
        }
        return kept
    }

    /** Déplace d'une case ; sans effet au bord, ni pour l'Accueil, ni pour passer devant lui. */
    fun move(items: List<LayoutItem>, id: String, delta: Int): List<LayoutItem> {
        val from = items.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in items.indices || id == TabKeys.HOME || items[to].id == TabKeys.HOME) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Masque ou remet un élément. Un élément remis va à la fin des visibles, comme sur la maquette. */
    fun setVisible(items: List<LayoutItem>, id: String, visible: Boolean): List<LayoutItem> {
        val item = items.firstOrNull { it.id == id } ?: return items
        if (item.visible == visible) return items
        val rest = items.filter { it.id != id }
        if (!visible) return items.map { if (it.id == id) it.copy(visible = false) else it }
        val lastVisible = rest.indexOfLast { it.visible }
        return rest.toMutableList().apply { add(lastVisible + 1, item.copy(visible = true)) }
    }

    /** Ce qui empêche de masquer [id] parmi les onglets, ou null. */
    fun hideTabProblem(id: String): String? = when {
        id == TabKeys.HOME -> "L'Accueil reste toujours affiché"
        id == TabKeys.APPS -> "Applis reste affiché : « Toutes les applis » y mène"
        else -> null
    }
}

/** Mise en page en JSON, pour DataStore. Une entrée illisible donne la mise en page par défaut. */
object LayoutCodec {

    fun encode(layout: ProfileLayout): String = JSONObject()
        .put("version", 1)
        .put("homeRows", items(layout.homeRows))
        .put("tabs", items(layout.tabs))
        .put("startTab", layout.startTab)
        .toString()

    /** null si rien n'est enregistré : le profil n'a jamais personnalisé. */
    fun decode(text: String?): ProfileLayout? {
        if (text.isNullOrBlank()) return null
        val json = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return null
        }
        return ProfileLayout(
            homeRows = items(json.optJSONArray("homeRows")),
            tabs = items(json.optJSONArray("tabs")),
            startTab = json.optString("startTab", TabKeys.HOME),
            customized = true,
        )
    }

    private fun items(list: List<LayoutItem>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("visible", it.visible)) }
    }

    private fun items(array: JSONArray?): List<LayoutItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            LayoutItem(item.optString("id").ifEmpty { return@mapNotNull null }, item.optBoolean("visible", true))
        }
    }
}
