<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft Mod MCP logo" width="200"/>

# Minecraft Mod MCP

**Kit de développement de mods assisté par IA**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Release](https://img.shields.io/github/v/release/langyo/minecraft-mod-mcp)](https://github.com/langyo/minecraft-mod-mcp/releases)
[![npm](https://img.shields.io/npm/v/minecraft-mod-mcp)](https://www.npmjs.com/package/minecraft-mod-mcp)

**[English](../../README.md)** &bull; **[简体中文](../zhs/README.md)** &bull; **[繁體中文](../zht/README.md)** &bull; **[日本語](../ja/README.md)** &bull; **[한국어](../ko/README.md)** &bull; **Français** &bull; **[Español](../es/README.md)** &bull; **[Русский](../ru/README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

## 🤖 Connectez votre IA à Minecraft

**Copiez ce lien et collez-le à votre agent IA — il se configurera automatiquement :**

```
https://github.com/langyo/minecraft-mod-mcp/blob/master/docs/guides/fr/AI-TOOLS.md
```

Votre IA lit le guide, configure la connexion MCP et commence à contrôler le jeu. Aucune configuration manuelle n'est nécessaire.

> Vous avez déjà installé le mod ? Ce lien est tout ce dont vous avez besoin.

---

## Qu'est-ce que Minecraft Mod MCP

Minecraft Mod MCP est un outil d'assistance IA **pour les développeurs de mods**. Placez-le dans votre dossier `mods`, lancez le jeu, et votre IA pourra voir le jeu, cliquer sur les boutons GUI, taper des commandes et interagir avec le monde — le tout via le protocole MCP standard. Conçu pour tester vos mods, vérifier leur comportement et automatiser les tâches répétitives.

- **Voir** — capturer des captures d'écran avec des grilles de coordonnées
- **Agir** — cliquer, taper, faire défiler, glisser et appuyer sur n'importe quelle touche
- **Savoir** — interroger la position du joueur, les informations du monde, les boutons de l'écran et les champs de débogage
- **Enregistrer** — diffuser des événements en temps réel via SSE, capturer des images vidéo

> Vous voulez que votre IA teste l'interface de votre mod ? Lance un test de fumée ? Vérifie le comportement d'un nouveau bloc ? Minecraft Mod MCP rend cela possible.

---

## Versions supportées

| Version MC | Forge | Fabric | NeoForge |
|------------|:-----:|:------:|:--------:|
| 26.1.2 | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-26.1.2-forge.jar) | — | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-26.1.2-neoforge.jar) |
| 1.21.11 | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-1.21.11-forge.jar) | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-1.21.11-fabric.jar) | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-1.21.11-neoforge.jar) |

