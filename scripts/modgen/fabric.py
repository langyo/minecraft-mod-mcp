"""Fabric Loom build.gradle / settings.gradle / fabric.mod.json generators."""

import os

from version_config import get_fabric_loom

from .gradle import _gradle_ver_for_fabric, maven_local_depth

FABRIC_API_VERSIONS = {
    "1.14.4": "0.4.3+build.296-1.14.4",
    "1.15":   "0.5.0+build.294-1.15",
    "1.15.2": "0.7.1+build.311-1.15.2",
    "1.16.1": "0.17.1+build.360-1.16.1",
    "1.16.3": "0.22.0+build.416-1.16",
    "1.16.4": "0.30.0+1.16",
    "1.16.5": "0.42.0+1.16.5",
    "1.17.1": "0.46.1+1.17",
    "1.18":   "0.58.0+1.18.2",
    "1.18.2": "0.58.0+1.18.2",
    "1.19":   "0.73.2+1.19.3",
    "1.19.2": "0.77.0+1.19.2",
    "1.19.3": "0.87.2+1.19.3",
    "1.19.4": "0.92.2+1.19.4",
    "1.20":   "0.87.2+1.20",
    "1.20.1": "0.91.0+1.20.1",
    "1.20.2": "0.91.3+1.20.2",
    "1.20.3": "0.95.4+1.20.3",
    "1.20.4": "0.97.0+1.20.4",
    "1.20.5": "0.100.0+1.20.5",
    "1.20.6": "0.100.8+1.20.6",
    "1.21":   "0.100.7+1.21",
    "1.21.1": "0.102.0+1.21.1",
    "1.21.2": "0.103.0+1.21.2",
    "1.21.3": "0.104.0+1.21.3",
    "1.21.4": "0.118.0+1.21.4",
    "1.21.5": "0.115.0+1.21.5",
}

FABRIC_LOADER_VERSIONS = {
    "1.14.4": "0.11.3",
    "1.15":   "0.11.3",
    "1.15.2": "0.11.3",
    "1.16.1": "0.12.12",
    "1.16.3": "0.12.12",
    "1.16.4": "0.12.12",
    "1.16.5": "0.15.11",
    "1.17.1": "0.14.9",
    "1.18":   "0.14.9",
    "1.18.2": "0.14.9",
    "1.19":   "0.14.21",
    "1.19.2": "0.14.21",
    "1.19.3": "0.15.6",
    "1.19.4": "0.15.6",
    "1.20":   "0.15.6",
    "1.20.1": "0.15.6",
    "1.20.2": "0.15.11",
    "1.20.3": "0.16.0",
    "1.20.4": "0.16.0",
    "1.20.5": "0.16.0",
    "1.20.6": "0.16.0",
    "1.21":   "0.16.0",
    "1.21.1": "0.16.0",
    "1.21.2": "0.16.0",
    "1.21.3": "0.16.0",
    "1.21.4": "0.16.0",
    "1.21.5": "0.16.0",
}


# ============================================================
# FABRIC BUILD.GRADL
# ============================================================


