"""Generate mod projects for all supported MC versions and loaders.

All version/era data comes from version_config.py — nothing is hardcoded here.

FG Era build.gradle templates:
  FG 1.2    (1.7.x)         → buildscript + apply "forge", Gradle 2.14
  FG 2.1/2.2/2.3 (1.8-1.12) → buildscript + apply "net.minecraftforge.gradle.forge"
  FG 3.x    (1.13-1.14)     → buildscript + apply "net.minecraftforge.gradle", Gradle 4.10
  FG 4.1    (1.15-1.16)     → buildscript + apply "net.minecraftforge.gradle", Gradle 6.9
  FG 5.1    (1.17-1.19)     → buildscript + apply, java.toolchain JDK 17, Gradle 7.6
  FG 6.x    (1.19.3-1.21.3) → buildscript + apply, workingDirectory, Gradle 8.5
  FG 7.x    (1.21.4+)       → buildscript + apply, Gradle 9.3

NeoForge ModDev:
  MDG 1.0.x → Gradle 8.10
  MDG 2.0.x → Gradle 9.3

Fabric Loom:
  0.12.x (1.14.4-1.16) → Gradle 8.10
  1.x    (1.17+)        → varies
"""

import os
import shutil

from version_config import (
    ALL_VERSIONS,
    FG_ERAS,
    MODS_DIR,
    BASE_DIR,
    get_loaders,
    get_fg_era,
    get_fabric_loom,
    get_neoforge_gradle,
)

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
    "1.16.5": "0.12.12",
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

WRAPPER_JAR = os.path.join(
    os.environ.get("TEMP", "/tmp"), "opencode", "gradle-wrapper.jar"
)


def maven_local_depth():
    return "../../../"


def _gradle_ver_for_forge(era_key):
    return FG_ERAS[era_key]["gradle"]


def _gradle_ver_for_fabric(mc):
    loom = get_fabric_loom(mc)
    if loom.startswith("0."):
        return "7.6.4"
    return "8.10"


def _gradle_ver_for_neoforge(mc):
    _, gradle = get_neoforge_gradle(mc)
    return gradle if gradle else "8.10"


def copy_wrapper(path, gradle_ver):
    gw = os.path.join(path, "gradle", "wrapper")
    os.makedirs(gw, exist_ok=True)
    dst = os.path.join(gw, "gradle-wrapper.jar")
    try:
        shutil.copy2(WRAPPER_JAR, dst)
    except (OSError, PermissionError):
        try:
            os.remove(dst)
        except OSError:
            pass
        shutil.copy2(WRAPPER_JAR, dst)
    with open(os.path.join(gw, "gradle-wrapper.properties"), "w") as f:
        f.write("distributionBase=GRADLE_USER_HOME\n")
        f.write("distributionPath=wrapper/dists\n")
        f.write(
            f"distributionUrl=https\\://services.gradle.org/distributions/gradle-{gradle_ver}-bin.zip\n"
        )
        f.write("networkTimeout=10000\n")
        f.write("validateDistributionUrl=true\n")
        f.write("zipStoreBase=GRADLE_USER_HOME\n")
        f.write("zipStorePath=wrapper/dists\n")


# ============================================================
# FORGE BUILD.GRADL — FG 1.2
# ============================================================



# ============================================================
# FORGE BUILD.GRADLE — FG 2.1 / 2.2 / 2.3
# ============================================================


