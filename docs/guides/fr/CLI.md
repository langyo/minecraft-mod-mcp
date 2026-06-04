# Guide d'utilisation de la CLI NPM MCP

**[English](../en/CLI.md)** &bull; **[简体中文](../zhs/CLI.md)** &bull; **[繁體中文](../zht/CLI.md)** &bull; **[日本語](../ja/CLI.md)** &bull; **[한국어](../ko/CLI.md)** &bull; **Français** &bull; **[Español](../es/CLI.md)** &bull; **[Русский](../ru/CLI.md)**

> Le package `minecraft-mod-mcp` fournit une CLI complète pour lancer des clients et serveurs Minecraft, gérer les versions et comptes, et compiler des SDK de mods — le tout depuis la ligne de commande.

---

## Installation

```bash
npm install -g minecraft-mod-mcp
```

Ou exécutez directement sans installation :

```bash
npx minecraft-mod-mcp
```

---

## Commandes

### Serveur MCP

Démarrer le serveur MCP stdio pour l'intégration avec les outils IA :

```bash
minecraft-mod-mcp
minecraft-mod-mcp mcp [options]
```

| Option | Description |
|--------|-------------|
| `--no-discover` | Ne pas scanner le mod Minecraft en cours d'exécution |
| `--discover-timeout <ms>` | Délai de découverte du mod (défaut : 300000) |

---

### Lancer le client — `launch`

Lance un client Minecraft avec la version et le loader de mod spécifiés.

```bash
minecraft-mod-mcp launch <version> [options]
```

| Option | Défaut | Description |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Loader de mod |
| `--mc-dir <path>` | Auto | Répertoire du jeu |
| `--java <path>` | Détection auto | Chemin de l'exécutable Java |
| `--memory <mb>` | `2048` | Mémoire JVM maximale (Mo) |
| `--min-memory <mb>` | `512` | Mémoire JVM minimale (Mo) |
| `--jvm-args <args>` | — | Arguments JVM supplémentaires (séparés par espaces) |
| `--game-args <args>` | — | Arguments de jeu supplémentaires (séparés par espaces) |
| `--fullscreen` | `false` | Lancer en plein écran |
| `--width <px>` | `854` | Largeur de la fenêtre |
| `--height <px>` | `480` | Hauteur de la fenêtre |
| `--server <host>` | — | Connexion automatique au serveur au lancement |
| `--server-port <port>` | `25565` | Port du serveur |
| `--port <port>` | Auto | Port du mod MCP |
| `--mod-jar <path>` | — | JAR du mod à injecter |
| `--dry-run` | `false` | Afficher la commande sans l'exécuter |

**Exemples :**

```bash
# Lancer avec 4 Go de RAM, plein écran
minecraft-mod-mcp launch 1.21.11 --memory 4096 --fullscreen --loader fabric

# Lancer avec des flags JVM personnalisés, connexion auto au serveur
minecraft-mod-mcp launch 26.1.2 --jvm-args "-XX:+UseG1GC -Dfml.readTimeout=120" --server myserver.com

# Lancer en fenêtré 1280x720
minecraft-mod-mcp launch 1.20.6 --width 1280 --height 720 --loader neoforge

# Aperçu de la commande de lancement
minecraft-mod-mcp launch 1.21.11 --dry-run
```

---

### Serveur dédié — `server`

Lancer un serveur Minecraft dédié.

```bash
minecraft-mod-mcp server <version> [options]
```

| Option | Défaut | Description |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Loader de mod |
| `--java <path>` | Détection auto | Chemin de l'exécutable Java |
| `--memory <mb>` | `1024` | Mémoire JVM maximale (Mo) |
| `--min-memory <mb>` | — | Mémoire JVM minimale (Mo) |
| `--jvm-args <args>` | — | Arguments JVM supplémentaires (séparés par espaces) |
| `--game-args <args>` | — | Arguments serveur supplémentaires (séparés par espaces) |
| `--mod-jar <path>` | — | JAR du mod à copier dans le dossier mods/ du serveur |
| `--dry-run` | `false` | Afficher la commande sans l'exécuter |

**Exemples :**

