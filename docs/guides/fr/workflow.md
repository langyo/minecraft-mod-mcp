# Système d'automatisation MCP Workflow

## Aperçu

MCP Workflow est un framework d'automatisation Minecraft basé sur YAML, remplaçant l'ancien `run.py` basé sur les arguments de ligne de commande.

**Philosophie de conception principale : Preview Click (Clic avec aperçu)** — avant d'exécuter un véritable clic, un marqueur rouge est d'abord dessiné sur la capture d'écran, confirmé visuellement, puis le clic est exécuté. Cela évite de deviner les coordonnées à l'aveugle.

## Démarrage rapide

```bash
# Simulation à sec (sans lancer MC, valide uniquement l'analyse YAML et la logique des étapes)
python scripts/run_yaml.py workflows/smoke_test.yaml --dry-run --skip-setup

# Exécution complète (lancement automatique de MC + exécution de toutes les étapes)
python scripts/run_yaml.py workflows/smoke_test.yaml

# Sauter la phase de démarrage si MC est déjà en cours d'exécution
python scripts/run_yaml.py workflows/smoke_test.yaml --skip-setup

# Exécuter uniquement l'étape 5
python scripts/run_yaml.py workflows/smoke_test.yaml --step 5

# Désactiver l'intégration du conteneur
python scripts/run_yaml.py workflows/smoke_test.yaml --no-container
```

## Flux Preview Click

C'est l'innovation centrale du système. L'approche traditionnelle envoie directement les coordonnées pour cliquer ; si les coordonnées sont incorrectes, le clic est perdu. Le flux d'aperçu se déroule en deux étapes :

```
Étape 1 : preview_click    → Met en file d'attente un marqueur rouge en (x,y), aucune action exécutée
Étape 2 : screenshot       → Capture d'écran + dessin du point rouge/réticule/texte de coordonnées sur l'image
          ↓ L'IA examine la capture d'écran annotée ↓
Étape 3 : click            → Après confirmation de l'exactitude des coordonnées, exécute le vrai clic
```

### Effets d'annotation du point rouge

- **Cercle rouge** (rayon configurable, valeur par défaut 10px)
- **Réticule** (lignes horizontale + verticale)
- **Étiquette de coordonnées** `(426,236) Solo` avec fond noir semi-transparent

### Exemple YAML

```yaml
# Étape 1 : Aperçu des coordonnées
- action: preview_click
  x: 426
  y: 236
  label: "Solo"
  radius: 10
  color: "#FF0000"
  comment: "Aperçu : dessiner un point rouge sur le bouton Solo"

# Étape 2 : Capture d'écran (inclura automatiquement le point rouge)
- action: screenshot
  name: "preview_singleplayer"
  comment: "Examen IA : le point rouge est-il sur le bouton ?"

# Étape 3 : Clic après confirmation
- action: click
  x: 426
  y: 236
  comment: "Coordonnées confirmées, exécution du clic"
```

## Actions prises en charge

| Action | Paramètres | Description |
|------|------|------|
| `wait` | `seconds` | Attendre le nombre de secondes spécifié |
| `screenshot` | `name` | Capturer et enregistrer une capture d'écran (dessine automatiquement les marqueurs d'aperçu en file d'attente) |
| **`preview_click`** | `x, y, label, radius, color` | **Mettre en file d'attente un marqueur rouge, ne pas exécuter de clic** |
| `click` | `x, y` | Exécuter un clic aux coordonnées |
| `click_btn_idx` | `index` | Cliquer sur un bouton par index de widget |
| `click_btn_id` | `button_id` | Cliquer sur un bouton par ID |
| `ctrl_on` | - | Entrer en mode de contrôle MCP (souris découplée) |
| `ctrl_off` | - | Quitter le mode de contrôle |
| `key` | `key` | Appuyer sur une touche (ex. `Escape`, `E`) |
| `paste` | `text, press_enter` | Coller du texte (contourne les problèmes IME) |
| `scroll` | `clicks` | Faire défiler la molette de la souris |
| `look_delta` | `dyaw, dpitch` | Faire pivoter l'angle de vue dans le jeu (ne déplace pas la souris) |
| `set_view_angle` | `yaw, pitch` | Définir l'angle de vue absolu |
| `right_click` | - | Clic droit |
| `enumerate_widgets` | - | Lister tous les widgets de l'écran actuel |
| `get_screen_buttons` | - | Obtenir la liste des boutons de l'écran actuel |
| `cmd` | `command` | Exécuter une commande MC (ex. `/gamemode creative`) |
| `vision_check` | `prompt, expect, store_as` | Analyse visuelle de la capture d'écran par IA |

## Format du workflow YAML

