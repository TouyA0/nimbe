package io.github.touya0.nimbe.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfileKind
import io.github.touya0.nimbe.data.profiles.ProfileRules
import io.github.touya0.nimbe.data.profiles.ProfilesState
import io.github.touya0.nimbe.data.profiles.ProfilesStore
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit

/** Le profil en cours de création ou de modification. */
private data class Editing(val profile: Profile, val isNew: Boolean)

/**
 * Rangée des profils avec « Ajouter », et l'éditeur à la place quand on en choisit un.
 * Sert au premier lancement et à Réglages → Profils. [below] : ce qui suit la rangée (boutons de l'étape…),
 * caché pendant l'édition pour qu'un seul formulaire soit à l'écran.
 */
@Composable
fun ProfilesManager(
    profiles: List<Profile>,
    onSave: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    /** Poser le focus sur la première tuile dès l'affichage (au premier lancement, pas dans les réglages). */
    focusOnShow: Boolean = false,
    below: @Composable () -> Unit = {},
) {
    var editing by remember { mutableStateOf<Editing?>(null) }
    // Après l'éditeur, le focus revient sur la tuile du profil (ou sur « Ajouter »).
    var focusId by remember { mutableStateOf(if (focusOnShow) profiles.firstOrNull()?.id ?: ADD else null) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(id: String) = requesters.getOrPut(id) { FocusRequester() }

    val current = editing
    if (current != null) {
        BackHandler { editing = null }
        ProfileEditor(
            initial = current.profile,
            isNew = current.isNew,
            all = profiles,
            onSave = {
                onSave(it)
                focusId = it.id
                editing = null
            },
            onCancel = {
                focusId = if (current.isNew) ADD else current.profile.id
                editing = null
            },
            onDelete = if (current.isNew) null else {
                {
                    onDelete(current.profile)
                    focusId = ADD
                    editing = null
                }
            },
        )
        return
    }

    LaunchedEffect(focusId, profiles) {
        val id = focusId ?: return@LaunchedEffect
        val shown = id == ADD || profiles.any { it.id == id }
        runCatching { requester(if (shown) id else ADD).requestFocus() }
        // Un profil tout juste enregistré n'est pas encore dans la liste : on attend qu'il arrive.
        if (shown) focusId = null
    }
    // La marge intérieure laisse la place à l'agrandissement et à l'anneau de focus, que le défilement couperait.
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(FocusRoom),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        profiles.forEach { profile ->
            ProfileTile(
                profile,
                onClick = { editing = Editing(profile, isNew = false) },
                modifier = Modifier.focusRequester(requester(profile.id)),
            )
        }
        if (profiles.size < ProfileRules.MAX_PROFILES) {
            AddProfileTile(
                onClick = {
                    // Le premier profil est un adulte ; ensuite, un enfant est le cas le plus courant.
                    val kind = if (ProfileRules.hasAdult(profiles)) ProfileKind.Child else ProfileKind.Adult
                    val draft = Profile(ProfilesStore.newId(), ProfileRules.suggestedName(kind, profiles), ProfileRules.nextColor(profiles), kind)
                    editing = Editing(draft, isNew = true)
                },
                modifier = Modifier.focusRequester(requester(ADD)),
            )
        }
    }
    below()
}

private const val ADD = "add"
private val FocusRoom = 16.dp

/**
 * « Qui regarde ? » : au démarrage quand la TV est réglée pour demander, et depuis l'avatar de l'en-tête.
 * Retour garde le profil actuel.
 */
@Composable
fun WhoIsWatchingScreen(state: ProfilesState, onPick: (Profile) -> Unit, onManage: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val activeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { activeFocus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(Nuit),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Qui regarde ?", style = MaterialTheme.typography.displaySmall)
        Text(
            "Chaque profil a son accueil et ses réglages.",
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
        )
        Spacer(Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            state.profiles.forEach { profile ->
                ProfileTile(
                    profile,
                    onClick = { onPick(profile) },
                    modifier = if (profile.id == state.active?.id) Modifier.focusRequester(activeFocus) else Modifier,
                )
            }
        }
        Spacer(Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Gérer les profils", onClick = onManage)
            PillButton("Retour à l'accueil", onClick = onClose)
        }
    }
}
