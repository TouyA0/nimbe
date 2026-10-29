package io.github.touya0.nimbe.core.storage

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import io.github.touya0.nimbe.data.nextcloud.GalleryCounts
import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Sur un appareil ou l'émulateur : vrai Keystore Android et vraie base SQLite. */
@RunWith(AndroidJUnit4::class)
class StorageInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun coffreAvecLeKeystore() = runBlocking {
        val vault = SecretVault(context)
        vault.put("test.token", "valeur-de-test")
        assertEquals("valeur-de-test", vault.get("test.token"))
        vault.remove("test.token")
        assertNull(vault.get("test.token"))
    }

    @Test
    fun lancementsDApplis() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, NimbeDatabase::class.java).build()
        val dao = db.appLaunches()
        dao.recordLaunch("a", at = 1)
        dao.recordLaunch("b", at = 2)
        dao.recordLaunch("a", at = 3)

        val recent = dao.recent(limit = 10).first()
        assertEquals(listOf("a", "b"), recent.map { it.packageName })
        assertEquals(2, recent.first().launchCount)

        dao.forget("a")
        assertEquals(listOf("b"), dao.recent(limit = 10).first().map { it.packageName })
        db.close()
    }

    /** Une TV qui a déjà la version 1 de la base garde ses lancements d'applis et gagne le cache Jellyfin. */
    @Test
    fun migrationDe1Vers2() = runBlocking<Unit> {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO app_launches (packageName, lastLaunchedAt, launchCount) VALUES ('a', 5, 3)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true).close()

        val db = Room.databaseBuilder(context, NimbeDatabase::class.java, name).build()
        assertEquals(3, db.appLaunches().recent(limit = 1).first().single().launchCount)
        val cache = db.cachedResponses()
        cache.put(CachedResponse("srv|u|/Items?", "{\"Items\":[]}", savedAt = 10))
        assertEquals("{\"Items\":[]}", cache.get("srv|u|/Items?")?.json)
        cache.deleteOlderThan(11)
        assertNull(cache.get("srv|u|/Items?"))
        db.close()
        context.deleteDatabase(name)
    }

    /** Une TV en version 2 garde son cache Jellyfin et gagne l'index des galeries, remplacé d'un coup par profil. */
    @Test
    fun migrationDe2Vers3EtGalerie() = runBlocking<Unit> {
        val name = "migration-test-3.db"
        context.deleteDatabase(name)
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO cached_responses (`key`, json, savedAt) VALUES ('k', '{}', 5)")
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true).close()

        val db = Room.databaseBuilder(context, NimbeDatabase::class.java, name).build()
        assertEquals("{}", db.cachedResponses().get("k")?.json)
        val gallery = db.gallery()
        fun item(profile: String, id: Long, takenAt: Long, mime: String = "image/jpeg") =
            GalleryItem(profile, id, "Photos/$id.jpg", mime, takenAt, 1, 0, 0, true)
        gallery.replace("a", listOf(item("a", 1, 10), item("a", 2, 30, "video/mp4"), item("a", 3, 20)), syncedAt = 100)
        gallery.replace("b", listOf(item("b", 1, 10)), syncedAt = 100)
        assertEquals(listOf(2L, 3L, 1L), gallery.page("a", 0, 10).map { it.fileId })
        assertEquals(GalleryCounts(photos = 2, videos = 1), gallery.counts("a").first())
        gallery.replace("a", listOf(item("a", 3, 20)), syncedAt = 200)
        assertEquals(listOf(3L), gallery.page("a", 0, 10).map { it.fileId })
        gallery.forget("a")
        assertNull(gallery.syncedAt("a").first())
        assertEquals(1, gallery.page("b", 0, 10).size)
        db.close()
        context.deleteDatabase(name)
    }

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NimbeDatabase::class.java)
}
