<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft MCP logo" width="200"/>

# Minecraft MCP

**Laissez l'IA jouer à Minecraft**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Release](https://img.shields.io/github/v/release/langyo/minecraft-mod-mcp)](https://github.com/langyo/minecraft-mod-mcp/releases)
[![npm](https://img.shields.io/npm/v/minecraft-mod-mcp)](https://www.npmjs.com/package/minecraft-mod-mcp)

**[English](../en/README.md)** &bull; **[简体中文](../zhs/README.md)** &bull; **[繁體中文](../zht/README.md)** &bull; **[日本語](../ja/README.md)** &bull; **[한국어](../ko/README.md)** &bull; **Français** &bull; **[Español](../es/README.md)** &bull; **[Русский](../ru/README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

## 🤖 Connectez votre IA à Minecraft

**Copiez ce lien et collez-le à votre agent IA — il se configurera automatiquement :**

```
https://github.com/langyo/minecraft-mod-mcp/blob/main/docs/guides/fr/AI-TOOLS.md
```

Votre IA lit le guide, configure la connexion MCP et commence à contrôler le jeu. Aucune configuration manuelle n'est nécessaire.

> Vous avez déjà installé le mod ? Ce lien est tout ce dont vous avez besoin.

---

## Qu'est-ce que Minecraft MCP

Minecraft MCP est un pont entre les assistants IA et Minecraft. Il s'exécute comme un mod à l'intérieur du jeu, exposant un serveur HTTP auquel les outils IA peuvent se connecter via le protocole MCP standard. Grâce à ce pont, l'IA peut voir le jeu, cliquer sur des boutons, taper des commandes et interagir avec le monde.

- **Voir** — capturer des captures d'écran avec des grilles de coordonnées
- **Agir** — cliquer, taper, faire défiler, glisser et appuyer sur n'importe quelle touche
- **Savoir** — interroger la position du joueur, les informations du monde, les boutons de l'écran et les champs de débogage
- **Enregistrer** — diffuser des événements en temps réel via SSE, capturer des images vidéo

> Vous voulez que votre IA construise un château ? Lance un test de fumée ? Navigue dans le menu d'un modpack ? Minecraft MCP rend cela possible.

---

## Versions supportées

| Version MC | Forge | Fabric | NeoForge |
|------------|:-----:|:------:|:--------:|
| 1.8.9 | ✓ | — | — |
| 1.9.4 | ✓ | — | — |
| 1.10.2 | ✓ | — | — |
| 1.11.2 | ✓ | — | — |
| 1.12.2 | ✓ | — | — |
| 1.13.2 | ✓ | — | — |
| 1.14.4 | ✓ | 🚧 | — |
| 1.15.2 | ✓ | 🚧 | — |
| 1.16.5 | ✓ | 🚧 | — |
| 1.17.1 | ✓ | 🚧 | — |
| 1.18.2 | ✓ | 🚧 | — |
| 1.19.4 | ✓ | 🚧 | — |
| 1.20.6 | ✓ | 🚧 | 🚧 |
| 1.21.7 | ✓ | — | — |
| 26.1.2 | ✓ | — | 🚧 |

> 🚧 = En cours de développement

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
https://github.com/langyo/minecraft-mod-mcp/blob/main/docs/guides/fr/AI-TOOLS.md
```

---

## Comment ça fonctionne

```mermaid
flowchart LR
    A["🧠 AI Tool<br/>(Claude Code, Cursor, etc.)<br/>.mcp.json → port 9876"]
    B["🔌 Minecraft MCP<br/>(in-game mod)<br/>HTTP + SSE server"]
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

## Contribuer

Voir [CONTRIBUTING.md](../../CONTRIBUTING.md) pour savoir comment contribuer.

---

## Licence

Sous licence, au choix :

- Licence Apache, Version 2.0 ([LICENSE-APACHE](../../LICENSE-APACHE) ou http://www.apache.org/licenses/LICENSE-2.0)
- Licence MIT ([LICENSE-MIT](../../LICENSE-MIT) ou http://opensource.org/licenses/MIT)

à votre convenance.
