package io.github.touya0.nimbe.data.nextcloud

import org.junit.Assert.assertEquals
import org.junit.Test

class NextcloudFoldersTest {

    @Test
    fun `un dossier parent couvre ses sous-dossiers`() {
        assertEquals(
            listOf("Fichiers Quentin/Gallerie", "Photos"),
            NextcloudFolders.normalize(listOf("/Photos/2024/", "Photos", "Fichiers Quentin/Gallerie", "Photos", "")),
        )
        assertEquals(listOf("Photos", "Photos 2"), NextcloudFolders.normalize(listOf("Photos 2", "Photos")))
    }

    @Test
    fun `résumé des dossiers`() {
        assertEquals("Tout le compte", NextcloudFolders.label(emptyList()))
        assertEquals("Gallerie", NextcloudFolders.label(listOf("Fichiers Quentin/Gallerie")))
        assertEquals("2 dossiers", NextcloudFolders.label(listOf("A", "B")))
    }

    @Test
    fun `les dossiers survivent à l'enregistrement du lien`() {
        val link = NextcloudLink("https://cloud.example.fr", "q", "q", "Q", listOf("Photos"))
        assertEquals(link, NextcloudLinkCodec.decode(NextcloudLinkCodec.encode(link)))
        // Un lien enregistré avant les dossiers montre tout le compte.
        assertEquals(emptyList<String>(), NextcloudLinkCodec.decode("""{"server":"s","login":"l","user":"u"}""")?.folders)
    }

    @Test
    fun `parcourir un dossier`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/remote.php/dav/files/quentin/Fichiers%20Quentin/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/files/quentin/Fichiers%20Quentin/Gallerie/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/files/quentin/Fichiers%20Quentin/documents/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/files/quentin/Fichiers%20Quentin/.cache/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/files/quentin/Fichiers%20Quentin/notes.txt</d:href>
                <d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
        assertEquals(
            listOf("Fichiers Quentin/documents", "Fichiers Quentin/Gallerie"),
            WebDav.parseFolders(xml.byteInputStream(), "Fichiers Quentin"),
        )
        assertEquals("/files/quentin/Fichiers%20Quentin/Gallerie", WebDav.filesPath("quentin", "Fichiers Quentin/Gallerie"))
    }
}
