# Guide d'intégration des outils d'IA

**[English](../en/AI-TOOLS.md)** &bull; **[简体中文](../zhs/AI-TOOLS.md)** &bull; **[繁體中文](../zht/AI-TOOLS.md)** &bull; **[日本語](../ja/AI-TOOLS.md)** &bull; **[한국어](../ko/AI-TOOLS.md)** &bull; **Français** &bull; **[Español](../es/AI-TOOLS.md)** &bull; **[Русский](../ru/AI-TOOLS.md)**

> **Astuce** : Vous pouvez simplement demander à votre assistant IA de lire ce guide directement depuis l'URL de ce dépôt. Dans la plupart des cas, l'agent configurera automatiquement la connexion MCP — aucune configuration manuelle n'est nécessaire de votre part.

Ce guide explique comment configurer les principaux outils de codage IA pour se connecter au serveur Minecraft MCP via HTTP.

## Points de terminaison HTTP de Minecraft MCP

Le serveur Minecraft MCP expose les points de terminaison HTTP suivants (port par défaut : **9876**) :

| Point de terminaison | Méthode | Description |
|----------|--------|-------------|
| `/api/status` | GET | Vérification de l'état |
| `/api/cmd` | POST | Envoi de commandes JSON-RPC (corps : `{"cmd":"...", "params":{...}}`) |
| `/api/screenshot` | GET | Prendre une capture d'écran, retourne du PNG en base64 |
| `/api/events` | GET | Flux SSE (Server-Sent Events) pour l'historique des appels en temps réel |
| `/api/calls` | GET | Retourne les 50 derniers événements d'appel sous forme de tableau JSON |

> **Prérequis** : Assurez-vous que le démon Minecraft MCP est en cours d'exécution et qu'un client Minecraft avec le mod MCP est connecté. Exécutez `just daemon` puis `just launch <version> <loader>`.

---

## Méthodes d'intégration

La plupart des outils de codage IA prennent en charge le **Model Context Protocol (MCP)** pour se connecter à des serveurs externes. Le serveur Minecraft MCP peut être connecté via :

- **Transport SSE** : Pointer le client MCP de l'outil vers `http://localhost:9876/api/events`
- **API REST HTTP** : Envoyer des requêtes POST directement à `http://localhost:9876/api/cmd`

Les sections ci-dessous fournissent des instructions de configuration spécifiques à chaque outil.

---

## Outils d'agent de codage

### Claude Code

Assistant de codage IA en terminal d'Anthropic.

**Configuration** : Créez ou modifiez `.mcp.json` à la racine de votre projet :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

Vous pouvez également utiliser `claude mcp add minecraft-mcp --transport sse http://localhost:9876/api/events`.

### Claude Desktop / Claude for IDE

L'application de bureau et les versions plugin VS Code/JetBrains IDE de Claude.

**Configuration** : Modifiez `claude_desktop_config.json` :

- **macOS** : `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows** : `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

Pour **Claude for IDE** (VS Code / JetBrains), la configuration est identique — utilisez le fichier `.mcp.json` à la racine de votre projet.

### OpenCode

Agent de codage en terminal open source.

