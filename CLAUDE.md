# Nimbe

Launcher Android TV maison pour un Xiaomi TV Stick, alimenté par un Raspberry Pi 5
(Jellyfin, Nextcloud, Home Assistant). Le dossier technique complet (« Ophan », ancien nom) et la maquette
de 58 écrans sont hors du dépôt, dans `../docs/`.

## Dépôt

- `android/` : launcher, Kotlin + Jetpack Compose for TV (`androidx.tv`). Package `io.github.touya0.nimbe`
  (définitif : ne jamais le changer, sinon les mises à jour auto cassent). `minSdk 28`.
- `gateway/` : `nimbe-gateway` en Go (guide TV XMLTV, callback OAuth Spotify, actions en liste blanche, sauvegardes).
- `debloat/` : outil Go qui désactive des paquets via ADB, de façon réversible.
- `deploy/` : docker-compose du Pi, Caddyfile, automatisations HA.

## Règles

- Ne jamais désactiver l'accueil d'origine du stick : c'est le secours si le launcher plante.
- Aucun secret dans le dépôt ni dans les journaux. Sur la TV, uniquement des jetons révocables,
  chiffrés avec une clé Android Keystore.
- Chaque écran : utilisable à la télécommande seule, un seul élément focalisé, états chargement / vide / erreur,
  fonctionne quand le Pi est coupé.
- Commits conventionnels (`feat:`, `fix:`, `chore:`…), une branche par fonctionnalité, fusion en squash.
- Textes de l'interface en français.

## Design

Couleurs : Nuit `#0B0E13`, Panneau `#141922`, Lune `#F4F2EE`, Focus `#F0B44C`, En ligne `#8FD4A0`,
Alerte `#F87171`. Polices : Manrope (interface), Bebas Neue (titres de films).
Focus : agrandi de 8 %, anneau ambre de 4 px décalé de 4 px.
