package io.github.touya0.nimbe.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UpdateRulesTest {

    private val base = "https://github.com/TouyA0/nimbe/releases/download"
    private val sha = "a".repeat(64)

    private fun release(tag: String, publishedAt: String, prerelease: Boolean = false, draft: Boolean = false) =
        ReleaseSummary(
            tag = tag,
            prerelease = prerelease,
            draft = draft,
            publishedAt = publishedAt,
            assets = mapOf("update.json" to "$base/$tag/update.json", "nimbe.apk" to "$base/$tag/nimbe.apk"),
        )

    @Test
    fun `le canal stable ignore les bêtas`() {
        val releases = listOf(
            release("v1.1.0-beta.1", "2026-11-10T10:00:00Z", prerelease = true),
            release("v1.0.0", "2026-11-01T10:00:00Z"),
        )
        assertEquals("v1.0.0", Releases.pick(releases, UpdateChannel.Stable)?.tag)
        assertEquals("v1.1.0-beta.1", Releases.pick(releases, UpdateChannel.Beta)?.tag)
    }

    @Test
    fun `les brouillons et les releases sans update json sont ignorés`() {
        val releases = listOf(
            release("v2.0.0", "2026-12-01T10:00:00Z", draft = true),
            release("v1.9.0", "2026-11-20T10:00:00Z").copy(assets = mapOf("autre.zip" to "$base/v1.9.0/autre.zip")),
            release("v1.8.0", "2026-11-10T10:00:00Z"),
        )
        assertEquals("v1.8.0", Releases.pick(releases, UpdateChannel.Stable)?.tag)
    }

    @Test
    fun `aucune release, rien à proposer`() {
        assertNull(Releases.pick(emptyList(), UpdateChannel.Beta))
    }

    @Test
    fun `lecture de la liste de l'API GitHub`() {
        val json = """
            [{"tag_name": "v1.0.0", "prerelease": false, "draft": false, "published_at": "2026-11-01T10:00:00Z",
              "assets": [{"name": "update.json", "browser_download_url": "$base/v1.0.0/update.json"}]}]
        """
        val releases = Releases.parseList(json)
        assertEquals(1, releases.size)
        assertEquals("$base/v1.0.0/update.json", releases[0].assets["update.json"])
    }

    @Test
    fun `update json se relit à l'identique`() {
        val json = """
            {"versionCode": 1000099, "versionName": "1.0.0", "channel": "stable", "apk": "nimbe-1.0.0.apk",
             "sha256": "${sha.uppercase()}", "size": 8400000, "publishedAt": "2026-11-01T10:00:00Z",
             "notes": ["Écran Mise à jour", "", "Canal bêta"]}
        """
        val manifest = UpdateManifest.parse(json, apkUrl = "$base/v1.0.0/nimbe-1.0.0.apk")
        assertEquals(1000099L, manifest.versionCode)
        assertEquals(sha, manifest.sha256)
        assertEquals(listOf("Écran Mise à jour", "Canal bêta"), manifest.notes)
        assertEquals(manifest, UpdateManifest.parse(manifest.toJson()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `une empreinte illisible est refusée`() {
        UpdateManifest.parse(
            """{"versionCode": 1, "versionName": "1.0.0", "apk": "a.apk", "sha256": "1234", "size": 1}""",
            apkUrl = "$base/v1.0.0/a.apk",
        )
    }

    @Test
    fun `seules les adresses du dépôt sont acceptées`() {
        assertTrue(Releases.isTrustedUrl("$base/v1.0.0/nimbe-1.0.0.apk"))
        assertFalse(Releases.isTrustedUrl("https://github.com/autre/nimbe/releases/download/v1.0.0/nimbe.apk"))
        assertFalse(Releases.isTrustedUrl("http://github.com/TouyA0/nimbe/releases/download/v1.0.0/nimbe.apk"))
        assertFalse(Releases.isTrustedUrl("https://github.com.evil.example/TouyA0/nimbe/releases/download/x"))
    }

    @Test
    fun `même signature seulement si les certificats sont identiques`() {
        assertTrue(UpdateRules.sameSigners(setOf("aa"), setOf("aa")))
        assertFalse(UpdateRules.sameSigners(setOf("aa"), setOf("bb")))
        assertFalse(UpdateRules.sameSigners(emptySet(), emptySet()))
    }

    @Test
    fun `le rapport d'échec se relit, même avec un message sur plusieurs lignes`() {
        val failure = UpdateFailure("1.5.0", UpdateStep.Sha256, "Fichier différent.\nSupprimé.", 42L)
        assertEquals(failure, UpdateFailure.decode(failure.encode()))
        assertNull(UpdateFailure.decode("illisible"))
        assertNull(UpdateFailure.decode(null))
    }

    @Test
    fun `empreinte SHA-256 connue`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.of("abc".toByteArray()))
    }

    @Test
    fun `la vérification est prévue à la prochaine nuit`() {
        val paris = ZoneId.of("Europe/Paris")
        val evening = ZonedDateTime.of(2026, 10, 26, 21, 30, 0, 0, paris)
        assertEquals(5 * 3_600_000L + 30 * 60_000L, UpdateScheduler.delayUntilNight(evening))
        val night = ZonedDateTime.of(2026, 10, 27, 1, 0, 0, 0, paris)
        assertEquals(2 * 3_600_000L, UpdateScheduler.delayUntilNight(night))
        val justAfter = ZonedDateTime.of(2026, 10, 27, 3, 0, 0, 0, paris)
        assertEquals(24 * 3_600_000L, UpdateScheduler.delayUntilNight(justAfter))
    }
}
