package io.github.touya0.nimbe.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.ServerAddress
import io.github.touya0.nimbe.data.nextcloud.NextcloudAccounts
import io.github.touya0.nimbe.data.nextcloud.NextcloudException
import io.github.touya0.nimbe.data.nextcloud.NextcloudLink
import io.github.touya0.nimbe.feature.onboarding.AddressField
import io.github.touya0.nimbe.feature.onboarding.StatusLine
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import kotlinx.coroutines.launch

/**
 * L'adresse du Nextcloud sur le réseau de la maison (« 192.168.1.182:7580 »). Avant d'enregistrer, Nimbe
 * vérifie qu'à cette adresse le même compte répond avec le même mot de passe d'application.
 * Elle vaut pour toutes les galeries de ce serveur.
 */
@Composable
fun LocalAddressScreen(accounts: NextcloudAccounts, profileId: String, link: NextcloudLink, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf(link.localUrl?.removePrefix("http://").orEmpty()) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    BackHandler(onBack = onClose)

    fun save() {
        val url = ServerAddress.parse(text, defaultPort = 80).getOrElse {
            error = it.message
            return
        }
        scope.launch {
            checking = true
            error = null
            try {
                accounts.setLocalUrl(profileId, url)
                onClose()
            } catch (e: NextcloudException) {
                error = "${e.message} à cette adresse"
            } finally {
                checking = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Nuit).padding(horizontal = 96.dp, vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Adresse à la maison", style = MaterialTheme.typography.displaySmall)
        Text(
            "Ton Nextcloud répond aussi directement sur le réseau de la maison. Nimbe l'utilise quand il répond : " +
                "les photos arrivent bien plus vite, et la galerie marche même sans internet. " +
                "Ailleurs, ou si le Pi ne répond pas, c'est ${link.serverUrl.substringAfter("://")} qui sert.",
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
            modifier = Modifier.width(900.dp),
        )
        AddressField(
            value = text,
            onValueChange = { text = it },
            onSubmit = ::save,
            modifier = Modifier.focusRequester(field),
            placeholder = "192.168.1.20:7580",
        )
        when {
            checking -> StatusLine("Vérification avec ton compte…", spinning = true)
            error != null -> Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Alerte)
            else -> Text(
                "L'adresse IP du Pi et le port de Nextcloud, par exemple 192.168.1.20:7580. Valable pour toute la maison.",
                style = MaterialTheme.typography.bodyMedium,
                color = LuneAttenuee,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Enregistrer", onClick = ::save)
            if (link.localUrl != null) {
                PillButton("Ne plus l'utiliser", onClick = {
                    scope.launch {
                        accounts.setLocalUrl(profileId, null)
                        onClose()
                    }
                })
            }
            PillButton("Annuler", onClick = onClose)
        }
    }
}
