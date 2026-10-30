# Nimbe

**L'accueil de la maison.** Un launcher Android TV maison, rapide et sans pub, qui réunit au même endroit
les films et séries Jellyfin, les photos Nextcloud, la TV en direct et l'état du
serveur Raspberry Pi suivi par Home Assistant.

> 🚧 Projet en cours de construction : phase 0 (faisabilité).

## Principes

- **Local d'abord** : ce qui peut rester sur le réseau de la maison y reste.
- **Rien d'irréversible** : on désactive, on ne désinstalle pas ; l'accueil d'origine reste le secours.
- **Aucun mot de passe sur la TV** : uniquement des jetons révocables (Quick Connect, Login Flow v2, OAuth).
- **Dégradation gracieuse** : si le serveur tombe, la TV en direct et les applis marchent toujours.
- **Léger** : pensé pour un petit stick avec peu de mémoire.

## Organisation du dépôt

| Dossier | Contenu | Techno |
|---|---|---|
| [`android/`](android/) | Le launcher | Kotlin, Jetpack Compose for TV |
| [`gateway/`](gateway/) | `nimbe-gateway`, petit service sur le Pi (sauvegardes) | Go, Docker |
| [`debloat/`](debloat/) | Outil réversible pour alléger le stick via ADB | Go |
| [`deploy/`](deploy/) | `docker-compose` du Pi, Caddyfile, automatisations Home Assistant d'exemple | |
| [`docs/`](docs/) | Décisions techniques, captures | |

## Développer

Prérequis : Android Studio, un émulateur **Android TV (1080p)**, Go et `adb`.

```bash
cd android
./gradlew assembleDebug
```

## Réglages du stick à faire une fois (adb)

Google TV ne propose pas de choisir un écran de veille tiers : on le règle depuis le PC.

```bash
adb shell settings put secure screensaver_components io.github.touya0.nimbe/io.github.touya0.nimbe.feature.screensaver.NimbeDream
adb shell settings put secure screensaver_enabled 1
```

Pour revenir à l'écran de veille de Google TV : `adb shell settings put secure screensaver_components com.google.android.backdrop/.Backdrop`
(valeur d'origine à relever avant avec `adb shell settings get secure screensaver_components`).

HDMI-CEC (Android 12 et plus garde ces réglages pour lui ; Nimbe ouvre seulement Affichage et son → HDMI-CEC) :

```bash
adb shell cmd hdmi_control cec_setting get power_control_mode
```

- `power_control_mode` : `to_tv` éteint la TV quand le stick se met en veille, `broadcast` éteint aussi les autres
  appareils (barre de son), `none` laisse la TV allumée.
- `volume_control_enabled` (`0` ou `1`) : la télécommande du stick règle le volume de la TV.
- `tv_wake_on_one_touch_play` (`0` ou `1`) : réveiller le stick allume la TV et passe sur son entrée.
- Pour changer : `adb shell cmd hdmi_control cec_setting set <réglage> <valeur>`.

Alerte critique par-dessus une autre appli (Google TV n'a pas ce réglage) :

```bash
adb shell appops set io.github.touya0.nimbe SYSTEM_ALERT_WINDOW allow
```

« Continuer à regarder » des autres applis (Watch Next) : Android demande l'accord à la première ouverture de
l'accueil. Si on l'a refusé, il se redonne dans les réglages d'Android, ou ainsi :

```bash
adb shell pm grant io.github.touya0.nimbe android.permission.READ_TV_LISTINGS
```

## Publier une version

Un tag `v*` lance [`release.yml`](.github/workflows/release.yml) : tests, APK signé, empreinte SHA-256
et `update.json` publiés dans une release GitHub. Chaque nuit, le launcher la trouve, la télécharge,
vérifie l'empreinte et la signature, puis l'installe (avec une confirmation à l'écran avant Android 12).

```bash
git tag -a v0.3.0 -m "Première nouveauté" -m "Deuxième nouveauté"
git push origin v0.3.0
```

- `v1.2.0` : version stable. `v1.2.0-beta.1` : bêta, reçue seulement par les appareils réglés sur le canal Bêta.
- Le message du tag devient la liste des nouveautés affichée sur la TV, une ligne par nouveauté.
- La clé de signature n'existe que dans l'environnement GitHub `release` et dans une sauvegarde hors ligne.

## Licence

[MIT](LICENSE)
