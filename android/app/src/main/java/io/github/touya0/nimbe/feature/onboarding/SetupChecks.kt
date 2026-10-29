package io.github.touya0.nimbe.feature.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.touya0.nimbe.crashguard.StockLauncher
import io.github.touya0.nimbe.feature.screensaver.ScreensaverSetting
import io.github.touya0.nimbe.homekey.HomeKeyService

/** Ce que l'étape Applis vérifie sur le stick, relu à chaque « Revérifier » et au retour des réglages Android. */
data class StickState(
    /** Nimbe est l'accueil par défaut d'Android (impossible sur Google TV, possible ailleurs). */
    val defaultHome: Boolean,
    val homeKeyOn: Boolean,
    /** Commande adb qui active la touche Accueil pour ce paquet (version publiée ou de développement). */
    val homeKeyComponent: String,
    val stockLauncherFound: Boolean,
    /** null : Android ne laisse pas lire le réglage. */
    val screensaverOn: Boolean?,
    val screensaverComponent: String,
    val canInstallUpdates: Boolean,
)

enum class CheckLevel { Ok, ToDo, Unknown }

enum class CheckAction { Accessibility, Screensaver, InstallPermission }

/** [command] : à lancer depuis le PC quand Android ne laisse pas une appli changer le réglage elle-même. */
data class SetupCheck(
    val title: String,
    val detail: String,
    val level: CheckLevel,
    val value: String,
    val action: CheckAction?,
    val command: String? = null,
)

/** Les vérifications de fin de premier lancement. Aucune ne bloque : Nimbe marche sans, en moins pratique. */
object SetupChecks {

    fun build(state: StickState): List<SetupCheck> = listOf(
        when {
            state.defaultHome -> SetupCheck(
                "Touche Accueil", "Nimbe est l'écran d'accueil d'Android", CheckLevel.Ok, "OK", null,
            )
            state.homeKeyOn -> SetupCheck(
                "Touche Accueil", "Appui court : Nimbe · appui long : l'accueil d'origine", CheckLevel.Ok, "OK", CheckAction.Accessibility,
            )
            else -> SetupCheck(
                "Touche Accueil",
                "Pour qu'elle ouvre Nimbe, une fois depuis le PC",
                CheckLevel.ToDo,
                "À activer",
                CheckAction.Accessibility,
                command = "adb shell \"settings put secure enabled_accessibility_services ${state.homeKeyComponent}; " +
                    "settings put secure accessibility_enabled 1\"",
            )
        },
        if (state.stockLauncherFound) {
            SetupCheck("Accueil d'origine", "Toujours installé : c'est le secours si Nimbe plante", CheckLevel.Ok, "OK", null)
        } else {
            SetupCheck(
                "Accueil d'origine",
                "Introuvable : en cas de problème, Nimbe ouvrira les réglages Android",
                CheckLevel.ToDo,
                "Absent",
                null,
            )
        },
        when (state.screensaverOn) {
            true -> SetupCheck("Écran de veille", "Heure et état du serveur", CheckLevel.Ok, "Activé", CheckAction.Screensaver)
            false -> SetupCheck(
                "Écran de veille",
                "Heure et état du serveur · à choisir une fois depuis le PC",
                CheckLevel.ToDo,
                "À choisir",
                CheckAction.Screensaver,
                command = "adb shell settings put secure screensaver_components ${state.screensaverComponent}",
            )
            null -> SetupCheck("Écran de veille", "Android ne laisse pas lire ce réglage", CheckLevel.Unknown, "Inconnu", CheckAction.Screensaver)
        },
        if (state.canInstallUpdates) {
            SetupCheck("Mises à jour", "Nimbe peut installer ses nouvelles versions", CheckLevel.Ok, "Autorisées", null)
        } else {
            SetupCheck(
                "Mises à jour",
                "Pour installer ses nouvelles versions · OK pour l'autoriser",
                CheckLevel.ToDo,
                "À autoriser",
                CheckAction.InstallPermission,
            )
        },
    )

    fun read(context: Context, canInstallUpdates: Boolean): StickState {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val defaultHome = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
        return StickState(
            defaultHome = defaultHome,
            homeKeyOn = HomeKeyService.isEnabled(context),
            homeKeyComponent = ComponentName(context, HomeKeyService::class.java).flattenToString(),
            stockLauncherFound = StockLauncher.intent(context) != null,
            screensaverOn = ScreensaverSetting.isActive(context),
            screensaverComponent = ScreensaverSetting.component(context),
            canInstallUpdates = canInstallUpdates,
        )
    }
}
