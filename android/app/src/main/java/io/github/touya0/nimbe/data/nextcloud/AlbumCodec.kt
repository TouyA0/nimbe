package io.github.touya0.nimbe.data.nextcloud

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Albums et contenus d'albums gardés sur la TV (table des réponses en cache), sans Android pour les tests.
 * Une réponse illisible vaut « rien en cache » : on redemandera au serveur.
 */
object AlbumCodec {

    fun encodeAlbums(albums: List<NextcloudAlbum>): String = JSONArray(
        albums.map {
            JSONObject()
                .put("name", it.name)
                .put("dav", it.davPath)
                .put("count", it.count)
                .put("cover", it.coverFileId ?: -1)
                .put("owner", it.owner ?: "")
        },
    ).toString()

    fun decodeAlbums(text: String): List<NextcloudAlbum>? = try {
        val array = JSONArray(text)
        List(array.length()) { i ->
            val json = array.getJSONObject(i)
            NextcloudAlbum(
                name = json.getString("name"),
                davPath = json.getString("dav"),
                count = json.optInt("count"),
                coverFileId = json.optLong("cover", -1).takeIf { it > 0 },
                owner = json.optString("owner").ifEmpty { null },
            )
        }
    } catch (_: JSONException) {
        null
    }

    fun encodeFiles(files: List<GalleryFile>): String = JSONArray(
        files.map {
            JSONObject()
                .put("id", it.fileId)
                .put("path", it.path)
                .put("mime", it.mime)
                .put("at", it.takenAt)
                .put("size", it.size)
                .put("w", it.width)
                .put("h", it.height)
                .put("preview", it.hasPreview)
        },
    ).toString()

    fun decodeFiles(text: String): List<GalleryFile>? = try {
        val array = JSONArray(text)
        List(array.length()) { i ->
            val json = array.getJSONObject(i)
            GalleryFile(
                fileId = json.getLong("id"),
                path = json.getString("path"),
                mime = json.getString("mime"),
                takenAt = json.getLong("at"),
                size = json.optLong("size"),
                width = json.optInt("w"),
                height = json.optInt("h"),
                hasPreview = json.optBoolean("preview"),
            )
        }
    } catch (_: JSONException) {
        null
    }
}
