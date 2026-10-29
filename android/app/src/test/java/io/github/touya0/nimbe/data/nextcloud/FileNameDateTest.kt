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
}
