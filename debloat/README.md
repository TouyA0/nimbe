# debloat

Outil en Go, lancé depuis le PC, qui allège le stick Android TV via ADB **de façon réversible** :
on désactive des paquets, on ne les désinstalle jamais, et l'accueil d'origine reste toujours actif.

## Commandes

| Commande | Ce qu'elle fait | Modifie le stick ? |
|---|---|---|
| `diag` | Modèle, Android, mémoire, stockage, réseau, accueil actuel, liste des paquets, capture d'écran | Non |
| `backup` | Instantané JSON de l'état de chaque paquet | Non |
| `debloat` | Désactive les paquets des listes, après confirmation et instantané automatique | Oui, réversible |
| `restore` | Remet les paquets dans l'état du plus ancien instantané (l'état d'origine) | Oui |
| `check` | Vérifie que les paquets protégés sont actifs, signale ce qu'une mise à jour a réactivé | Non |

Les résultats sont rangés dans `sauvegardes/<numéro de série>/` (ignoré par Git) et ne sont jamais écrasés.

## Listes

- [`listes/protegee.txt`](listes/protegee.txt) : jamais désactivés, même s'ils apparaissent ailleurs.
  L'outil protège aussi automatiquement toutes les applis d'accueil, le clavier actif et Nimbe.
- [`listes/recommandes.txt`](listes/recommandes.txt) : désactivés après une seule confirmation.
- [`listes/optionnels.txt`](listes/optionnels.txt) : une confirmation par paquet.

Un nom de paquet invalide (faute de frappe, espace…) fait échouer la lecture de la liste.

## Garde-fous

- **Verrou d'identité** : `sauvegardes/appareils.json` retient quel numéro de série répond à chaque adresse `-s`.
  Si un autre appareil répond à la même adresse, `debloat` et `restore` refusent d'agir.
- **Simulation** : `-dry-run` affiche le plan sans rien modifier.
- **Confirmation** : non par défaut ; `-yes` pour tout accepter.
- **Instantané automatique** avant chaque `debloat`, et **journal** de chaque action dans `journal.log`.
- Si la détection de l'accueil ou du clavier échoue, l'outil s'arrête plutôt que de risquer de les désactiver.

## Utilisation

```bash
adb connect 192.168.1.57:5555
go run . diag -s 192.168.1.57:5555
go run . debloat -s 192.168.1.57:5555 -dry-run
go run . debloat -s 192.168.1.57:5555
go run . check -s 192.168.1.57:5555
go run . restore -s 192.168.1.57:5555
```

## Tests

```bash
go test ./...
```
