# debloat

Outil en Go, lancé depuis le PC, qui allège le stick Android TV via ADB **de façon réversible** :
on désactive des paquets, on ne les désinstalle jamais, et l'accueil d'origine reste toujours actif.

## Commandes

| Commande | Ce qu'elle fait | Modifie le stick ? |
|---|---|---|
| `diag` | Modèle, Android, mémoire, stockage, réseau, accueil actuel, liste des paquets, capture d'écran | Non |
| `backup` | Instantané JSON de l'état de chaque paquet, pour une restauration future | Non |
| `debloat`, `restore`, `check` | À venir (phase 1) | |

Les résultats sont rangés dans `sauvegardes/<numéro de série>/` (ignoré par Git) et ne sont jamais écrasés.

## Utilisation

```bash
adb connect 192.168.1.57:5555
go run . diag
go run . backup
```

Avec plusieurs appareils connectés (stick et émulateur), précise lequel : `go run . diag -s 192.168.1.57:5555`.

## Tests

```bash
go test ./...
```
