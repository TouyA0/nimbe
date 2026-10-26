package io.github.touya0.nimbe.update

import org.json.JSONArray
import org.json.JSONObject

/** Canal suivi par ce stick. La bêta reçoit aussi les versions stables. */
enum class UpdateChannel(val key: String, val label: String) {
    Stable("stable", "Stable"),
    Beta("beta", "Bêta");

    companion object {
        fun fromKey(key: String?): UpdateChannel = entries.firstOrNull { it.key == key } ?: Stable
    }
}

/** Une release du dépôt, telle que la décrit l'API GitHub. [assets] : nom du fichier vers son adresse. */
data class ReleaseSummary(
    val tag: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val publishedAt: String,
    val assets: Map<String, String>,
)

/**
 * Le fichier update.json publié avec chaque release par le workflow release.yml,
 * complété par l'adresse de l'APK trouvée dans la release.
 */
data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val channel: UpdateChannel,
    val notes: List<String>,
    val apkName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val publishedAt: String,
) {
    fun toJson(): String = JSONObject()
        .put("versionCode", versionCode)
        .put("versionName", versionName)
        .put("channel", channel.key)
        .put("notes", JSONArray(notes))
        .put("apk", apkName)
        .put("apkUrl", apkUrl)
        .put("sha256", sha256)
        .put("size", size)
        .put("publishedAt", publishedAt)
        .toString()

    companion object {
        /** Lit update.json. [apkUrl] vient de la release ; sinon on reprend celle déjà enregistrée. */
        fun parse(json: String, apkUrl: String? = null): UpdateManifest {
            val o = JSONObject(json)
            val notes = o.optJSONArray("notes")
            val sha = o.getString("sha256").lowercase()
            require(Sha256.isValidHex(sha)) { "Empreinte SHA-256 illisible" }
            return UpdateManifest(
                versionCode = o.getLong("versionCode"),
                versionName = o.getString("versionName"),
                channel = UpdateChannel.fromKey(o.optString("channel")),
                notes = (0 until (notes?.length() ?: 0)).map { notes!!.getString(it) }.filter { it.isNotBlank() },
                apkName = o.getString("apk"),
                apkUrl = apkUrl ?: o.getString("apkUrl"),
                sha256 = sha,
                size = o.getLong("size"),
                publishedAt = o.optString("publishedAt"),
            )
        }
    }
}

/** Règles de choix d'une version, sans dépendance à Android pour pouvoir les tester simplement. */
object Releases {
    const val MANIFEST_ASSET = "update.json"

    /** Liste renvoyée par GET /repos/{dépôt}/releases. */
    fun parseList(json: String): List<ReleaseSummary> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val assets = o.optJSONArray("assets") ?: JSONArray()
            ReleaseSummary(
                tag = o.getString("tag_name"),
                prerelease = o.optBoolean("prerelease"),
                draft = o.optBoolean("draft"),
                publishedAt = o.optString("published_at"),
                assets = (0 until assets.length()).associate {
                    val asset = assets.getJSONObject(it)
                    asset.getString("name") to asset.getString("browser_download_url")
                },
            )
        }
    }

    /**
     * La dernière release publiée pour ce canal, et qui contient bien update.json.
     * Les dates ISO 8601 de GitHub se comparent comme du texte.
     */
    fun pick(releases: List<ReleaseSummary>, channel: UpdateChannel): ReleaseSummary? = releases
        .filter { !it.draft && MANIFEST_ASSET in it.assets }
        .filter { channel == UpdateChannel.Beta || !it.prerelease }
        .maxByOrNull { it.publishedAt }

    /**
     * Seules les adresses de GitHub sont acceptées : un update.json modifié ne peut pas
     * envoyer le stick télécharger ailleurs.
     */
    fun isTrustedUrl(url: String): Boolean = url.startsWith("https://github.com/$REPO/releases/download/")

    const val REPO = "TouyA0/nimbe"
}