```bash
# Lancer un serveur avec 4 Go de RAM
minecraft-mod-mcp server 1.21.11 --memory 4096

# Lancer avec un réglage GC personnalisé
minecraft-mod-mcp server 26.1.2 --jvm-args "-XX:+UseZGC -XX:+ZGenerational" --memory 8192

# Serveur Fabric avec mod
minecraft-mod-mcp server 1.21.11 --loader fabric --mod-jar ./path/to/mod.jar
```

---

### Tout-en-un (serveur + client) — `serve`

Une seule commande : installer le serveur + lancer le serveur + lancer le client (connexion automatique).

```bash
minecraft-mod-mcp serve <version> [options]
```

| Option | Défaut | Description |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Loader de mod |
| `--java <path>` | Détection auto | Chemin de l'exécutable Java |
| `--memory <mb>` | `2048` | Mémoire max du client (Mo) |
| `--min-memory <mb>` | — | Mémoire min du client (Mo) |
| `--server-memory <mb>` | `1024` | Mémoire max du serveur (Mo) |
| `--server-min-memory <mb>` | — | Mémoire min du serveur (Mo) |
| `--jvm-args <args>` | — | Arguments JVM supplémentaires pour les deux |
| `--game-args <args>` | — | Arguments de jeu supplémentaires pour le client |
| `--server-game-args <args>` | — | Arguments supplémentaires pour le serveur |
| `--fullscreen` | `false` | Lancer le client en plein écran |
| `--width <px>` | `854` | Largeur de la fenêtre client |
| `--height <px>` | `480` | Hauteur de la fenêtre client |
| `--port <port>` | Auto | Port MCP |
| `--mod-jar <path>` | — | JAR du mod pour les deux côtés |
| `--dry-run` | `false` | Afficher le plan sans exécuter |

**Exemple :**

```bash
# Environnement complet : 4 Go client, 2 Go serveur, plein écran
minecraft-mod-mcp serve 1.21.11 --memory 4096 --server-memory 2048 --fullscreen
```

---

### Gestion des versions

| Commande | Description |
|---------|-------------|
| `minecraft-mod-mcp list` | Lister toutes les versions Minecraft supportées |
| `minecraft-mod-mcp installed` | Lister les versions installées localement |
| `minecraft-mod-mcp install <version> [--loader <l>]` | Télécharger et installer une version |

---

### Gestion des comptes

| Commande | Description |
|---------|-------------|
| `minecraft-mod-mcp auth login` | Se connecter avec un compte Microsoft |
| `minecraft-mod-mcp auth offline <name>` | Créer un compte hors-ligne |
| `minecraft-mod-mcp auth list` | Lister les comptes configurés |
| `minecraft-mod-mcp auth select <uuid>` | Sélectionner le compte actif |
| `minecraft-mod-mcp auth remove <uuid>` | Supprimer un compte |

---

### Utilitaires

| Commande | Description |
|---------|-------------|
| `minecraft-mod-mcp java` | Détecter les versions Java installées |
| `minecraft-mod-mcp status` | Afficher l'état de connexion du mod MCP |
| `minecraft-mod-mcp sdk <version> [--loader <l>] [--no-build]` | Compiler le SDK de mod pour une version |

---

## Arguments JVM / Jeu

Les options `--jvm-args` et `--game-args` acceptent des arguments séparés par des espaces. Dans les shells qui séparent par espaces, entourez la valeur de guillemets :

```bash
minecraft-mod-mcp launch 1.21.11 --jvm-args "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
minecraft-mod-mcp server 1.21.11 --game-args "--port 25566 --max-players 10"
```

---

## Fichier de configuration JSON

Les valeurs par défaut avancées peuvent être définies dans `~/.minecraft/mcp_launcher/config.json` :

```json
{
  "max_memory_mb": 4096,
  "min_memory_mb": 1024,
  "width": 1920,
  "height": 1080,
  "fullscreen": false,
  "java_args": "-XX:+UseG1GC",
  "game_args": "",
  "game_dir": "~/.minecraft/mcp_launcher/game",
  "mcp_port": 9876,
  "download_source": "bmclapi",
  "language": "fr-FR"
}
```

Les flags CLI remplacent toujours les valeurs du fichier de configuration.
