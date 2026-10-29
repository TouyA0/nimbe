package io.github.touya0.nimbe.feature.nextcloud

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.nextcloud.NextcloudAccounts
import io.github.touya0.nimbe.feature.homeassistant.QrCode
import io.github.touya0.nimbe.feature.onboarding.AddressField
import io.github.touya0.nimbe.feature.onboarding.Instruction
import io.github.touya0.nimbe.feature.onboarding.ServiceInitial
import io.github.touya0.nimbe.feature.onboarding.StatusLine
import io.github.touya0.nimbe.feature.onboarding.rememberStepFocus
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.Panneau
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.delay

/** Bleu Nextcloud, pour l'initiale du service. */
val NextcloudColor = Color(0xFF0082C9)

/**
 * Écran « Relier la galerie », ouvert depuis Réglages → Serveurs et comptes pour un profil adulte.
 * Plein écran : le focus ne peut pas s'échapper vers l'écran de derrière.
 * [knownServer] : adresse déjà vérifiée (ce profil ou un autre), on passe directement au QR code.
 */
@Composable
fun NextcloudConnectScreen(
    accounts: NextcloudAccounts,
    profileId: String,
    profileName: String,
    knownServer: String?,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(profileId) { NextcloudConnectController(accounts, profileId, scope) }
    LaunchedEffect(controller) { controller.start(knownServer) }
    BackHandler(onBack = onClose)

    Row(
        Modifier.fillMaxSize().background(Nuit).padding(horizontal = 56.dp, vertical = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Column(Modifier.width(320.dp).padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Galerie de $profileName", style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp))
            Text(
                "Tes photos et vidéos Nextcloud sur la TV, avec ton propre compte. Aucun mot de passe ne passe par la TV : " +
                    "tu autorises la connexion depuis ton téléphone.",
                style = MaterialTheme.typography.bodyLarge,
                color = LuneAttenuee,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .background(Panneau, RoundedCornerShape(20.dp))
                .border(1.dp, PanneauClair, RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val step = controller.step) {
                NextcloudStep.Checking -> {
                    PanelHeader("Nextcloud", "Vérification du serveur")
                    StatusLine("Vérification…", spinning = true)
                }
                is NextcloudStep.Address -> AddressContent(step, controller, knownServer)
                is NextcloudStep.Qr -> QrContent(step, onChangeServer = controller::enterAddress)
                is NextcloudStep.Failed -> FailedContent(step, onRetry = { controller.retry(step.server) }, onChangeServer = controller::enterAddress)
                is NextcloudStep.Done -> DoneContent(step, onClose)
            }
        }
    }
}

@Composable
private fun AddressContent(step: NextcloudStep.Address, controller: NextcloudConnectController, knownServer: String?) {
    val focus = rememberStepFocus()
    var text by rememberSaveable { mutableStateOf(knownServer?.removePrefix("https://").orEmpty()) }
    PanelHeader("Adresse de Nextcloud", step.notice ?: "L'adresse où tu ouvres Nextcloud dans ton navigateur.")
    AddressField(
        value = text,
        onValueChange = { text = it },
        onSubmit = { controller.tryAddress(text) },
        modifier = Modifier.focusRequester(focus),
        placeholder = "cloud.example.fr",
    )
    when {
        controller.checking -> StatusLine("Vérification…", spinning = true)
        controller.addressError != null -> Text(controller.addressError.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Alerte)
        else -> Text(
            "Sans http://, Nimbe utilise https://. Le http:// en clair n'est accepté que sur ton réseau.",
            style = MaterialTheme.typography.bodyMedium,
            color = LuneAttenuee,
        )
    }
    PillButton("Continuer", onClick = { controller.tryAddress(text) })
}

@Composable
private fun QrContent(step: NextcloudStep.Qr, onChangeServer: () -> Unit) {
    val focus = rememberStepFocus()
    PanelHeader("Autoriser sur ton téléphone", step.server.substringAfter("://"), initial = true)
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        QrCode(step.loginUrl, 220.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Instruction(1, "Scanne le code avec ton téléphone")
            Instruction(2, "Connecte-toi avec ton propre compte")
            Instruction(3, "Choisis « Autoriser l'accès »")
            Text("Expire dans ${remainingText(step.expiresAt)}", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
        }
    }
    StatusLine(step.trouble ?: "En attente de l'autorisation sur ton téléphone…", spinning = true, alert = step.trouble != null)
    PillButton("Changer d'adresse", onClick = onChangeServer, modifier = Modifier.focusRequester(focus))
}

@Composable
private fun FailedContent(step: NextcloudStep.Failed, onRetry: () -> Unit, onChangeServer: () -> Unit) {
    val focus = rememberStepFocus()
    PanelHeader("Connexion impossible", step.server.substringAfter("://"), initial = true)
    Text(step.message, style = MaterialTheme.typography.bodyLarge, color = Alerte)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Réessayer", onClick = onRetry, modifier = Modifier.focusRequester(focus))
        PillButton("Changer d'adresse", onClick = onChangeServer)
    }
}

@Composable
private fun DoneContent(step: NextcloudStep.Done, onClose: () -> Unit) {
    val focus = rememberStepFocus()
    PanelHeader("Galerie reliée", "Compte ${step.displayName} sur ${step.server.substringAfter("://")}", initial = true)
    Text(
        "Pour retirer l'accès à cette TV : Réglages → Serveurs et comptes, ou dans Nextcloud, " +
            "Paramètres → Sécurité → Appareils et sessions.",
        style = MaterialTheme.typography.bodyMedium,
        color = LuneAttenuee,
    )
    Spacer(Modifier.height(4.dp))
    PillButton("Terminer", onClick = onClose, modifier = Modifier.focusRequester(focus))
}

@Composable
private fun PanelHeader(title: String, subtitle: String, initial: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (initial) ServiceInitial("N", NextcloudColor)
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LuneAttenuee)
        }
    }
}

/** « 19:42 », recalculé chaque seconde. */
@Composable
private fun remainingText(expiresAt: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = ((expiresAt - now) / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