def write_forge_build_fg2(mc, info, path):
    java = info.get("java", 8)
    forge_ver = info.get("forge", "")
    mappings = info.get("mappings", "stable_12")
    era = get_fg_era(mc)
    fg_ver = era["fg_version"] if era else "2.3-SNAPSHOT"
    depth = maven_local_depth()
    content = f"""buildscript {{
    repositories {{
        maven {{ url = "https://maven.minecraftforge.net/" }}
        mavenCentral()
    }}
    dependencies {{
        classpath "net.minecraftforge.gradle:ForgeGradle:{fg_ver}"
    }}
}}

apply plugin: "net.minecraftforge.gradle.forge"

version = "0.1.0-SNAPSHOT"
group = "xyz.langyo"
archivesBaseName = "minecraft-moddev-mcp-mod"

sourceCompatibility = targetCompatibility = "1.{java}"

minecraft {{
    version = "{forge_ver}"
    mappings = "{mappings}"
    runDir = "run"
}}

repositories {{
    mavenCentral()
    maven {{
        url = "{depth}.maven-local"
    }}
}}

sourceSets.main.java.srcDir '../../../common/src/main/java'

dependencies {{
    compile "org.java-websocket:Java-WebSocket:1.5.4"
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)


# ============================================================
# FORGE BUILD.GRADL — FG 3.x
# ============================================================


def _split_mappings(mappings_str):
    parts = mappings_str.split("_", 1)
    if len(parts) == 2:
        return parts[0], parts[1]
    return "snapshot", mappings_str


def write_forge_build_fg3(mc, info, path):
    java = info.get("java", 8)
    forge_ver = info.get("forge", "")
    mappings = info.get("mappings", "snapshot_20190314")
    mchannel, mversion = _split_mappings(mappings)
    mversion_full = mversion if (mchannel == "official" or "-" in mversion) else f"{mversion}-{mc}"
    depth = maven_local_depth()
    content = f"""buildscript {{
    repositories {{
        maven {{ url = "https://maven.minecraftforge.net/" }}
        mavenCentral()
    }}
    dependencies {{
        classpath "net.minecraftforge.gradle:ForgeGradle:3.+"
    }}
}}

apply plugin: "net.minecraftforge.gradle"

version = "0.1.0-SNAPSHOT"
group = "xyz.langyo"
archivesBaseName = "minecraft-moddev-mcp-mod"

sourceCompatibility = targetCompatibility = "1.8"

minecraft {{
    mappings channel: "{mchannel}", version: "{mversion_full}"
    runs {{
        client {{
            workingDirectory = file("run")
        }}
    }}
}}

repositories {{
    mavenCentral()
    maven {{
        url = "{depth}.maven-local"
    }}
}}

sourceSets.main.java.srcDir '../../../common/src/main/java'

