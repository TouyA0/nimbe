package io.github.touya0.nimbe.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ScreenTime
import io.github.touya0.nimbe.data.profiles.ScreenTimeVerdict
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit

/**
 * Écran de fin doux du profil Enfant : temps du jour écoulé, ou couvre-feu. Par-dessus tout le reste ;
 * Retour ne le ferme pas. Un parent peut accorder un peu de rab avec son PIN, ou changer de profil.
 */
@Composable
fun TimeUpScreen(profile: Profile, verdict: ScreenTimeVerdict, onBonus: () -> Unit, onSwitchProfile: () -> Unit) {
    BackHandler {}
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val (title, text) = when (verdict) {
        is ScreenTimeVerdict.Curfewed -> "C'est l'heure de dormir !" to
            "La TV est fermée jusqu'à ${ScreenTime.curfewEnd(verdict.curfew).let { "%d:%02d".format(it.hour, it.minute) }}. " +
            "Bonne nuit ${profile.name}, à demain !"
        is ScreenTimeVerdict.TimeUp -> "C'est fini pour aujourd'hui !" to
            "Tu as regardé ${ScreenTime.formatDuration(verdict.usedMs)} de télé. Ce que tu regardais est gardé au chaud : " +
            "tu le reprendras demain."
        is ScreenTimeVerdict.Allowed -> return
    }
    Column(
        Modifier.fillMaxSize().background(Nuit),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ProfileAvatar(profile, 96.dp)
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = LuneAttenuee,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(620.dp),
        )
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                "Encore ${ScreenTime.formatDuration(ScreenTime.BONUS_MS)} (code parent)",
                onClick = onBonus,
                modifier = Modifier.focusRequester(focus),
            )
            PillButton("Changer de profil", onClick = onSwitchProfile)
        }
    }
}
