package io.github.touya0.nimbe.feature.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class DockTest {

    private fun app(pkg: String, label: String = pkg) =
        InstalledApp(packageName = pkg, label = label, category = AppCategory.Other, icon = null, removable = true)

    private val youtube = app("com.google.android.youtube.tv", "YouTube")
    private val netflix = app("com.netflix.ninja", "Netflix")
    private val apps = listOf(app("a.alpha", "Alpha"), app("b.beta", "Beta"), netflix, youtube) +
        (1..6).map { app("z.app$it", "Z $it") }

    private fun names(list: List<InstalledApp>) = list.map { it.packageName }

    @Test
    fun `par défaut, les applis préférées d'abord puis l'ordre alphabétique`() {
        val dock = Dock.resolve(saved = null, apps = apps, hidden = emptySet())
        assertEquals(Dock.DEFAULT_SIZE, dock.size)
        assertEquals(listOf(youtube.packageName, netflix.packageName, "a.alpha", "b.beta"), names(dock).take(4))
    }

    @Test
    fun `un dock enregistré garde son ordre et ignore les applis désinstallées`() {
        val dock = Dock.resolve(saved = listOf("b.beta", "plus.installee", "a.alpha"), apps = apps, hidden = emptySet())
        assertEquals(listOf("b.beta", "a.alpha"), names(dock))
    }

    @Test
    fun `un dock vidé reste vide`() {
        assertEquals(emptyList<String>(), names(Dock.resolve(saved = emptyList(), apps = apps, hidden = emptySet())))
    }

    @Test
    fun `une appli masquée n'apparaît jamais, même par défaut`() {
        val dock = Dock.resolve(saved = null, apps = apps, hidden = setOf(youtube.packageName))
        assertEquals(netflix.packageName, dock.first().packageName)
        val saved = Dock.resolve(saved = listOf(youtube.packageName, "a.alpha"), apps = apps, hidden = setOf(youtube.packageName))
        assertEquals(listOf("a.alpha"), names(saved))
    }

    @Test
    fun `ajouter, retirer, sans doublon`() {
        assertEquals(listOf("a", "b"), Dock.add(listOf("a"), "b"))
        assertEquals(listOf("a", "b"), Dock.add(listOf("a", "b"), "a"))
        assertEquals(listOf("b"), Dock.remove(listOf("a", "b"), "a"))
    }

    @Test
    fun `déplacer d'une case, sans effet au bord`() {
        val dock = listOf("a", "b", "c")
        assertEquals(listOf("a", "c", "b"), Dock.move(dock, "c", -1))
        assertEquals(listOf("b", "a", "c"), Dock.move(dock, "a", 1))
        assertEquals(dock, Dock.move(dock, "a", -1))
        assertEquals(dock, Dock.move(dock, "c", 1))
        assertEquals(dock, Dock.move(dock, "absent", 1))
    }
}
