package io.github.touya0.nimbe.feature.security

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.security.ParentPinStore
import io.github.touya0.nimbe.data.security.PinCheck
import io.github.touya0.nimbe.data.security.PinHash
import io.github.touya0.nimbe.data.security.PinLockout
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.components.focusRing
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Touches chiffrées de la télécommande, en plus du pavé à l'écran. */
private val DigitKeys = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
    Key.NumPad0 to '0', Key.NumPad1 to '1', Key.NumPad2 to '2', Key.NumPad3 to '3', Key.NumPad4 to '4',
    Key.NumPad5 to '5', Key.NumPad6 to '6', Key.NumPad7 to '7', Key.NumPad8 to '8', Key.NumPad9 to '9',
)

/**
 * Saisie d'un PIN à 4 chiffres, en plein écran : pavé à droite, points à gauche. Le quatrième chiffre
 * envoie le code. [round] change à chaque refus : la saisie repart de zéro.
 */
@Composable
private fun PinPad(
    title: String,
    subtitle: String,
    message: String?,
    error: Boolean,
    round: Int,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    // Un seul état pour toute la vie du pavé, vidé à chaque refus : un clic gardé par une tuile
    // écrit toujours au bon endroit, et appelle toujours le dernier [onComplete].
    var digits by remember { mutableStateOf("") }
    LaunchedEffect(round) { digits = "" }
    val complete by rememberUpdatedState(onComplete)
    fun type(digit: Char) {
        if (digits.length >= PinHash.LENGTH) return
        digits += digit
        if (digits.length == PinHash.LENGTH) complete(digits)
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Row(
        Modifier
            .fillMaxSize()
            .background(Nuit)
            .onPreviewKeyEvent { event ->
                val digit = DigitKeys[event.key] ?: return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) type(digit)
                true
            }
            .padding(horizontal = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(80.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.displaySmall)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = LuneAttenuee)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(PinHash.LENGTH) { index ->
                    val filled = index < digits.length
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(if (filled) Lune else Nuit, CircleShape)
                            .border(2.dp, if (filled) Lune else LuneAttenuee, CircleShape),
                    )
                }
            }
            Text(
                message ?: "Les touches chiffrées de la télécommande marchent aussi",
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) Alerte else LuneAttenuee,
            )
            Spacer(Modifier.height(8.dp))
            PillButton("Annuler", onClick = onCancel)
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("123", "456", "789").forEachIndexed { row, keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    keys.forEachIndexed { column, key ->
                        PadKey(key.toString(), onClick = { type(key) }, modifier = if (row == 0 && column == 0) Modifier.focusRequester(first) else Modifier)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PadKey("⌫", onClick = { digits = digits.dropLast(1) })
                PadKey("0", onClick = { type('0') })
                PadKey("OK", onClick = { if (digits.length == PinHash.LENGTH) complete(digits) })
            }
        }
    }
}

@Composable
private fun PadKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(72.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, 36.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PanneauClair,
            contentColor = Lune,
            focusedContainerColor = Lune,
            focusedContentColor = Nuit,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Message à afficher après un refus, avec le temps d'attente recalculé chaque seconde pendant un blocage. */
@Composable
private fun refusalText(check: PinCheck?): String? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(check) {
        while (check is PinCheck.Locked) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return when (check) {
        is PinCheck.Wrong -> if (check.triesLeft == 1) "Code faux · encore 1 essai" else "Code faux · encore ${check.triesLeft} essais"
        is PinCheck.Locked -> if (check.until > now) {
            "Trop de codes faux : réessaie dans ${PinLockout.formatWait(check.until - now)}"
        } else {
            "Tu peux réessayer"
        }
        else -> null
    }
}

/** Demande le PIN parent ; [onSuccess] s'il est bon, ou s'il n'y a plus de PIN à vérifier. */
@Composable
fun PinPrompt(store: ParentPinStore, title: String, subtitle: String, onSuccess: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf<PinCheck?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val message = refusalText(last)
    PinPad(
        title = title,
        subtitle = subtitle,
        message = message ?: FORGOTTEN,
        error = message != null,
        round = round,
        onComplete = { pin ->
            scope.launch {
                when (val result = store.check(pin)) {
                    PinCheck.Ok, PinCheck.NotSet -> onSuccess()
                    else -> {
                        last = result
                        round++
                    }
                }
            }
        },
        onCancel = onCancel,
    )
}

/** Ce qu'on fait du PIN parent depuis Réglages → Confidentialité et sécurité. */
enum class PinChange { Create, Change, Remove }

private enum class Stage { Current, New, Confirm }

/**
 * Créer, changer ou retirer le PIN parent. Changer et retirer demandent d'abord le PIN actuel ;
 * un nouveau PIN est saisi deux fois.
 */
@Composable
fun PinChangeScreen(store: ParentPinStore, change: PinChange, onDone: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(if (change == PinChange.Create) Stage.New else Stage.Current) }
    var chosen by remember { mutableStateOf("") }
    var last by remember { mutableStateOf<PinCheck?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    val refusal = refusalText(last)

    when (stage) {
        Stage.Current -> PinPad(
            title = if (change == PinChange.Remove) "Retirer le PIN parent" else "Changer le PIN parent",
            subtitle = "Saisis d'abord le code actuel.",
            message = refusal ?: FORGOTTEN,
            error = refusal != null,
            round = round,
            onComplete = { pin ->
                scope.launch {
                    when (val result = store.check(pin)) {
                        PinCheck.Ok, PinCheck.NotSet -> if (change == PinChange.Remove) {
                            store.remove()
                            onDone()
                        } else {
                            last = null
                            stage = Stage.New
                            round++
                        }
                        else -> {
                            last = result
                            round++
                        }
                    }
                }
            },
            onCancel = onCancel,
        )
        Stage.New -> PinPad(
            title = "Nouveau PIN parent",
            subtitle = "Quatre chiffres, pour toute la TV. Évite une date de naissance facile à deviner.",
            message = if (mismatch) "Les deux codes ne correspondent pas, recommence" else null,
            error = mismatch,
            round = round,
            onComplete = { pin ->
                chosen = pin
                mismatch = false
                stage = Stage.Confirm
                round++
            },
            onCancel = onCancel,
        )
        Stage.Confirm -> PinPad(
            title = "Confirme le PIN",
            subtitle = "Saisis le même code une seconde fois.",
            message = null,
            error = false,
            round = round,
            onComplete = { pin ->
                if (pin == chosen) {
                    scope.launch {
                        store.set(pin)
                        onDone()
                    }
                } else {
                    mismatch = true
                    stage = Stage.New
                    round++
                }
            },
            onCancel = onCancel,
        )
    }
}

/** Seul moyen de retirer un PIN oublié : effacer les données de Nimbe, ce qui remet toute la TV à zéro. */
private const val FORGOTTEN =
    "Code oublié ? Réglages Android → Applis → Nimbe → Effacer les données le retire, avec toute la configuration."