```yaml
name: "Nom du workflow"
description: |
  Description multiligne expliquant ce que fait ce workflow et ce qu'il valide.

setup:
  version: "1.21.7-forge-57.0.2"   # Version MC
  container: false                   # Utiliser ou non la fenêtre conteneur
  wait_after_connect: 15             # Secondes d'attente après connexion pour le chargement de MC

steps:
  # Chaque étape est une action, exécutée dans l'ordre
  - action: wait
    seconds: 15
    comment: "Attendre la fin du chargement de MC"

  - action: ctrl_on
    comment: "Entrer en mode de contrôle"

  - action: screenshot
    name: "baseline"
    comment: "Capture d'écran de référence"
```

## Champs de configuration setup

| Champ | Type | Valeur par défaut | Description |
|------|------|--------|------|
| `version` | string | `"1.21.7-forge-57.0.2"` | Identifiant de version MC |
| `container` | bool | `true` | Utiliser ou non l'intégration du conteneur Win32 |
| `wait_after_connect` | int | `15` | Secondes d'attente après la connexion du mod (pour que l'écran de démarrage Mojang disparaisse) |

## Arguments CLI

| Argument | Description |
|------|------|
| `<workflow.yaml>` | Chemin du fichier de workflow YAML (obligatoire) |
| `--dry-run` | Mode simulation à sec, n'envoie pas de commandes réelles à MC |
| `--skip-setup` | Sauter le démarrage de MC (à utiliser si MC est déjà en cours d'exécution) |
| `--step N` | Exécuter uniquement l'étape N (indexée à partir de 1) |
| `--interactive` | Pause après chaque étape et attente de confirmation |
| `--no-container` | Désactiver la fenêtre conteneur |

## Structure des fichiers

```
minecraft-mcp/
├── workflows/                        # Définitions de workflow YAML
│   └── smoke_test.yaml               # Test de fumée : menu principal → en jeu → rotation de vue
├── scripts/
│   ├── run_yaml.py                   # Point d'entrée de l'exécuteur YAML
│   ├── workflow_engine.py            # Moteur principal (exécution d'actions, gestion d'état, annotation de captures)
│   └── run.py                       # Ancien exécuteur CLI (toujours utilisable)
├── packages/common/                  # Code commun du mod (entrée par réflexion, capture d'écran, mode de contrôle)
│   └── src/main/java/.../
│       ├── ReflectionHelper.java     # guiClick, preview_click, lookDelta, etc.
│       ├── McpMessageHandler.java    # Distribution de messages WebSocket
│       └── ReflectedInputHandler.java# Gestionnaire d'entrée
└── docs/
    └── workflow.md                   # Ce document
```

## Différences avec l'ancien run.py

| Fonctionnalité | run.py (Ancien) | run_yaml.py (Nouveau) |
|------|-------------|-----------------|
| Format | Arguments CLI | Fichier YAML |
| Réutilisabilité | Faible (arguments à taper chaque fois) | Élevée (YAML versionnable et partageable) |
| Preview click | Non pris en charge | ✅ preview_click + annotation de capture d'écran |
| Commentaires structurés | Aucun | Chaque étape a un champ comment |
| Récupération d'erreur | Aucune | Suivi de l'état success/error par étape |
| Branchement conditionnel | Non pris en charge | vision_check / if_screen |

## Écriture de workflows personnalisés

1. Copier `workflows/smoke_test.yaml` comme modèle
2. Modifier `name`, `description` et `steps`
3. Valider d'abord la syntaxe avec `--dry-run --skip-setup`
4. Tester avec une vraie instance MC

### Modèle typique : Trouver un bouton et cliquer

```yaml
# 1. D'abord capturer l'écran actuel
- action: screenshot
  name: "current_screen"

# 2. Utiliser la vision IA pour trouver les coordonnées du bouton (ou lire manuellement depuis les résultats enum)
- action: vision_check
  prompt: "Trouver les coordonnées en pixels du centre du bouton « Créer un nouveau monde »"
  store_as: "create_world_pos"

# 3. Aperçu de ces coordonnées
- action: preview_click
  x: "${variables.create_world_pos.x}"   # TODO: référence de variable à implémenter
  y: "${variables.create_world_pos.y}"
  label: "Créer un nouveau monde"

# 4. Capture d'écran pour examen
- action: screenshot
  name: "preview_create_world"

# 5. Confirmer et cliquer
- action: click
  x: 350
  y: 420
```

## Remarques importantes

1. **Ne pas entrer en mode de contrôle pendant l'écran de démarrage Mojang** — cela bloquera le jeu sur le logo. `setup.wait_after_connect` doit être ≥ 15 secondes.
2. **Préférer Robot (AWT) pour les captures d'écran** plutôt que les captures natives MC — les captures natives MC peuvent retourner des trames en cache.
3. **Prévoir suffisamment d'attente après chaque clic** — les transitions GUI prennent du temps, généralement 3 à 8 secondes.
4. **Les marqueurs de preview_click sont dessinés lors du prochain screenshot** — c'est un mécanisme de file d'attente, pas un effet immédiat.