dependencies {{
    minecraft "net.minecraftforge:forge:{forge_ver}"
    compile "org.java-websocket:Java-WebSocket:1.5.4"
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)
    with open(os.path.join(path, "gradle.properties"), "w") as f:
        f.write("org.gradle.jvmargs=-Xmx3g\n")


# ============================================================
# FORGE BUILD.GRADL — FG 4.1
# ============================================================


def write_forge_build_fg41(mc, info, path):
    java = info.get("java", 8)
    forge_ver = info.get("forge", "")
    mappings = info.get("mappings", "snapshot_20200126")
    mchannel, mversion = _split_mappings(mappings)
    mversion_full = mversion if (mchannel == "official" or "-" in mversion) else f"{mversion}-{mc}"
    depth = maven_local_depth()
    fg_ver = FG_ERAS["fg41"]["fg_version"]
    content = f"""buildscript {{
    repositories {{
        maven {{ url = "https://maven.minecraftforge.net/" }}
        mavenCentral()
    }}
    dependencies {{
        classpath "net.minecraftforge.gradle:ForgeGradle:{fg_ver}"
    }}
}}

apply plugin: "net.minecraftforge.gradle"

version = "0.1.0-SNAPSHOT"
group = "xyz.langyo"
archivesBaseName = "minecraft-moddev-mcp-mod"

sourceCompatibility = targetCompatibility = "1.8"

minecraft {{
    mappings channel: "{mchannel}", version: "{mversion_full}"
    runs {{
        client {{
            workingDirectory = file("run")
        }}
    }}
}}

repositories {{
    mavenCentral()
    maven {{
        url = "{depth}.maven-local"
    }}
}}

sourceSets.main.java.srcDir '../../../common/src/main/java'

dependencies {{
    minecraft "net.minecraftforge:forge:{forge_ver}"
    implementation "org.java-websocket:Java-WebSocket:1.5.4"
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)
    with open(os.path.join(path, "gradle.properties"), "w") as f:
        f.write("org.gradle.jvmargs=-Xmx3g\n")


# ============================================================
# FORGE BUILD.GRADL — FG 5.1
# ============================================================


def write_forge_build_fg51(mc, info, path):
    java = info.get("java", 17)
    forge_ver = info.get("forge", "")
    depth = maven_local_depth()
    content = f"""buildscript {{
    repositories {{
        maven {{ url = "https://maven.minecraftforge.net/" }}
        mavenCentral()
    }}
    dependencies {{
        classpath "net.minecraftforge.gradle:ForgeGradle:5.1.+"
    }}
}}

apply plugin: "net.minecraftforge.gradle"

version = "0.1.0-SNAPSHOT"
group = "xyz.langyo"
archivesBaseName = "minecraft-moddev-mcp-mod"

java.toolchain.languageVersion = JavaLanguageVersion.of({java})

minecraft {{
    mappings channel: "official", version: "{mc}"
    runs {{
        client {{
            workingDirectory = file("run")
            property "forge.logging.markers", "REGISTRIES"
        }}
    }}
}}

repositories {{
    mavenCentral()
    maven {{
        url = "{depth}.maven-local"
    }}
}}

sourceSets.main.java.srcDir '../../../common/src/main/java'

dependencies {{
    minecraft "net.minecraftforge:forge:{forge_ver}"
    implementation "org.java-websocket:Java-WebSocket:1.5.4"
}}

tasks.withType(JavaCompile).configureEach {{
    options.encoding = "UTF-8"
}}

jar {{
    manifest {{
        attributes "FMLCorePluginContainsFMLMod": "true"
    }}
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)


# ============================================================
# FORGE BUILD.GRADL — FG 6.x
# ============================================================


def write_forge_build_fg6(mc, info, path):
    java = info.get("java", 17)
    forge_ver = info.get("forge", "")
    fg = FG_ERAS[info.get("fg_era", "fg6")]["fg_version"]
    depth = maven_local_depth()
    content = f"""plugins {{
    id 'java'
    id 'idea'
    id 'eclipse'
    id 'net.minecraftforge.gradle' version '{fg}'
}}

version = '0.1.0-SNAPSHOT'
group = 'xyz.langyo'

java.toolchain.languageVersion = JavaLanguageVersion.of({java})

minecraft {{
    mappings channel: 'official', version: '{mc}'
    runs {{
        configureEach {{
            property 'forge.logging.markers', 'REGISTRIES'
            property 'forge.logging.console.level', 'debug'
        }}
        client {{
            workingDirectory = file('run')
        }}
    }}
}}

repositories {{
    mavenCentral()
    maven {{
        url = rootProject.projectDir.toPath().resolve('{depth}.maven-local').toUri()
        allowInsecureProtocol = false
    }}
}}

sourceSets.main.java.srcDir '../../../common/src/main/java'

dependencies {{
    minecraft "net.minecraftforge:forge:{forge_ver}"
    implementation 'org.java-websocket:Java-WebSocket:1.5.4'
}}

tasks.withType(JavaCompile).configureEach {{
    options.encoding = 'UTF-8'
}}

jar {{
    manifest {{
        attributes 'Automatic-Module': 'xyz.langyo.minecraft.mcp.mod'
    }}
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)


# ============================================================
# FORGE BUILD.GRADL — FG 7.x
# ============================================================


def write_forge_build_fg7(mc, info, path):
    java = info.get("java", 21)
    forge_ver = info.get("forge", "")
    fg = info.get("fg", "[7.0.23,8)")
    if "fg_era" in info:
        fg = FG_ERAS[info["fg_era"]]["fg_version"]
    depth = maven_local_depth()
    content = f"""plugins {{
    id 'java'
    id 'idea'
    id 'eclipse'
    id 'net.minecraftforge.gradle' version '{fg}'
}}

version = '0.1.0-SNAPSHOT'
group = 'xyz.langyo'

java.toolchain.languageVersion = JavaLanguageVersion.of({java})

minecraft {{
    runs {{
        configureEach {{
            workingDir = layout.projectDirectory.dir('run')
            systemProperty 'eventbus.api.strictRuntimeChecks', 'true'
            systemProperty 'forge.enabledGameTestNamespaces', 'mcpmod'
        }}
        register('client')
    }}
}}

repositories {{
    minecraft.mavenizer(it)
    maven fg.forgeMaven
    maven fg.minecraftLibsMaven
    mavenCentral()
    maven {{
        url = rootProject.projectDir.toPath().resolve('{depth}.maven-local').toUri()
        allowInsecureProtocol = false
    }}
}}

sourceSets.main.java.srcDir '../../../common/src/main/java'

dependencies {{
    implementation minecraft.dependency('net.minecraftforge:forge:{forge_ver}')
    implementation 'org.java-websocket:Java-WebSocket:1.5.4'
}}

tasks.withType(JavaCompile).configureEach {{
    options.encoding = 'UTF-8'
}}

jar {{
    manifest {{
        attributes 'ModSide' : 'BOTH'
        attributes 'Automatic-Module' : 'xyz.langyo.minecraft.mcp.mod'
        attributes 'Implementation-Version' : project.version
    }}
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)


# ============================================================
# FORGE SETTINGS.GRADL — legacy (FG 1.2 – 4.1)
# ============================================================


def write_forge_settings_legacy(mc, info, path):
    content = f"""rootProject.name = 'minecraft-moddev-mcp-forge-{mc}'
"""
    with open(os.path.join(path, "settings.gradle"), "w") as f:
        f.write(content)


# ============================================================
# FORGE SETTINGS.GRADL — FG 5.1+
# ============================================================


def write_forge_settings_modern(mc, info, path):
    content = f"""pluginManagement {{
    repositories {{
        gradlePluginPortal()
        maven {{ url = 'https://maven.minecraftforge.net/' }}
    }}
}}

plugins {{
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.9.0'
}}

rootProject.name = 'minecraft-moddev-mcp-forge-{mc}'
"""
    with open(os.path.join(path, "settings.gradle"), "w") as f:
        f.write(content)


# ============================================================
# NEOFORGE BUILD.GRADL — FG 6 style (MC 1.20.1 only)
# ============================================================


def write_neoforge_build_fg6(mc, info, path):
    java = info.get("java", 17)
    nf_ver = info.get("neoforge", "")
    fg = FG_ERAS["fg6"]["fg_version"]
    depth = maven_local_depth()
    if nf_ver.startswith("1.20.1-"):
        nf_group = "net.neoforged:forge"
    else:
        nf_group = "net.neoforged:neoforge"
    content = f"""plugins {{
    id 'java'
    id 'idea'
    id 'eclipse'
    id 'net.minecraftforge.gradle' version '{fg}'
}}

version = '0.1.0-SNAPSHOT'
group = 'xyz.langyo'

java.toolchain.languageVersion = JavaLanguageVersion.of({java})

minecraft {{
    mappings channel: 'official', version: '{mc}'
    runs {{
        configureEach {{
            property 'forge.logging.markers', 'REGISTRIES'
            property 'forge.logging.console.level', 'debug'
        }}
        client {{
            workingDirectory = file('run')
        }}
    }}
}}

repositories {{
    mavenCentral()
    maven {{
        url = 'https://maven.neoforged.net/releases'
    }}
    maven {{
        url = rootProject.projectDir.toPath().resolve('{depth}.maven-local').toUri()
        allowInsecureProtocol = false
    }}
}}

sourceSets.main.java.srcDir '../../../common/src/main/java'

dependencies {{
    minecraft "{nf_group}:{nf_ver}"
    implementation 'org.java-websocket:Java-WebSocket:1.5.4'
}}

tasks.withType(JavaCompile).configureEach {{
    options.encoding = 'UTF-8'
}}

jar {{
    manifest {{
        attributes 'Automatic-Module': 'xyz.langyo.minecraft.mcp.mod'
    }}
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)


# ============================================================
# NEOFORGE BUILD.GRADL — MDG style (MC 1.20.2+)
# ============================================================


def write_neoforge_build(mc, info, path):
    java = info.get("java", 21)
    nf_ver = info.get("neoforge", "")
    mdg = info.get("mdg", "2.0.141")
    depth = maven_local_depth()
    content = f"""plugins {{
    id 'java'
    id 'idea'
    id 'net.neoforged.moddev' version '{mdg}'
}}

version = '0.1.0-SNAPSHOT'
group = 'xyz.langyo'

java.toolchain.languageVersion = JavaLanguageVersion.of({java})

repositories {{
    maven {{
        url = rootProject.projectDir.toPath().resolve('{depth}.maven-local').toUri()
        allowInsecureProtocol = false
    }}
    mavenCentral()
}}

sourceSets.main.java.srcDir '../../../common/src/main/java'

dependencies {{
    implementation 'org.java-websocket:Java-WebSocket:1.5.4'
}}

neoForge {{
    version = "{nf_ver}"
    runs {{
        configureEach {{
            gameDirectory = project.file('run')
        }}
        client {{
            client()
        }}
    }}
    mods {{
        mcpmod {{
            sourceSet sourceSets.main
        }}
    }}
}}

tasks.withType(JavaCompile).configureEach {{
    options.encoding = 'UTF-8'
}}

jar {{
    manifest {{
        attributes 'ModSide' : 'CLIENT'
        attributes 'Automatic-Module' : 'xyz.langyo.minecraft.mcp.mod'
        attributes 'Implementation-Version' : project.version
    }}
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)


# ============================================================
# NEOFORGE SETTINGS.GRADL
# ============================================================


def write_neoforge_settings(mc, info, path):
    content = f"""pluginManagement {{
    repositories {{
        gradlePluginPortal()
        maven {{ url = 'https://maven.neoforged.net/releases' }}
    }}
}}

plugins {{
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.9.0'
}}

rootProject.name = 'minecraft-moddev-mcp-neoforge-{mc}'
"""
    with open(os.path.join(path, "settings.gradle"), "w") as f:
        f.write(content)


# ============================================================
# FABRIC BUILD.GRADL
# ============================================================


def write_fabric_build(mc, info, path):
    java = info.get("java", 17)
    yarn = info.get("fabric_yarn", "")
    depth = maven_local_depth()
    loom_ver = get_fabric_loom(mc)
    loader_ver = FABRIC_LOADER_VERSIONS.get(mc, "0.16.0")
    if loom_ver.startswith("0."):
        mod_cfg = "modImplementation"
        yarn_suffix = ":v2"
    else:
        mod_cfg = "modImplementation"
        yarn_suffix = ":v2"
    java_str = f"1.{java}" if java <= 11 else str(java)
    content = f"""plugins {{
    id "fabric-loom" version "{loom_ver}"
}}

version = "0.1.0-SNAPSHOT"
group = "xyz.langyo"

sourceCompatibility = "{java_str}"
targetCompatibility = "{java_str}"

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
  "version": "0.1.0",
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


# ============================================================
# BUILD / SETTINGS DISPATCH
# ============================================================

_FORGE_BUILD_FNS = {
    "fg21": write_forge_build_fg2,
    "fg22": write_forge_build_fg2,
    "fg23": write_forge_build_fg2,
    "fg3":  write_forge_build_fg3,
    "fg41": write_forge_build_fg41,
    "fg51": write_forge_build_fg51,
    "fg6":  write_forge_build_fg6,
    "fg7":  write_forge_build_fg7,
}

_FORGE_SETTINGS_LEGACY_ERAS = {"fg21", "fg22", "fg23", "fg3", "fg41"}


def _get_gradle_ver(mc, loader, info):
    if loader == "fabric":
        return _gradle_ver_for_fabric(mc)
    if loader == "neoforge":
        return _gradle_ver_for_neoforge(mc)
    era_key = info.get("fg_era", "")
    if era_key in FG_ERAS:
        return _gradle_ver_for_forge(era_key)
    return "8.10"


def create_mod(mc, loader, info):
    base = os.path.join(MODS_DIR, mc, loader)
    os.makedirs(base, exist_ok=True)
    gradle_ver = _get_gradle_ver(mc, loader, info)
    copy_wrapper(base, gradle_ver)

    if loader == "forge":
        era_key = info.get("fg_era", "")
        if era_key in _FORGE_SETTINGS_LEGACY_ERAS:
            write_forge_settings_legacy(mc, info, base)
        else:
            write_forge_settings_modern(mc, info, base)
        build_fn = _FORGE_BUILD_FNS.get(era_key)
        if build_fn:
            build_fn(mc, info, base)
    elif loader == "neoforge":
        style = info.get("neoforge_style", "mdg")
        if style == "fg6":
            era_key = info.get("fg_era", "fg6")
            if era_key in _FORGE_SETTINGS_LEGACY_ERAS:
                write_forge_settings_legacy(mc, info, base)
            else:
                write_forge_settings_modern(mc, info, base)
            write_neoforge_build_fg6(mc, info, base)
        else:
            write_neoforge_settings(mc, info, base)
            write_neoforge_build(mc, info, base)
    elif loader == "fabric":
        write_fabric_settings(mc, info, base)
        write_fabric_build(mc, info, base)
        write_fabric_mod_json(mc, info, base)

    return base


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":
    total = 0
    for mc, info in ALL_VERSIONS.items():
        loaders = get_loaders(mc)
        for loader in loaders:
            path = create_mod(mc, loader, info)
            total += 1
            print(
                f"  Created: {mc}/{loader} (Gradle {_get_gradle_ver(mc, loader, info)})"
            )
    print(f"\nTotal: {total} mod projects created")
