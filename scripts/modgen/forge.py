"""Forge build.gradle / settings.gradle generators (FG 1.2 through FG 7)."""

import os

from version_config import FG_ERAS, get_fg_era

from .gradle import maven_local_depth


# ============================================================
# FORGE BUILD.GRADLE — FG 1.2 (GTNH fork)
# ============================================================


def write_forge_build_fg12(mc, info, path):
    forge_ver = info.get("forge", "")
    depth = maven_local_depth()
    content = f"""buildscript {{
    repositories {{
        mavenCentral()
        maven {{ url = "https://maven.minecraftforge.net/" }}
        maven {{ url = "https://jitpack.io" }}
    }}
    dependencies {{
        classpath "com.github.GTNewHorizons:ForgeGradle:1.2.11"
    }}
}}

apply plugin: "forge"

version = "0.1.1-SNAPSHOT"
group = "xyz.langyo"
archivesBaseName = "minecraft-moddev-mcp-mod"

sourceCompatibility = targetCompatibility = "1.8"

minecraft {{
    version = "{forge_ver}"
    runDir = "run"
}}

repositories {{
    mavenCentral()
    maven {{
        url = "{depth}.maven-local"
    }}
}}

sourceSets.main.java.srcDir '../../../common/src/main/java'
sourceSets.main.resources.srcDir '../../../common/src/main/resources'

dependencies {{
    compile "org.java-websocket:Java-WebSocket:1.5.4"
    compile 'net.java.dev.jna:jna:5.15.0'
    compile 'net.java.dev.jna:jna-platform:5.15.0'
}}
"""
    with open(os.path.join(path, "build.gradle"), "w", encoding="utf-8") as f:
        f.write(content)



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

version = "0.1.1-SNAPSHOT"
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

jar {{
    manifest {{
        attributes 'Implementation-Title': 'minecraft-moddev-mcp-mod'
        attributes 'Implementation-Version': project.version
    }}
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

version = "0.1.1-SNAPSHOT"
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

version = "0.1.1-SNAPSHOT"
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

version = "0.1.1-SNAPSHOT"
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

version = '0.1.1-SNAPSHOT'
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

version = '0.1.1-SNAPSHOT'
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
