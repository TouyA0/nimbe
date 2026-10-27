package io.github.touya0.nimbe.data.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CacheKeyTest {

    private val auth = JellyfinAuth(server = "http://192.168.1.182:8097", userId = "u1", token = "SECRET")

    @Test
    fun `même requête, même clé, quel que soit l'ordre des paramètres`() {
        val a = JellyfinLibrary.cacheKey(auth, "/Items", mapOf("limit" to 48, "sortBy" to "SortName", "filters" to null))
        val b = JellyfinLibrary.cacheKey(auth, "/Items", mapOf("sortBy" to "SortName", "limit" to 48))
        assertEquals("http://192.168.1.182:8097|u1|/Items?limit=48&sortBy=SortName", a)
        assertEquals(a, b)
    }

    @Test
    fun `un autre compte n'a pas la même clé, et le jeton n'y figure jamais`() {
        val mine = JellyfinLibrary.cacheKey(auth, "/Items/Latest", emptyMap())
        val other = JellyfinLibrary.cacheKey(auth.copy(userId = "u2"), "/Items/Latest", emptyMap())
        assertFalse(mine == other)
        assertFalse(mine.contains("SECRET"))
    }
}
