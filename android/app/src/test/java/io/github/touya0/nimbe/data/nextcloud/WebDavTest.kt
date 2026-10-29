package io.github.touya0.nimbe.data.nextcloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavTest {

    /** Réponse type de Nextcloud 33 : une photo avec EXIF, une vidéo sans, un dossier caché, un 404 sur une propriété. */
    private val response = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
          <d:response>
            <d:href>/remote.php/dav/files/quentin/Photos/Bretagne%202025/IMG+0042%C3%A9.jpg</d:href>
            <d:propstat>
              <d:prop>
                <oc:fileid>4242</oc:fileid>
                <d:getcontenttype>image/jpeg</d:getcontenttype>
                <d:getlastmodified>Wed, 29 Oct 2025 10:00:00 GMT</d:getlastmodified>
                <oc:size>3145728</oc:size>
                <nc:has-preview>true</nc:has-preview>
                <nc:metadata-photos-original_date_time>1760812920</nc:metadata-photos-original_date_time>
                <nc:metadata-photos-size>{"width":4032,"height":3024}</nc:metadata-photos-size>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
          <d:response>
            <d:href>/remote.php/dav/files/quentin/Vid%C3%A9os/rando.mp4</d:href>
            <d:propstat>
              <d:prop>
                <oc:fileid>99</oc:fileid>
                <d:getcontenttype>video/mp4</d:getcontenttype>
                <d:getlastmodified>Sat, 18 Oct 2025 17:42:00 GMT</d:getlastmodified>
                <oc:size>12</oc:size>
                <nc:has-preview>false</nc:has-preview>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
            <d:propstat>
              <d:prop>
                <nc:metadata-photos-original_date_time/>
                <nc:metadata-photos-size/>
              </d:prop>
              <d:status>HTTP/1.1 404 Not Found</d:status>
            </d:propstat>
          </d:response>
          <d:response>
            <d:href>/remote.php/dav/files/quentin/.thumbnails/x.png</d:href>
            <d:propstat>
              <d:prop><oc:fileid>7</oc:fileid><d:getcontenttype>image/png</d:getcontenttype></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `photos et vidéos lues, dossiers cachés écartés`() {
        var read = 0
        val files = WebDav.parseSearch(response.byteInputStream()) { read = it }
        assertEquals(3, read)
        assertEquals(2, files.size)

        val photo = files[0]
        assertEquals(4242L, photo.fileId)
        assertEquals("Photos/Bretagne 2025/IMG+0042é.jpg", photo.path)
        assertEquals(1760812920_000L, photo.takenAt)
        assertEquals(4032 to 3024, photo.width to photo.height)
        assertTrue(photo.hasPreview)
        assertFalse(photo.isVideo)

        val video = files[1]
        assertEquals("Vidéos/rando.mp4", video.path)
        assertTrue(video.isVideo)
        // Sans date EXIF : la date de modification.
        assertEquals(java.time.Instant.parse("2025-10-18T17:42:00Z").toEpochMilli(), video.takenAt)
        assertEquals(0 to 0, video.width to video.height)
    }

    @Test
    fun `la requête vise les fichiers de l'utilisateur, par pages`() {
        val body = WebDav.searchBody("anaïs & co", folder = null, offset = 500, limit = 500)
        assertTrue("<d:href>/files/anaïs &amp; co</d:href>" in body)
        assertTrue("<ns:firstresult>500</ns:firstresult>" in body)
        assertTrue("<d:nresults>500</d:nresults>" in body)
    }

    @Test
    fun `chemins décodés sans transformer les plus en espaces`() {
        assertEquals("a b/c+d.jpg", WebDav.relativePath("/remote.php/dav/files/u/a%20b/c+d.jpg"))
        assertNull(WebDav.relativePath("/remote.php/dav/files/u/"))
        assertNull(WebDav.relativePath("/ailleurs/x.jpg"))
    }
}
