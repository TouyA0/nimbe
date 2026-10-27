package io.github.touya0.nimbe.core.storage

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NimbeDatabase::class.java)
}
