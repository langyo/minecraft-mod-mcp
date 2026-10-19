"""NeoForge build.gradle / settings.gradle generators."""

import os

from version_config import FG_ERAS

from .gradle import maven_local_depth


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

version = '0.1.1-SNAPSHOT'
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
