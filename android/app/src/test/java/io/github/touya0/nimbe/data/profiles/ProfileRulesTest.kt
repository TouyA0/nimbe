package io.github.touya0.nimbe.data.profiles

import io.github.touya0.nimbe.data.layout.HomeRow
import io.github.touya0.nimbe.data.layout.LayoutRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {

    private val quentin = Profile("q", "Quentin", 0, ProfileKind.Adult)
    private val enfant = Profile("e", "Enfant", 1, ProfileKind.Child)
    private val invite = Profile("i", "Invité", 2, ProfileKind.Guest)

    @Test
    fun `nom vide, trop long ou déjà pris`() {
        assertEquals("Donne un nom au profil", ProfileRules.problem(Profile("n", "   ", 0, ProfileKind.Adult), listOf(quentin)))
        assertNotNull(ProfileRules.problem(Profile("n", "x".repeat(21), 0, ProfileKind.Adult), listOf(quentin)))
        assertNotNull(ProfileRules.problem(Profile("n", " quentin ", 0, ProfileKind.Adult), listOf(quentin)))
        // Renommer un profil en gardant son propre nom n'est pas un doublon.
        assertNull(ProfileRules.problem(quentin.copy(color = 3), listOf(quentin, enfant)))
    }

    @Test
    fun `un seul invité`() {
        assertNotNull(ProfileRules.problem(Profile("n", "Visite", 3, ProfileKind.Guest), listOf(quentin, invite)))
        assertNull(ProfileRules.problem(invite.copy(color = 4), listOf(quentin, invite)))
    }

    @Test
    fun `le dernier adulte ne peut ni partir ni changer de type`() {
        assertNotNull(ProfileRules.problem(quentin.copy(kind = ProfileKind.Child), listOf(quentin, enfant)))
        assertNotNull(ProfileRules.deleteProblem(quentin, listOf(quentin, enfant)))
        val sophie = Profile("s", "Sophie", 3, ProfileKind.Adult)
        assertNull(ProfileRules.problem(quentin.copy(kind = ProfileKind.Child), listOf(quentin, sophie)))
        assertNull(ProfileRules.deleteProblem(quentin, listOf(quentin, sophie)))
        assertNull(ProfileRules.deleteProblem(enfant, listOf(quentin, enfant)))
    }

    @Test
    fun `on peut commencer par un profil enfant, mais continuer demande un adulte`() {
        assertNull(ProfileRules.problem(enfant, emptyList()))
        assertFalse(ProfileRules.hasAdult(listOf(enfant)))
        assertTrue(ProfileRules.hasAdult(listOf(enfant, quentin)))
    }

    @Test
    fun `huit profils au plus`() {
        val eight = (1..8).map { Profile("p$it", "Profil $it", it % 6, ProfileKind.Adult) }
        assertNotNull(ProfileRules.problem(Profile("n", "Neuf", 0, ProfileKind.Adult), eight))
    }

    @Test
    fun `couleur libre et nom proposé`() {
        assertEquals(2, ProfileRules.nextColor(listOf(quentin, enfant)))
        assertEquals("", ProfileRules.suggestedName(ProfileKind.Adult, emptyList()))
        assertEquals("Enfant", ProfileRules.suggestedName(ProfileKind.Child, listOf(quentin)))
        assertEquals("", ProfileRules.suggestedName(ProfileKind.Child, listOf(enfant)))
    }

    @Test
    fun `profil actif et question au démarrage`() {
        val state = ProfilesState(listOf(quentin, enfant), activeId = "disparu", onStart = StartChoice.Ask)
        assertEquals(quentin, state.active)
        assertTrue(state.asksOnStart)
        assertFalse(state.copy(onStart = StartChoice.ResumeLast).asksOnStart)
        assertFalse(state.copy(profiles = listOf(quentin)).asksOnStart)
    }

    @Test
    fun `aller-retour JSON, entrées illisibles ignorées`() {
        val profiles = listOf(quentin, enfant, invite)
        assertEquals(profiles, ProfileCodec.decode(ProfileCodec.encode(profiles)))
        assertEquals(emptyList<Profile>(), ProfileCodec.decode("pas du json"))
        val mixed = """[{"id":"q","name":"Quentin","color":9,"kind":"adult"},{"id":"x","name":"X","kind":"martien"},{"name":"sans id"}]"""
        assertEquals(listOf(quentin.copy(color = 3)), ProfileCodec.decode(mixed))
    }

    @Test
    fun `applis masquées de l'invité gardées en JSON`() {
        val guest = invite.copy(hiddenApps = setOf("com.netflix.ninja", "org.example"))
        assertEquals(listOf(guest), ProfileCodec.decode(ProfileCodec.encode(listOf(guest))))
        // Sans choix enregistré, ce sont les applis des comptes de la famille.
        assertEquals(null, ProfileCodec.decode(ProfileCodec.encode(listOf(invite))).single().hiddenApps)
        assertEquals(GuestMode.FamilyAccountApps, GuestMode.hiddenApps(invite))
        assertEquals(emptySet<String>(), GuestMode.hiddenApps(invite.copy(hiddenApps = emptySet())))
    }

    @Test
    fun `l'invité n'a ni reprise, ni musique, ni galerie`() {
        val layout = LayoutRules.normalize(GuestMode.Layout)
        assertFalse(HomeRow.Continue in layout.visibleRows)
        assertFalse("music" in layout.visibleTabs)
        assertFalse("gallery" in layout.visibleTabs)
        assertTrue("movies" in layout.visibleTabs)
        assertTrue(invite.isGuest)
        assertFalse(quentin.isGuest)
    }

    @Test
    fun `applis autorisées de l'enfant gardées en JSON`() {
        val child = enfant.copy(allowedApps = setOf("com.google.android.youtube.tvkids", "org.example"))
        assertEquals(listOf(child), ProfileCodec.decode(ProfileCodec.encode(listOf(child))))
        assertEquals(ChildMode.KidsApps, ChildMode.allowedApps(enfant))
        assertEquals(emptySet<String>(), ChildMode.allowedApps(enfant.copy(allowedApps = emptySet())))
    }

    @Test
    fun `accueil de l'enfant, sans reprise, sans TV, musique ni galerie`() {
        val layout = LayoutRules.normalize(ChildMode.layout(null))
        assertEquals(listOf(HomeRow.Apps, HomeRow.Latest), layout.visibleRows)
        assertEquals(listOf("home", "movies", "series", "apps"), layout.visibleTabs)
        assertTrue(enfant.isRestricted)
        assertTrue(invite.isRestricted)
        assertFalse(quentin.isRestricted)
    }

    @Test
    fun `quitter le profil Enfant demande le PIN, s'il existe`() {
        assertTrue(leavingNeedsPin(enfant, quentin, pinSet = true))
        assertFalse(leavingNeedsPin(enfant, quentin, pinSet = false))
        assertFalse(leavingNeedsPin(enfant, enfant, pinSet = true))
        assertFalse(leavingNeedsPin(quentin, enfant, pinSet = true))
        // Même vers un autre profil Enfant : c'est au parent de choisir.
        assertTrue(leavingNeedsPin(enfant, enfant.copy(id = "e2", name = "Léa"), pinSet = true))
    }

    @Test
    fun `limites de l'enfant gardées en JSON`() {
        val child = enfant.copy(limits = ChildLimits(dailyMinutes = 120, curfew = Curfew(20 * 60, 7 * 60 + 30)))
        assertEquals(listOf(child), ProfileCodec.decode(ProfileCodec.encode(listOf(child))))
        assertEquals(ChildLimits(), ProfileCodec.decode(ProfileCodec.encode(listOf(enfant))).single().limits)
    }

    @Test
    fun `galerie de l'enfant, relue et visible seulement si choisie`() {
        val child = Profile("e", "Enfant", 1, ProfileKind.Child, gallery = ChildGallery("q", "Souvenirs", folder = "Photos/Souvenirs"))
        assertEquals(child, ProfileCodec.decode(ProfileCodec.encode(listOf(child))).single())
        // Album et dossier à la fois : choix incohérent, ignoré.
        val broken = """[{"id":"e","name":"Enfant","color":1,"kind":"child","gallery":{"source":"q","name":"x","album":"a","folder":"b"}}]"""
        assertEquals(null, ProfileCodec.decode(broken).single().gallery)
        fun galleryVisible(profile: Profile?) = LayoutRules.normalize(ChildMode.layout(profile)).visibleTabs.contains("gallery")
        assertEquals(false, galleryVisible(child.copy(gallery = null)))
        assertEquals(true, galleryVisible(child))
    }
}
