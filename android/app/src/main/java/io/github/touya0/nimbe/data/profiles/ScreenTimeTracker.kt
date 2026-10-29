package io.github.touya0.nimbe.data.profiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Compte le temps d'écran du profil Enfant actif, que l'enfant soit dans Nimbe ou dans une appli autorisée :
 * une séance dure tant que le profil est actif et l'écran allumé. Vit avec le processus de Nimbe.
 *
 * Quand le temps est écoulé (ou au couvre-feu) pendant une autre appli, [bringToFront] ramène Nimbe,
 * qui affiche « Temps écoulé ». Android ne le permet que si le service de la touche Accueil est actif ;
 * sinon, l'écran s'affiche au prochain retour sur Nimbe.
 */
class ScreenTimeTracker(
    private val context: Context,
    private val profiles: ProfilesStore,
    private val store: ScreenTimeStore,
    private val scope: CoroutineScope,
    private val bringToFront: () -> Unit,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val screenOn = MutableStateFlow(context.getSystemService(PowerManager::class.java)?.isInteractive ?: true)

    fun start() {
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    screenOn.value = intent.action == Intent.ACTION_SCREEN_ON
                }
            },
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // La séance suit le profil Enfant actif et l'écran : on l'ouvre et on la ferme à chaque changement.
        scope.launch {
            // Une séance restée ouverte par un processus arrêté, sur un autre profil : on ne sait pas quand
            // elle a fini, on la ferme sans la compter plutôt que de compter des heures d'écran éteint.
            val startup = profiles.state.first()
            val current = startup.active?.takeIf { it.isChild && screenOn.value }?.id
            startup.profiles.filter { it.isChild && it.id != current }.forEach { child ->
                store.update(child.id) { it.copy(activeSince = null) }
            }
            var counting: String? = null
            combine(profiles.state, screenOn) { state, on -> state.active?.takeIf { it.isChild && on }?.id }
                .distinctUntilChanged()
                .collect { child ->
                    val now = System.currentTimeMillis()
                    counting?.let { id -> store.update(id) { ScreenTime.stop(it, now, zone()) } }
                    child?.let { id -> store.update(id) { ScreenTime.start(it, now, zone()) } }
                    counting = child
                }
        }

        // Toutes les 30 s pendant une séance : temps écoulé ou couvre-feu, on ramène Nimbe devant.
        scope.launch {
            while (true) {
                delay(CHECK_MS)
                val child = profiles.state.first().active?.takeIf { it.isChild && screenOn.value } ?: continue
                val usage = store.usage(child.id).first()
                if (ScreenTime.verdict(child.limits, usage, System.currentTimeMillis(), zone()).blocked) bringToFront()
            }
        }
    }

    private companion object {
        const val CHECK_MS = 30_000L
    }
}