def write_fabric_build(mc, info, path):
    java = info.get("java", 17)
    yarn = info.get("fabric_yarn", "")
    depth = maven_local_depth()
    loom_ver = get_fabric_loom(mc)
    # Unobfuscated MC 26.x: per-version loader pin, no yarn mappings exist.
    loader_ver = info.get("fabric_loader") or FABRIC_LOADER_VERSIONS.get(mc, "0.16.0")
    unobfuscated = bool(info.get("fabric_unobfuscated"))
    if loom_ver.startswith("0."):
        mod_cfg = "modImplementation"
        yarn_suffix = ":v2"
    else:
        mod_cfg = "modImplementation"
        yarn_suffix = ":v2"
    java_str = f"1.{java}" if java <= 11 else str(java)
    gradle_ver = _gradle_ver_for_fabric(mc)
    if gradle_ver.startswith("9."):
        java_block = f"""java {{
    toolchain {{
        languageVersion = JavaLanguageVersion.of({java})
    }}
}}"""
    else:
        java_block = f"""sourceCompatibility = "{java_str}"
targetCompatibility = "{java_str}" """

    if unobfuscated:
        # MC 26.x ships unobfuscated (intermediary pinned at 0.0.0): no
        # mappings dependency, plugin renamed to net.fabricmc.fabric-loom,
        # and no remapping means plain `implementation` replaces
        # `modImplementation` (matches the official fabric-example-mod).
        content = f"""plugins {{
    id "net.fabricmc.fabric-loom" version "{loom_ver}"
}}

version = "0.1.1-SNAPSHOT"
group = "xyz.langyo"

{java_block}

repositories {{
    mavenCentral()
    maven {{
        url = "{depth}.maven-local"
    }}
}}

sourceSets.main.java.srcDir '../../../common/src/main/java'

dependencies {{
    minecraft "com.mojang:minecraft:{mc}"
    implementation "net.fabricmc:fabric-loader:{loader_ver}"
    implementation "org.java-websocket:Java-WebSocket:1.5.4"
}}
"""
    else:
        content = f"""plugins {{
    id "fabric-loom" version "{loom_ver}"
}}

version = "0.1.1-SNAPSHOT"
group = "xyz.langyo"

{java_block}

repositories {{
    mavenCentral()
    maven {{
        url = "{depth}.maven-local"
    }}
}}

sourceSets.main.java.srcDir '../../../common/src/main/java'

dependencies {{
    minecraft "com.mojang:minecraft:{mc}"
    mappings "net.fabricmc:yarn:{yarn}{yarn_suffix}"
    {mod_cfg} "net.fabricmc:fabric-loader:{loader_ver}"
    implementation "org.java-websocket:Java-WebSocket:1.5.4"
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)
    with open(os.path.join(path, "gradle.properties"), "w") as f:
        f.write("org.gradle.jvmargs=-Xmx4g\n")


# ============================================================
# FABRIC SETTINGS.GRADL
# ============================================================


def write_fabric_settings(mc, info, path):
    content = f"""pluginManagement {{
    repositories {{
        maven {{ url = 'https://maven.fabricmc.net/' }}
        gradlePluginPortal()
    }}
}}

rootProject.name = 'minecraft-moddev-mcp-fabric-{mc}'
"""
    with open(os.path.join(path, "settings.gradle"), "w") as f:
        f.write(content)


def write_fabric_mod_json(mc, info, path):
    content = """{
  "schemaVersion": 1,
  "id": "mcpmod",
  "version": "0.1.1",
  "name": {
    "en_us": "ModDev MCP",
    "zh_cn": "ModDev MCP",
    "zh_tw": "ModDev MCP",
    "ja_jp": "ModDev MCP",
    "ko_kr": "ModDev MCP",
    "fr_fr": "ModDev MCP",
    "es_es": "ModDev MCP",
    "ru_ru": "ModDev MCP"
  },
  "description": {
    "en_us": "WebSocket bridge for AI agent interaction",
    "zh_cn": "\\u7528\\u4e8e AI \\u4ee3\\u7406\\u4ea4\\u4e92\\u7684 Minecraft WebSocket \\u6865\\u63a5\\u6a21\\u7ec4",
    "zh_tw": "\\u7528\\u65bc AI \\u4ee3\\u7406\\u4ea4\\u4e92\\u7684 Minecraft WebSocket \\u6a4b\\u63a5\\u6a21\\u7d44",
    "ja_jp": "AI\\u30a8\\u30fc\\u30b8\\u30a7\\u30f3\\u30c8\\u9023\\u643a\\u306e\\u305f\\u3081\\u306eMinecraft WebSocket\\u30d6\\u30ea\\u30c3\\u30b8MOD",
    "ko_kr": "AI \\uc5d0\\uc774\\uc804\\ud2b8 \\uc0c1\\ud638\\uc791\\uc6a9\\uc744 \\uc704\\ud55c Minecraft WebSocket \\ube0c\\ub9ac\\uc9c0 \\ubaa8\\ub4dc",
    "fr_fr": "Pont WebSocket pour l'interaction d'agents IA avec Minecraft",
    "es_es": "Puente WebSocket para la interacci\\u00f3n de agentes IA en Minecraft",
    "ru_ru": "WebSocket-\\u043c\\u043e\\u0441\\u0442 \\u0434\\u043b\\u044f \\u0432\\u0437\\u0430\\u0438\\u043c\\u043e\\u0434\\u0435\\u0439\\u0441\\u0442\\u0432\\u0438\\u044f AI-\\u0430\\u0433\\u0435\\u043d\\u0442\\u043e\\u0432 \\u0441 Minecraft"
  },
  "authors": ["langyo"],
  "environment": "client",
  "entrypoints": {
    "client": ["xyz.langyo.minecraft.mcp.mod.ModDevMcpMod"]
  }
}
"""
    os.makedirs(os.path.join(path, "src", "main", "resources"), exist_ok=True)
    with open(
        os.path.join(path, "src", "main", "resources", "fabric.mod.json"), "w"
    ) as f:
        f.write(content)
