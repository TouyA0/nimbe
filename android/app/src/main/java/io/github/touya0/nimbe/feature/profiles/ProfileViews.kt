package io.github.touya0.nimbe.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfileKind
import io.github.touya0.nimbe.data.profiles.ProfileRules
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.TextInput
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.NimbeFocus
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair
import io.github.touya0.nimbe.ui.theme.ProfileColors

fun profileColor(index: Int): Color = ProfileColors[index.mod(ProfileColors.size)]

/** Rond de couleur avec l'initiale du profil. */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp) {
    Box(Modifier.size(size).background(profileColor(profile.color), CircleShape), contentAlignment = Alignment.Center) {
        Text(profile.initial, style = MaterialTheme.typography.titleLarge.copy(fontSize = (size.value * 0.42f).sp), color = Lune)
    }
}

/** Tuile d'un profil : avatar, nom et type. Sert à « Qui regarde ? », au premier lancement et aux réglages. */
@Composable
fun ProfileTile(profile: Profile, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String = profile.kind.label) {
    TileSurface(onClick, modifier) {
        ProfileAvatar(profile, AvatarSize)
        TileText(profile.name, detail)
    }
}

/** Tuile « Ajouter » à la fin d'une rangée de profils. */
@Composable
fun AddProfileTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TileSurface(onClick, modifier) {
        Box(Modifier.size(AvatarSize).border(2.dp, LuneAttenuee, CircleShape), contentAlignment = Alignment.Center) {
            Text("+", style = MaterialTheme.typography.titleLarge.copy(fontSize = 36.sp), color = LuneAttenuee)
        }
        TileText("Ajouter", "Adulte, enfant, invité")
    }
}

private val AvatarSize = 88.dp

@Composable
private fun TileSurface(onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(150.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.CardRadius),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Lune,
            focusedContainerColor = PanneauClair,
            focusedContentColor = Lune,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
private fun TileText(title: String, detail: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            detail,
            style = MaterialTheme.typography.labelMedium,
            color = LuneAttenuee,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * Création ou modification d'un profil : nom, couleur, type. [all] : les profils existants, pour les règles
 * (nom unique, un seul invité, toujours un adulte). [onDelete] absent pour un nouveau profil.
 */
@Composable
fun ProfileEditor(
    initial: Profile,
    isNew: Boolean,
    all: List<Profile>,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var color by remember(initial.id) { mutableStateOf(initial.color) }
    var kind by remember(initial.id) { mutableStateOf(initial.kind) }
    var problem by remember(initial.id) { mutableStateOf<String?>(null) }
    // Supprimer demande un second appui : c'est la seule action qu'on ne peut pas annuler.
    var confirmDelete by remember(initial.id) { mutableStateOf(false) }
    // Relu à chaque usage : un clic garde sinon le brouillon tel qu'il était quand le bouton a été dessiné.
    fun draft() = initial.copy(name = name, color = color, kind = kind)

    val first = remember { FocusRequester() }
    LaunchedEffect(initial.id) { runCatching { first.requestFocus() } }

    fun save() {
        val current = draft()
        problem = ProfileRules.problem(current, all)
        if (problem == null) onSave(current.copy(name = ProfileRules.cleanName(current.name)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(draft().copy(name = name.ifBlank { "?" }), 48.dp)
            Text(if (isNew) "Nouveau profil" else "Modifier le profil", style = MaterialTheme.typography.headlineSmall)
        }
        FieldLabel("Nom")
        TextInput(
            value = name,
            onValueChange = { name = it.take(ProfileRules.MAX_NAME) },
            onSubmit = ::save,
            modifier = if (isNew) Modifier.focusRequester(first) else Modifier,
            placeholder = "Prénom",
        )
        FieldLabel("Couleur")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(ProfileRules.COLORS) { index ->
                ColorDot(profileColor(index), selected = index == color, onClick = { color = index })
            }
        }
        FieldLabel("Type")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileKind.entries.forEach { option ->
                ChoicePill(option.label, selected = option == kind, onClick = {
                    // Le nom proposé suit le type tant qu'on ne l'a pas changé.
                    if (isNew && name == ProfileRules.suggestedName(kind, all)) name = ProfileRules.suggestedName(option, all)
                    kind = option
                    problem = null
                })
            }
            Text(kind.summary, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee, modifier = Modifier.padding(start = 8.dp))
        }
        problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Alerte) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Enregistrer", onClick = ::save, modifier = if (isNew) Modifier else Modifier.focusRequester(first))
            PillButton("Annuler", onClick = onCancel)
            if (onDelete != null) {
                PillButton(if (confirmDelete) "Confirmer la suppression" else "Supprimer ce profil", onClick = {
                    problem = ProfileRules.deleteProblem(initial, all)
                    when {
                        problem != null -> Unit
                        confirmDelete -> onDelete()
                        else -> confirmDelete = true
                    }
                })
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = LuneAttenuee)
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, 20.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color, pressedContainerColor = color),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        if (selected) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Text("✓", style = MaterialTheme.typography.titleMedium, color = Lune)
            }
        }
    }
}

/** Pilule d'un choix exclusif : pleine quand elle est choisie. */
@Composable
private fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, NimbeFocus.PillRadius, grow = false),
        shape = ClickableSurfaceDefaults.shape(NimbeFocus.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Lune else PanneauClair,
            contentColor = if (selected) Nuit else Lune,
            focusedContainerColor = if (selected) Lune else PanneauClair,
            focusedContentColor = if (selected) Nuit else Lune,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
    }
}
