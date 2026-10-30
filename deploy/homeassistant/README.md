# Home Assistant pour Nimbe

Ce que Nimbe lit dans Home Assistant pour savoir si le Pi va bien.

## 1. Compte de la TV

Paramètres → Personnes → Ajouter une personne : `tv-salon`, connexion autorisée,
**Administrateur** désactivé, **Accès local uniquement** activé.
Connecté avec ce compte : Profil → Sécurité → Jetons d'accès longue durée → Créer un jeton,
puis Nimbe → Réglages → Serveurs et comptes → Home Assistant.

## 2. Santé du Pi : System Monitor

Paramètres → Appareils et services → Ajouter une intégration → System Monitor.
Plusieurs capteurs sont désactivés au départ : dans l'intégration, ouvrir les entités et activer
**Processeur utilisé**, **Température du processeur**, **Mémoire utilisée (%)** et
**Disque utilisé (%)** pour `/`.

## 3. Services : `packages/nimbe.yaml`

1. Copier `packages/nimbe.yaml` dans le dossier `packages/` de la configuration de Home Assistant
   (à côté de `configuration.yaml`), en adaptant les adresses.
2. Si ce n'est pas déjà fait, ajouter dans `configuration.yaml` :

   ```yaml
   homeassistant:
     packages: !include_dir_named packages
   ```

3. Outils de développement → YAML → Vérifier la configuration, puis redémarrer Home Assistant.

## 4. Notifications (facultatif)

Sans rien faire, Nimbe prévient déjà quand le Pi ou un service suivi ne répond plus depuis 2 minutes, et quand
le disque dépasse 80 %. Pour envoyer tes propres alertes à la TV, une automatisation déclenche l'événement
`nimbe_notify` (titre, détail, niveau `critique`, `normal` ou `discret`) : voir l'exemple à la fin de
`packages/nimbe.yaml`, qui crée aussi le capteur `sensor.nimbe_notification` que la TV écoute.

Pour essayer : Outils de développement → Événements → type `nimbe_notify`, données
`{"title": "Test", "detail": "Depuis Home Assistant", "level": "normal"}`.

## Vérifier

Outils de développement → Modèle, coller :

```jinja
{{ states | selectattr('entity_id', 'search', 'processor|cpu_|memory_|disk_|temperature|joignable')
          | rejectattr('entity_id', 'search', 'docker')
          | map(attribute='entity_id') | join('\n') }}
```

Les capteurs listés sont ceux que Nimbe peut proposer.