> Les versions antérieures (1.8.9 – 1.20.6) sont disponibles sur la [page des releases](https://github.com/langyo/minecraft-mod-mcp/releases).

---

## Pour commencer

### 1. Installer le mod

Téléchargez le JAR depuis les [Releases GitHub](https://github.com/langyo/minecraft-mod-mcp/releases) et placez-le dans votre dossier `mods` de Minecraft.

- Nécessite **Forge**, **Fabric** ou **NeoForge** (voir les versions supportées ci-dessus)
- Compatible avec Minecraft **1.8.9** jusqu'à **26.1.2**

### 2. Installer le pont MCP (Bridge)

```bash
npm install -g minecraft-mod-mcp
```

Ou exécutez-le sans installation :

```bash
npx minecraft-mod-mcp
```

### 3. Lancer Minecraft

Lancez le jeu avec votre modloader. Le mod démarre automatiquement un serveur HTTP sur le port 9876.

### 4. Connecter votre IA

**[→ Guide d'intégration des outils IA](./AI-TOOLS.md)** — instructions pas à pas pour Claude Code, Cursor, Cline, Copilot et plus de 20 autres outils IA.

Ou collez ce lien à votre agent IA et laissez-le gérer la configuration :

```
https://github.com/langyo/minecraft-mod-mcp/blob/master/docs/guides/fr/AI-TOOLS.md
```

### 5. Utilisation de la CLI

**[→ Guide d'utilisation de la CLI](./CLI.md)** — lancer clients et serveurs, gérer versions et comptes, compiler des SDKs, tout en ligne de commande.

---

## Astuces d'utilisation

### Travailler à côté du mod

Normalement, basculer hors de Minecraft ouvre l'écran de pause, ce qui peut interrompre les commandes MCP. Utilisez l'une de ces méthodes pour l'éviter :

- **Écran de pause** : Appuyez sur `Échap` pour ouvrir l'écran de pause, puis cliquez sur le bouton **libérer la souris** de l'overlay MCP. Cela vous permet de changer de fenêtre librement sans réactiver l'écran de pause.
- **Overlay en jeu** : En vue 3D, cliquez sur le bouton de l'overlay MCP dans le **coin supérieur droit** pour détacher temporairement le curseur. Une fois libéré, vous pouvez faire `Alt+Tab` sans que le jeu ne se mette en pause — idéal pour travailler dans votre IDE ou outil IA tout en gardant la connexion MCP active.

### Port et serveur HTTP

Le mod démarre un serveur HTTP au lancement du jeu. Il essaie d'abord le port **9876** ; s'il est occupé, il bascule sur **9875 → 9874 → ... → 9000** jusqu'à en trouver un libre. Vous pouvez fixer un port avec `-Dmcp.port=XXXX` (argument JVM) ou `MC_MCP_PORT` (variable d'env).

Pour savoir quel port a été choisi :
- La console affiche `[MCP-MOD] Debug page: http://127.0.0.1:{port}/debug`
- Un message cliquable avec l'URL de la page de débogage apparaît dans le chat en jeu
- `GET /api/status` renvoie `version`, `loader`, `port`, `pid`, `uptime` — le pont Node.js s'en sert pour la découverte automatique
- Ouvrez `http://localhost:{port}/debug` dans votre navigateur pour un tableau de bord en direct avec les logs MCP, les événements SSE et l'état de connexion

La version MC et le loader sont confirmés lors de la poignée de main via `/api/status`, permettant au pont et à la page de débogage d'identifier l'environnement MC connecté.

---

## Comment ça fonctionne

<details>
<summary>📸 Capture d'écran — cliquer pour agrandir</summary>

<img src="../screenshot.webp" alt="Capture d'écran de Minecraft Mod MCP" width="100%"/>

</details>

```mermaid
flowchart LR
    A["🧠 AI Tool<br/>(Claude Code, Cursor, etc.)<br/>.mcp.json → port 9876"]
    B["🔌 Minecraft Mod MCP<br/>(in-game mod)<br/>HTTP + SSE server"]
    C["🎮 Minecraft Client<br/>(1.8.9 – 26.1.2)"]

    A <-- "HTTP / SSE" --> B
    B -- "reflection" --> C
```

Le mod exécute un serveur HTTP sur le port 9876 dans Minecraft. Votre outil IA se connecte via le protocole MCP standard (transport SSE), et chaque commande — clic, saisie, capture d'écran, etc. — utilise la réflexion Java pour fonctionner sur toutes les versions de Minecraft sans code spécifique à chaque version.

---

## Compilation depuis les sources

> Cette section s'adresse aux contributeurs. Si vous souhaitez simplement utiliser le mod, consultez [Pour commencer](#pour-commencer) ci-dessus.

Consultez [CONTRIBUTING.md](../../CONTRIBUTING.md) pour la configuration de développement, la structure du projet et les directives de contribution.

---

## Licence

Sous licence, au choix :

- Licence Apache, Version 2.0 ([LICENSE-APACHE](../../LICENSE-APACHE) ou http://www.apache.org/licenses/LICENSE-2.0)
- Licence MIT ([LICENSE-MIT](../../LICENSE-MIT) ou http://opensource.org/licenses/MIT)

à votre convenance.
