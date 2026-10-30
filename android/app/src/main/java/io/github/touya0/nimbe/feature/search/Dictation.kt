package io.github.touya0.nimbe.feature.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.search.SearchText
import io.github.touya0.nimbe.ui.components.NimbeIcon
import io.github.touya0.nimbe.ui.components.NimbeIcons
import io.github.touya0.nimbe.ui.components.PillButton
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.Nuit
import io.github.touya0.nimbe.ui.theme.PanneauClair

private sealed interface DictationState {
    data class Listening(val heard: String = "") : DictationState
    data class Problem(val message: String) : DictationState
}

/** Écran de dictée : écoute avec le service vocal d'Android et rend le texte reconnu à [onResult]. */
@Composable
internal fun DictationScreen(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<DictationState>(DictationState.Listening()) }
    var attempt by remember { mutableStateOf(0) }
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (!granted) state = DictationState.Problem("Sans accès au micro, la dictée ne peut pas t'écouter. Le clavier marche toujours.")
    }
    LaunchedEffect(Unit) { if (!allowed) permission.launch(Manifest.permission.RECORD_AUDIO) }
    BackHandler(onBack = onClose)
    val currentResult by rememberUpdatedState(onResult)
    if (allowed) {
        DisposableEffect(attempt) {
            state = DictationState.Listening()
            val recognizer = startRecognizer(context, onHeard = { state = DictationState.Listening(it) }, onDone = { currentResult(it) }, onProblem = { state = DictationState.Problem(it) })
            onDispose { recognizer?.destroy() }
        }
    }
    val retry = remember { FocusRequester() }
    val problem = state as? DictationState.Problem
    LaunchedEffect(problem != null) { if (problem != null) runCatching { retry.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Nuit), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(96.dp).background(if (problem == null) Focus else PanneauClair, CircleShape), contentAlignment = Alignment.Center) {
                NimbeIcon(NimbeIcons.Mic, size = 44.dp, tint = if (problem == null) Nuit else Lune)
            }
            when (val current = state) {
                is DictationState.Listening -> {
                    Text("J'écoute…", style = MaterialTheme.typography.displaySmall)
                    Text(
                        if (current.heard.isBlank()) "Parle dans la télécommande" else "« ${current.heard} »",
                        style = MaterialTheme.typography.headlineSmall,
                        color = LuneAttenuee,
                    )
                    Text("Tu peux dire par exemple", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, modifier = Modifier.padding(top = 24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("« Brumes »", "« reprends Brumes »", "« ouvre YouTube »", "« photos de Noël »").forEach {
                            Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.background(PanneauClair, CircleShape).padding(horizontal = 14.dp, vertical = 6.dp))
                        }
                    }
                }
                is DictationState.Problem -> {
                    Text(current.message, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton("Réessayer", {
                            if (allowed) attempt++ else permission.launch(Manifest.permission.RECORD_AUDIO)
                        }, Modifier.focusRequester(retry))
                        PillButton("Revenir au clavier", onClose)
                    }
                }
            }
        }
    }
}

private fun startRecognizer(context: Context, onHeard: (String) -> Unit, onDone: (String) -> Unit, onProblem: (String) -> Unit): SpeechRecognizer? {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onProblem("La reconnaissance vocale n'est pas disponible sur ce stick. Le service vocal de Google est peut-être désactivé.")
        return null
    }
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            first(partialResults)?.let(onHeard)
        }

        override fun onResults(results: Bundle?) {
            val text = first(results)?.let(SearchText::cleanSpoken)
            if (text.isNullOrBlank()) onProblem("Je n'ai rien entendu. Maintiens la télécommande près de toi et réessaie.") else onDone(text)
        }

        override fun onError(error: Int) {
            onProblem(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Je n'ai rien entendu. Réessaie en parlant près de la télécommande."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "La dictée a besoin d'internet, et il ne répond pas."
                    SpeechRecognizer.ERROR_AUDIO -> "Le micro de la télécommande n'envoie rien."
                    else -> "La dictée n'a pas marché. Le clavier marche toujours."
                },
            )
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun first(bundle: Bundle?) = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
    })
    recognizer.startListening(
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
    )
    return recognizer
}
