# Nimbe

**L'accueil de la maison.** Un launcher Android TV maison, rapide et sans pub, qui réunit au même endroit
les films et séries Jellyfin, les photos Nextcloud, la TV en direct, la musique Spotify et l'état du
serveur Raspberry Pi suivi par Home Assistant.

> 🚧 Projet en cours de construction : phase 0 (faisabilité).

## Principes

- **Local d'abord** : ce qui peut rester sur le réseau de la maison y reste.
- **Rien d'irréversible** : on désactive, on ne désinstalle pas ; l'accueil d'origine reste le secours.
- **Aucun mot de passe sur la TV** : uniquement des jetons révocables (Quick Connect, Login Flow v2, OAuth).
- **Dégradation gracieuse** : si le serveur tombe, la TV en direct, les applis et Spotify marchent toujours.
- **Léger** : pensé pour un petit stick avec peu de mémoire.

## Organisation du dépôt

| Dossier | Contenu | Techno |
|---|---|---|
| [`android/`](android/) | Le launcher | Kotlin, Jetpack Compose for TV |
| [`gateway/`](gateway/) | `nimbe-gateway`, petit service sur le Pi (guide TV, OAuth Spotify, sauvegardes) | Go, Docker |
| [`debloat/`](debloat/) | Outil réversible pour alléger le stick via ADB | Go |
| [`deploy/`](deploy/) | `docker-compose` du Pi, Caddyfile, automatisations Home Assistant d'exemple | |
| [`docs/`](docs/) | Décisions techniques, captures | |

## Développer

Prérequis : Android Studio, un émulateur **Android TV (1080p)**, Go et `adb`.

```bash
cd android
./gradlew assembleDebug
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
