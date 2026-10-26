package io.github.touya0.nimbe.feature.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.jellyfin.JellyfinAuth
import io.github.touya0.nimbe.data.jellyfin.JellyfinException
import io.github.touya0.nimbe.data.jellyfin.JellyfinLibrary
import io.github.touya0.nimbe.data.jellyfin.JellyfinLink
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.LuneAttenuee

/**
 * Ce dont les écrans Jellyfin ont besoin : la bibliothèque, le compte relié (null si aucun),
 * et de quoi ouvrir l'écran de connexion. [linkKnown] reste false tant que le compte n'est pas lu :
 * on n'affiche pas « pas connecté » le temps d'un éclair au démarrage.
 */
@Stable
class JellyfinUi(val library: JellyfinLibrary, val link: JellyfinLink?, val linkKnown: Boolean, val onConnect: () -> Unit)

val LocalJellyfin = compositionLocalOf<JellyfinUi?> { null }

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object NotConnected : Load<Nothing>

    /** [reconnect] : le serveur a refusé le jeton, il faut refaire Quick Connect. */
    data class Failed(val message: String, val reconnect: Boolean) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

class Loader<T>(val state: Load<T>, val retry: () -> Unit) {
    val value: T? get() = (state as? Load.Ready<T>)?.value
}

/**
 * Charge des données Jellyfin. La dernière réponse connue ([cacheKey]) s'affiche tout de suite ;
 * si le rafraîchissement échoue ensuite, on la garde plutôt que d'afficher une erreur.
 */
@Composable
fun <T> rememberJellyfin(cacheKey: String?, vararg keys: Any?, load: suspend JellyfinLibrary.(JellyfinAuth) -> T): Loader<T> {
    val ui = LocalJellyfin.current
    var attempt by remember { mutableIntStateOf(0) }
    val cached = remember(ui?.link, cacheKey) { cacheKey?.let { ui?.library?.peek<T>(it) } }
    val state by produceState<Load<T>>(cached?.let { Load.Ready(it) } ?: Load.Loading, ui?.link, ui?.linkKnown, attempt, *keys) {
        if (ui != null && !ui.linkKnown) return@produceState
        val library = ui?.library
        val auth = if (ui?.link == null || library == null) null else library.auth()
        if (library == null || auth == null) {
            value = Load.NotConnected
            return@produceState
        }
        if (value !is Load.Ready) value = Load.Loading
        value = try {
            Load.Ready(library.load(auth))
        } catch (e: JellyfinException) {
            value as? Load.Ready ?: Load.Failed(
                message = e.message.orEmpty(),
                reconnect = e.kind == JellyfinException.Kind.Unauthorized,
            )
        }
    }
    return Loader(state, retry = { attempt++ })
}

/**
 * Message plein écran pour les états chargement, vide, erreur et « pas connecté ».
 * Quand il y a une action, elle prend le focus : l'écran reste utilisable à la télécommande.
 */
@Composable
fun StateMessage(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    takeFocus: Boolean = false,
) {
    val requester = remember { FocusRequester() }
    if (action != null && takeFocus) LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee, textAlign = TextAlign.Center, modifier = Modifier.width(560.dp))
        }
        if (action != null) {
            PillButton(action, onClick = onAction, modifier = Modifier.padding(top = 12.dp).focusRequester(requester))
        }
    }
}

/** États communs d'un onglet Jellyfin ; [content] n'est appelé qu'avec des données. */
@Composable
fun <T> LoadStates(loader: Loader<T>, what: String, takeFocus: Boolean, content: @Composable (T) -> Unit) {
    val ui = LocalJellyfin.current
    when (val state = loader.state) {
        Load.Loading -> StateMessage("Chargement…", null)
        Load.NotConnected -> StateMessage(
            title = "Jellyfin n'est pas connecté",
            detail = "Relie cette TV à ton serveur pour voir $what. Aucun mot de passe ne passe par la TV.",
            action = "Connecter Jellyfin",
            onAction = { ui?.onConnect?.invoke() },
            takeFocus = takeFocus,
        )
        is Load.Failed -> if (state.reconnect) {
            StateMessage(
                title = "L'accès de cette TV a été retiré",
                detail = "Le serveur Jellyfin ne reconnaît plus cette TV. Reconnecte-la avec Quick Connect.",
                action = "Reconnecter",
                onAction = { ui?.onConnect?.invoke() },
                takeFocus = takeFocus,
            )
        } else {
            StateMessage(
                title = "Jellyfin injoignable",
                detail = "${state.message}. Vérifie que le Raspberry Pi est allumé ; tes applis restent disponibles.",
                action = "Réessayer",
                onAction = loader.retry,
                takeFocus = takeFocus,
            )
        }
        is Load.Ready -> content(state.value)
    }
}
