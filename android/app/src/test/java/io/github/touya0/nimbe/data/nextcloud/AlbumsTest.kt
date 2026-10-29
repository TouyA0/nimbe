package io.github.touya0.nimbe.data.nextcloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumsTest {

    private val albums = """
        <d:multistatus xmlns:d="DAV:" xmlns:nc="http://nextcloud.org/ns">
          <d:response><d:href>/remote.php/dav/photos/quentin/sharedalbums/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
          <d:response><d:href>/remote.php/dav/photos/quentin/sharedalbums/Famille%20(anais)/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><nc:nbItems>138</nc:nbItems><nc:last-photo>4242</nc:last-photo></d:prop></d:propstat></d:response>
          <d:response><d:href>/remote.php/dav/photos/quentin/sharedalbums/Vide%20(anais)/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><nc:nbItems>0</nc:nbItems><nc:last-photo>-1</nc:last-photo></d:prop></d:propstat></d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `albums partagés, nom, propriétaire et couverture`() {
        val parsed = WebDav.parseAlbums(albums.byteInputStream(), shared = true)
        assertEquals(2, parsed.size)
        val famille = parsed[0]
        assertEquals("Famille", famille.name)
        assertEquals("anais", famille.owner)
        assertEquals(138, famille.count)
        assertEquals(4242L, famille.coverFileId)
        assertEquals("photos/quentin/sharedalbums/Famille (anais)", famille.davPath)
        assertNull(parsed[1].coverFileId)
    }

    @Test
    fun `éléments d album, adresse d album et nom sans l identifiant`() {
        val path = WebDav.albumItemPath("/remote.php/dav/photos/quentin/albums/Bretagne%202025/42-IMG_20250718_194200.jpg")
        assertEquals("@dav/photos/quentin/albums/Bretagne 2025/42-IMG_20250718_194200.jpg", path)
        assertEquals("IMG_20250718_194200.jpg", WebDav.displayName(path!!))
        assertEquals("42-ok.jpg", WebDav.displayName("Photos/42-ok.jpg"))
        assertEquals(
            "https://c.fr/remote.php/dav/photos/quentin/albums/Bretagne%202025/42-a.mp4",
            NextcloudApi.fileUrl("https://c.fr", "quentin", "@dav/photos/quentin/albums/Bretagne 2025/42-a.mp4"),
        )
    }

    @Test
    fun `albums et contenus gardés sur la TV`() {
        val list = listOf(NextcloudAlbum("Famille", "photos/q/sharedalbums/Famille (anais)", 3, 7, "anais"), NextcloudAlbum("A", "photos/q/albums/A", 0, null))
        assertEquals(list, AlbumCodec.decodeAlbums(AlbumCodec.encodeAlbums(list)))
        val files = listOf(GalleryFile(1, "@dav/x/1-a.jpg", "image/jpeg", 5, 6, 7, 8, true))
        assertEquals(files, AlbumCodec.decodeFiles(AlbumCodec.encodeFiles(files)))
        assertNull(AlbumCodec.decodeAlbums("pas du json"))
    }
}
