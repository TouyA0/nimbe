package io.github.touya0.nimbe.data.nextcloud

import org.json.JSONException
import org.json.JSONObject
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.xml.parsers.SAXParserFactory

/**
 * Une photo ou une vidéo du compte. [path] : chemin depuis la racine des fichiers de l'utilisateur,
 * décodé (« Photos/2025/Bretagne/IMG_0042.jpg »). [takenAt] : date de prise de vue si le serveur la
 * connaît (métadonnées EXIF lues par Nextcloud), sinon celle du nom de fichier, sinon date de
 * modification (souvent celle de l'envoi sur le serveur) ; en millisecondes.
 */
data class GalleryFile(
    val fileId: Long,
    val path: String,
    val mime: String,
    val takenAt: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val hasPreview: Boolean,
) {
    val isVideo: Boolean get() = mime.startsWith("video/")
}

/**
 * Requête WebDAV SEARCH des photos et vidéos d'un compte, et lecture de la réponse, sans Android
 * (testées sur le PC). La réponse est lue au fil de l'eau : quelques milliers d'éléments ne
 * passent jamais entiers en mémoire sous forme de texte.
 */
object WebDav {

    /**
     * Les images et vidéos sous /files/<utilisateur>, ou seulement sous [folder] (chemin depuis la racine
     * du compte), des plus récentes aux plus anciennes, par pages.
     */
    fun searchBody(userId: String, folder: String?, offset: Int, limit: Int): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <d:searchrequest xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns" xmlns:ns="https://github.com/icewind1991/SearchDAV/ns">
          <d:basicsearch>
            <d:select><d:prop>
              <oc:fileid/><d:getcontenttype/><d:getlastmodified/><oc:size/><nc:has-preview/>
              <nc:metadata-photos-original_date_time/><nc:metadata-photos-size/>
            </d:prop></d:select>
            <d:from><d:scope><d:href>${escapeXml(filesPath(userId, folder, encoded = false))}</d:href><d:depth>infinity</d:depth></d:scope></d:from>
            <d:where><d:or>
              <d:like><d:prop><d:getcontenttype/></d:prop><d:literal>image/%</d:literal></d:like>
              <d:like><d:prop><d:getcontenttype/></d:prop><d:literal>video/%</d:literal></d:like>
            </d:or></d:where>
            <d:orderby><d:order><d:prop><d:getlastmodified/></d:prop><d:descending/></d:order></d:orderby>
            <d:limit><d:nresults>$limit</d:nresults><ns:firstresult>$offset</ns:firstresult></d:limit>
          </d:basicsearch>
        </d:searchrequest>
    """.trimIndent()

    /** Les dossiers directement dans un dossier : PROPFIND de profondeur 1, type de ressource seulement. */
    const val FOLDERS_BODY = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""

    /**
     * « /files/quentin/Fichiers%20Quentin/Gallerie », chaque morceau encodé pour une adresse.
     * Sans [encoded], le chemin brut : c'est ce qu'attend Nextcloud dans la portée d'un SEARCH
     * (il ne décode pas les « %20 » à cet endroit et répond 404).
     */
    fun filesPath(userId: String, folder: String?, encoded: Boolean = true): String =
        (listOf("files", userId) + folder.orEmpty().split('/').filter { it.isNotEmpty() })
            .joinToString("/", prefix = "/") { if (encoded) encodeSegment(it) else it }

    /**
     * Sous-dossiers d'une réponse PROPFIND, chemins depuis la racine du compte, triés sans tenir compte
     * de la casse. Le dossier demandé lui-même et les dossiers cachés sont écartés.
     */
    fun parseFolders(input: InputStream, parent: String?): List<String> {
        val handler = FolderHandler()
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        factory.newSAXParser().parse(InputSource(input), handler)
        val self = parent.orEmpty().trim('/')
        return handler.folders
            .filter { it != self && !it.substringAfterLast('/').startsWith(".") }
            .distinct()
            .sortedBy { it.lowercase() }
    }

    /**
     * Les éléments d'une réponse multistatus. Écartés : les dossiers cachés (« .Trash », « .thumbnails »),
     * les éléments sans identifiant ou sans type, et ce qui n'est ni image ni vidéo.
     * [count] reçoit le nombre de réponses lues, écartées comprises, pour savoir s'il reste une page.
     */
    fun parseSearch(input: InputStream, count: (Int) -> Unit = {}): List<GalleryFile> {
        val handler = SearchHandler()
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        factory.newSAXParser().parse(InputSource(input), handler)
        count(handler.responses)
        return handler.files
    }

    /** Chemin depuis la racine de l'utilisateur, décodé ; null si l'adresse ne mène pas à ses fichiers. */
    internal fun relativePath(href: String): String? {
        val decoded = percentDecode(href)
        val marker = "/files/"
        val start = decoded.indexOf(marker).takeIf { it >= 0 } ?: return null
        val afterUser = decoded.indexOf('/', start + marker.length).takeIf { it >= 0 } ?: return null
        return decoded.substring(afterUser + 1).trimEnd('/').takeIf { it.isNotEmpty() }
    }

    /** Comme [relativePath], mais la racine du compte donne "" (au lieu de null). */
    private fun folderPath(href: String): String? {
        val decoded = percentDecode(href)
        val start = decoded.indexOf("/files/").takeIf { it >= 0 } ?: return null
        val afterUser = decoded.indexOf('/', start + "/files/".length)
        return if (afterUser < 0) "" else decoded.substring(afterUser + 1).trimEnd('/')
    }

    private class FolderHandler : DefaultHandler() {
        val folders = mutableListOf<String>()
        private val text = StringBuilder()
        private var href: String? = null
        private var collection = false

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            text.setLength(0)
            if (uri == DAV && localName == "response") {
                href = null
                collection = false
            }
            if (uri == DAV && localName == "collection") collection = true
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            when {
                uri == DAV && localName == "href" -> href = text.toString().trim()
                uri == DAV && localName == "response" && collection -> href?.let(::folderPath)?.let(folders::add)
            }
            text.setLength(0)
        }
    }

    /** Décodage des « %xx » d'un chemin, en UTF-8. Un « + » reste un « + » (pas un espace, comme dans un formulaire). */
    internal fun percentDecode(text: String): String {
        if ('%' !in text) return text
        val out = ByteArrayOutputStream(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val hex = if (c == '%' && i + 2 < text.length) text.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) {
                out.write(hex)
                i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    internal fun encodeSegment(segment: String): String = buildString {
        segment.toByteArray(Charsets.UTF_8).forEach { byte ->
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-._~@") append(c) else append("%%%02X".format(byte.toInt() and 0xFF))
        }
    }

    private fun escapeXml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private class SearchHandler : DefaultHandler() {
        val files = mutableListOf<GalleryFile>()
        var responses = 0

        private val text = StringBuilder()
        private var props = mutableMapOf<String, String>()

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            text.setLength(0)
            if (uri == DAV && localName == "response") props = mutableMapOf()
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            if (uri == DAV && localName == "response") {
                responses++
                toFile(props)?.let(files::add)
                return
            }
            val value = text.toString().trim()
            text.setLength(0)
            // Un élément vide (propriété absente, propstat en 404) n'écrase jamais une valeur lue.
            if (value.isNotEmpty() && localName in WANTED) props[localName] = value
        }

        private fun toFile(p: Map<String, String>): GalleryFile? {
            val path = relativePath(p["href"] ?: return null) ?: return null
            if (path.split('/').any { it.startsWith(".") }) return null
            val mime = p["getcontenttype"]?.substringBefore(';')?.trim()?.lowercase() ?: return null
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) return null
            val fileId = p["fileid"]?.toLongOrNull() ?: return null
            val modified = p["getlastmodified"]?.let(::httpDate) ?: 0L
            val taken = p["metadata-photos-original_date_time"]?.toLongOrNull()?.takeIf { it > 0 }?.times(1000)
            val (width, height) = p["metadata-photos-size"]?.let(::size) ?: (0 to 0)
            return GalleryFile(
                fileId = fileId,
                path = path,
                mime = mime,
                takenAt = taken ?: FileNameDate.parse(path.substringAfterLast('/')) ?: modified,
                size = p["size"]?.toLongOrNull() ?: 0L,
                width = width,
                height = height,
                hasPreview = p["has-preview"] == "true",
            )
        }

        private fun httpDate(text: String): Long? = try {
            ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }

        /** « {"width":4032,"height":3024} » ; null si absent ou illisible. */
        private fun size(text: String): Pair<Int, Int>? = try {
            val json = JSONObject(text)
            json.optInt("width") to json.optInt("height")
        } catch (_: JSONException) {
            null
        }
    }

    private const val DAV = "DAV:"
    private val WANTED = setOf(
        "href", "fileid", "getcontenttype", "getlastmodified", "size", "has-preview",
        "metadata-photos-original_date_time", "metadata-photos-size",
    )
}