**Configuration** : Créez `.opencode.json` à la racine de votre projet ou modifiez `~/.config/opencode/config.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Cursor

Éditeur de code orienté IA avec prise en charge de modèles personnalisés.

**Configuration** : Créez `.cursor/mcp.json` à la racine de votre projet :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

Ou via l'interface : **Cursor Settings → MCP → Add new MCP Server**, définissez le type de transport sur **SSE** et saisissez l'URL.

### Cline

Extension de codage IA pour VS Code.

**Configuration** : Ouvrez les paramètres VS Code (`Ctrl+,`), recherchez `cline.mcpServers`, ou ajoutez dans `settings.json` :

```json
{
  "cline.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### Roo Code

Extension VS Code intelligente pour l'écriture et la refactorisation de code.

**Configuration** : Ajoutez dans `settings.json` de VS Code (même format que Cline) :

```json
{
  "roo.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### Kilo Code

Plugin VS Code efficace pour la génération de code et la gestion de projet.

**Configuration** : Ajoutez dans `settings.json` de VS Code :

```json
{
  "kilo.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### GitHub Copilot

Programmeur pair IA de GitHub dans VS Code.

**Configuration** : Créez `.github/copilot-instructions.md` dans votre espace de travail, ou configurez MCP via les paramètres VS Code :

```json
{
  "github.copilot.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### GitHub Copilot CLI

GitHub Copilot pour la ligne de commande.

**Configuration** : Définissez des variables d'environnement ou utilisez `gh copilot config` :

```bash
export MCP_SERVER_URL="http://localhost:9876/api/events"
```

### CodeBuddy / WorkBuddy

Outil de programmation full-stack intelligent optimisé par l'IA.

**Configuration** : Créez `mcp.json` à la racine de votre projet ou espace de travail :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### TRAE

Éditeur IA capable d'accomplir de manière autonome diverses tâches de développement.

**Configuration** : Accédez à **Settings → MCP Servers → Add Server** :

- **Name** : `minecraft-mcp`
- **Transport** : SSE
- **URL** : `http://localhost:9876/api/events`

### ZCode

Combine de puissants agents IA avec les chaînes d'outils existantes.

**Configuration** : Modifiez `~/.zcode/config.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Lingma

Assistant de programmation intelligent.

**Configuration** : Accédez à **Settings → MCP → Add Server** :

- **Name** : `minecraft-mcp`
- **Transport** : SSE
- **URL** : `http://localhost:9876/api/events`

### Qoder

Plateforme de programmation par agent pour les logiciels du monde réel.

**Configuration** : Modifiez `~/.qoder/mcp.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Droid

Agent de codage IA en terminal de niveau entreprise pour des flux de travail de bout en bout.

**Configuration** : Modifiez `~/.droid/mcp.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Crush

Outil de programmation IA en terminal prenant en charge les interfaces CLI et TUI.

**Configuration** : Modifiez `~/.crush/config.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Goose

Outil d'agent IA prenant en charge l'exécution locale et les tâches d'ingénierie automatisées.

**Configuration** : Modifiez `~/.config/goose/mcp.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Deep Code

Assistant de codage optimisé par DeepSeek.

**Configuration** : Modifiez `~/.deepcode/config.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Reasonix

Outil de codage IA axé sur le raisonnement.

**Configuration** : Modifiez `~/.reasonix/config.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Langcli

Assistant de codage IA en CLI.

**Configuration** : Modifiez `~/.langcli/config.yaml` :

```yaml
mcp_servers:
  minecraft-mcp:
    type: sse
    url: http://localhost:9876/api/events
```

### Oh My Pi

Plateforme d'agent IA polyvalente.

**Configuration** : Modifiez `~/.oh-my-pi/mcp.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Pi

Compagnon de codage IA léger.

**Configuration** : Modifiez `~/.pi/config.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

---

## Outils d'agent général

### OpenClaw

Assistant IA open source qui s'exécute localement avec une extensibilité par Skills.

**Configuration** : Modifiez `openclaw.json` dans votre espace de travail :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Cherry Studio

IDE d'application IA prenant en charge plusieurs intégrations de modèles.

**Configuration** : Accédez à **Settings → MCP Servers → Add** :

- **Name** : `minecraft-mcp`
- **Transport** : SSE
- **URL** : `http://localhost:9876/api/events`

### Hermes Agent

Agent IA auto-évolutif open source avec mémoire persistante.

**Configuration** : Modifiez `~/.hermes/config.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### AstrBot

Framework de bot optimisé par l'IA.

**Configuration** : Modifiez `astrbot_config.json` :

```json
{
  "mcp_servers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### nanobot

Agent IA léger pour diverses tâches.

**Configuration** : Modifiez `~/.nanobot/config.json` :

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

---

## Accès direct à l'API REST HTTP

Pour les outils qui ne prennent pas en charge nativement le protocole MCP, vous pouvez interagir directement avec le serveur Minecraft MCP via son API REST HTTP :

```bash
# Vérification de l'état
curl http://localhost:9876/api/status

# Exécuter une commande
curl -X POST http://localhost:9876/api/cmd \
  -H "Content-Type: application/json" \
  -d '{"cmd":"screenshot","params":{}}'

# Prendre une capture d'écran
curl http://localhost:9876/api/screenshot

# S'abonner aux événements (flux SSE)
curl http://localhost:9876/api/events
```

### Commandes courantes

| Commande | Description |
|---------|-------------|
| `screenshot` | Prendre une capture d'écran de la fenêtre Minecraft |
| `click` | Cliquer aux coordonnées (x, y) |
| `press_key` | Appuyer sur une touche du clavier |
| `type_text` | Saisir une chaîne de texte |
| `scroll` | Effectuer un défilement de la souris |
| `execute_command` | Exécuter une commande slash Minecraft |
| `get_player_info` | Obtenir la position et l'état du joueur |
| `get_world_info` | Obtenir les informations du monde |

---

## Dépannage

1. **Connexion refusée** : Assurez-vous que le démon MCP est en cours d'exécution (`just daemon`) et qu'un client Minecraft est lancé.
2. **Délai d'attente SSE** : Certains outils peuvent se déconnecter du SSE après une période d'inactivité. Redémarrez l'outil ou la connexion SSE.
3. **Conflit de port** : Si le port 9876 est utilisé, configurez un port différent via la variable d'environnement `MCP_PORT` ou la propriété système `mcp.server.port`.
4. **Pare-feu** : Assurez-vous que votre pare-feu autorise les connexions vers `localhost:9876`.

> Pour toute question ou problème, veuillez ouvrir une issue sur le [dépôt GitHub](https://github.com/langyo/minecraft-mod-mcp).
