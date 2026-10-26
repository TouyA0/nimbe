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

## Licence

[MIT](LICENSE)
