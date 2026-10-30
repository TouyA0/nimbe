package io.github.touya0.nimbe.data.nextcloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FileNameDateTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val now = LocalDateTime.of(2026, 10, 29, 10, 0).atZone(paris).toInstant().toEpochMilli()
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(paris).toInstant().toEpochMilli()
    private fun parse(name: String) = FileNameDate.parse(name, now, paris)

    @Test
    fun `noms des téléphones et des applis courantes`() {
        assertEquals(at(2022, 7, 30, 19, 6, 14), parse("IMG_20220730_190614.jpg"))
        assertEquals(at(2024, 1, 2, 10, 15, 0), parse("PXL_20240102_101500123.jpg"))
        assertEquals(at(2022, 7, 30, 12, 0, 0), parse("IMG-20220730-WA0012.jpg"))
        assertEquals(at(2019, 7, 14, 12, 0, 0), parse("2019-07-14_famille_12.jpg"))
        assertEquals(at(2023, 3, 5, 8, 9, 10), parse("Screenshot_2023-03-05-08-09-10.png"))
    }

    @Test
    fun `pas de date inventée`() {
        assertNull(parse("123456789_1234567890123456_1234567890123456789_n.jpg"))
        assertNull(parse("IMG_0042.jpg"))
        assertNull(parse("IMG_20230231_101010.jpg"))
        assertNull(parse("IMG_20271201_101010.jpg"))
    }

    private fun at0(y: Int, mo: Int, d: Int) = LocalDateTime.of(y, mo, d, 0, 0).atZone(paris).toInstant().toEpochMilli()

    @Test
    fun `date des dossiers, du plus proche au plus lointain`() {
        // Date complète dans le dossier : le jour.
        assertEquals(
            NameDate(at(2015, 10, 2, 12, 0, 0), DatePrecision.Day),
            FileNameDate.fromPath("Souvenirs/02 - Enfance ( 2006 - 2018 )/2015-10-02 Jeux enfants Lavernose/DSC_0102.JPG", now, paris),
        )
        // Le nom du fichier passe avant les dossiers, même s'il n'a que le mois.
        assertEquals(
            NameDate(at0(2017, 5, 1), DatePrecision.Month),
            FileNameDate.fromPath("Souvenirs/2015-10-02 Jeux/2017-05_video_gouaux_9.VOB", now, paris),
        )
        // Une année seule dans un dossier, mais pas une plage d'années.
        assertEquals(NameDate(at0(2012, 1, 1), DatePrecision.Year), FileNameDate.fromPath("Souvenirs/Noël 2012/DSC_1.JPG", now, paris))
        assertNull(FileNameDate.fromPath("Souvenirs/02 - Enfance ( 2006 - 2018 )/A trier/DSC_1.JPG", now, paris))
        assertNull(FileNameDate.fromPath("Souvenirs/1984-2019 École de Gratens/DSC_1.JPG", now, paris))
        // Un compteur de quatre chiffres dans un nom de fichier n'est pas une année.
        assertNull(FileNameDate.fromPath("Photos/IMG_2015.JPG", now, paris))
    }
}
