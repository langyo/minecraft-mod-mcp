"""Generate mod projects for all supported MC versions and loaders.

ForgeGradle version compatibility:
  FG 1.2   (1.7.x)    → buildscript {}, Gradle 2.14,  Java 8
  FG 2.3   (1.8-1.12) → buildscript {}, Gradle 4.10.3, Java 8
  FG 3.+   (1.13.2)   → buildscript {}, Gradle 4.10.3, Java 8
  FG 4.1   (1.15-1.16)→ buildscript {}, Gradle 4.10.3, Java 8/16
  FG 5.1   (1.17-1.19)→ buildscript {}, Gradle 8.5,    Java 16/17
  FG [6,7) (1.19.3-1.21.3) → plugins {}, Gradle 8.5, Java 17/21
  FG [7.0,8) (1.21.4+) → plugins {}, Gradle 9.3.1, Java 21/25

NeoForge ModDev:
  MDG 1.0.x → Gradle 8.10
  MDG 2.0.x → Gradle 9.3.1

Fabric Loom:
  0.12.x (1.14.4-1.16) → Gradle 8.10, Loom 0.12
  1.x    (1.17+)        → Gradle 8.10, Loom 1.7
"""
import os, shutil

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS_DIR = os.path.join(BASE, "mods")

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

FABRIC_LOOM_VERSIONS = {
    "1.14.4": "0.12-SNAPSHOT",
    "1.15":   "0.12-SNAPSHOT",
    "1.15.2": "0.12-SNAPSHOT",
    "1.16.1": "0.12-SNAPSHOT",
    "1.16.3": "0.12-SNAPSHOT",
    "1.16.4": "0.12-SNAPSHOT",
    "1.16.5": "0.12-SNAPSHOT",
    "1.17.1": "1.0-SNAPSHOT",
    "1.18":   "1.0-SNAPSHOT",
    "1.18.2": "1.0-SNAPSHOT",
    "1.19":   "1.3-SNAPSHOT",
    "1.19.2": "1.3-SNAPSHOT",
    "1.19.3": "1.3-SNAPSHOT",
    "1.19.4": "1.3-SNAPSHOT",
    "1.20":   "1.3-SNAPSHOT",
    "1.20.1": "1.3-SNAPSHOT",
    "1.20.2": "1.4-SNAPSHOT",
    "1.20.3": "1.4-SNAPSHOT",
    "1.20.4": "1.4-SNAPSHOT",
    "1.20.5": "1.5-SNAPSHOT",
    "1.20.6": "1.5-SNAPSHOT",
    "1.21":   "1.7-SNAPSHOT",
    "1.21.1": "1.7-SNAPSHOT",
    "1.21.2": "1.7-SNAPSHOT",
    "1.21.3": "1.7-SNAPSHOT",
    "1.21.4": "1.7-SNAPSHOT",
    "1.21.5": "1.7-SNAPSHOT",
}

FORGE_MAPPINGS_SNAPSHOT = {
    "1.7.2":  "snapshot_20140918",
    "1.7.10": "snapshot_20140918",
    "1.8":    "snapshot_20141103",
    "1.8.9":  "snapshot_20160113",
    "1.9":    "snapshot_20160318",
    "1.9.4":  "snapshot_20160512",
    "1.10":   "snapshot_20160518",
    "1.10.2": "snapshot_20160518",
    "1.11":   "snapshot_20161117",
    "1.11.2": "snapshot_20161220",
    "1.12":   "snapshot_20171003",
    "1.12.2": "snapshot_20171003",
}

WRAPPER_JAR = os.path.join(os.environ.get("TEMP", "/tmp"), "opencode", "gradle-wrapper.jar")

ALL_VERSIONS = {
    "1.7.2":  {"forge":"1.7.2-10.12.2.1161-mc172","java":8,"fg":"1.2"},
    "1.7.10": {"forge":"1.7.10-10.13.4.1614-1.7.10","java":8,"fg":"1.2"},
    "1.8":    {"forge":"1.8-11.14.4.1577","java":8,"fg":"2.3"},
    "1.8.9":  {"forge":"1.8.9-11.15.1.2318-1.8.9","java":8,"fg":"2.3"},
    "1.9":    {"forge":"1.9-12.16.1.1938-1.9.0","java":8,"fg":"2.3"},
    "1.9.4":  {"forge":"1.9.4-12.17.0.2317-1.9.4","java":8,"fg":"2.3"},
    "1.10":   {"forge":"1.10-12.18.0.2000-1.10.0","java":8,"fg":"2.3"},
    "1.10.2": {"forge":"1.10.2-12.18.3.2511","java":8,"fg":"2.3"},
    "1.11":   {"forge":"1.11-13.19.1.2199","java":8,"fg":"2.3"},
    "1.11.2": {"forge":"1.11.2-13.20.1.2588","java":8,"fg":"2.3"},
    "1.12":   {"forge":"1.12-14.21.1.2443","java":8,"fg":"2.3"},
    "1.12.2": {"forge":"1.12.2-14.23.5.2864","java":8,"fg":"2.3"},
    "1.13.2": {"forge":"1.13.2-25.0.223","java":8,"fg":"3.+"},
    "1.14.4": {"forge":"1.14.4-28.2.28","fabric_yarn":"1.14.4+build.18","java":8,"fg":"3.+"},
    "1.15":   {"forge":"1.15-29.0.4","fabric_yarn":"1.15+build.2","java":8,"fg":"4.1"},
    "1.15.2": {"forge":"1.15.2-31.2.60","fabric_yarn":"1.15.2+build.17","java":8,"fg":"4.1"},
    "1.16.1": {"forge":"1.16.1-32.0.108","fabric_yarn":"1.16.1+build.21","java":16,"fg":"5.1"},
    "1.16.3": {"forge":"1.16.3-34.1.42","fabric_yarn":"1.16.3+build.47","java":16,"fg":"5.1"},
    "1.16.4": {"forge":"1.16.4-35.1.37","fabric_yarn":"1.16.4+build.10","java":16,"fg":"5.1"},
    "1.16.5": {"forge":"1.16.5-36.2.42","fabric_yarn":"1.16.5+build.10","java":16,"fg":"5.1"},
    "1.17.1": {"forge":"1.17.1-37.1.1","fabric_yarn":"1.17.1+build.65","java":16,"fg":"5.1"},
    "1.18":   {"forge":"1.18-38.0.17","fabric_yarn":"1.18+build.1","java":17,"fg":"5.1"},
    "1.18.2": {"forge":"1.18.2-40.3.12","fabric_yarn":"1.18.2+build.4","java":17,"fg":"5.1"},
    "1.19":   {"forge":"1.19-41.1.0","fabric_yarn":"1.19+build.4","java":17,"fg":"5.1"},
    "1.19.2": {"forge":"1.19.2-43.5.2","fabric_yarn":"1.19.2+build.28","java":17,"fg":"5.1"},
    "1.19.3": {"forge":"1.19.3-44.1.23","fabric_yarn":"1.19.3+build.4","java":17,"fg":"[6.0,6.2)"},
    "1.19.4": {"forge":"1.19.4-45.4.3","fabric_yarn":"1.19.4+build.5","java":17,"fg":"[6.0,6.2)"},
    "1.20":   {"forge":"1.20-46.0.14","fabric_yarn":"1.20+build.1","java":17,"fg":"[6.0,6.2)"},
    "1.20.1": {"forge":"1.20.1-47.4.20","neoforge":"21.0.167","fabric_yarn":"1.20.1+build.10","java":17,"fg":"[6.0,6.2)","mdg":"1.0.11"},
    "1.20.2": {"forge":"1.20.2-48.1.0","neoforge":"20.2.93","fabric_yarn":"1.20.2+build.1","java":17,"fg":"[6.0,6.2)","mdg":"1.0.11"},
    "1.20.3": {"forge":"1.20.3-49.0.2","neoforge":"20.3.8-beta","fabric_yarn":"1.20.3+build.1","java":17,"fg":"[6.0,6.2)","mdg":"1.0.11"},
    "1.20.4": {"forge":"1.20.4-49.2.7","neoforge":"20.4.251","fabric_yarn":"1.20.4+build.3","java":21,"fg":"[6.0,6.2)","mdg":"1.0.11"},
    "1.20.5": {"neoforge":"20.5.21-beta","fabric_yarn":"1.20.5+build.1","java":21,"mdg":"2.0.141"},
    "1.20.6": {"forge":"1.20.6-50.2.8","neoforge":"20.6.139","fabric_yarn":"1.20.6+build.1","java":21,"fg":"[6.0,6.2)","mdg":"2.0.141"},
    "1.21":   {"forge":"1.21-51.0.33","fabric_yarn":"1.21+build.9","java":21,"fg":"[6.0,6.2)"},
    "1.21.1": {"forge":"1.21.1-52.1.14","neoforge":"21.1.228","fabric_yarn":"1.21.1+build.10","java":21,"fg":"[6.0,6.2)","mdg":"2.0.141"},
    "1.21.2": {"neoforge":"21.2.1-beta","fabric_yarn":"1.21.2+build.1","java":21,"mdg":"2.0.141"},
    "1.21.3": {"forge":"1.21.3-53.1.10","neoforge":"21.3.96","fabric_yarn":"1.21.3+build.6","java":21,"fg":"[7.0.23,8)","mdg":"2.0.141"},
    "1.21.4": {"forge":"1.21.4-54.1.16","neoforge":"21.4.157","fabric_yarn":"1.21.4+build.8","java":21,"fg":"[7.0.23,8)","mdg":"2.0.141"},
    "1.21.5": {"forge":"1.21.5-55.1.10","neoforge":"21.5.97","fabric_yarn":"1.21.5+build.1","java":21,"fg":"[7.0.23,8)","mdg":"2.0.141"},
    "26.1":   {"forge":"26.1-62.0.9","java":25,"fg":"[7.0.23,8)"},
    "26.1.1": {"forge":"26.1.1-63.0.2","neoforge":"26.1.1.15-beta","java":25,"fg":"[7.0.23,8)","mdg":"2.0.141"},
    "26.1.2": {"forge":"26.1.2-64.0.8","neoforge":"26.1.2.36-beta","java":25,"fg":"[7.0.23,8)","mdg":"2.0.141"},
}

def get_gradle_ver(mc, loader, info):
    fg = info.get("fg", "")
    mdg = info.get("mdg", "")
    if loader == "fabric":
        return "8.10"
    if loader == "neoforge":
        return "9.3.1" if "2.0" in mdg else "8.10"
    if loader == "forge":
        if fg == "1.2":
            return "2.14"
        if fg == "2.3":
            return "4.10.3"
        if fg == "3.+":
            return "4.10.3"
        if fg == "4.1":
            return "6.9.4"
        if fg == "5.1":
            return "7.6.4"
        if fg == "[6.0,6.2)":
            return "8.5"
        if "[7.0" in fg:
            return "9.3.1"
    return "8.10"


def maven_local_depth():
    return "../../../"


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
        f.write(f"distributionUrl=https\\://services.gradle.org/distributions/gradle-{gradle_ver}-bin.zip\n")
        f.write("networkTimeout=10000\n")
        f.write("validateDistributionUrl=true\n")
        f.write("zipStoreBase=GRADLE_USER_HOME\n")
        f.write("zipStorePath=wrapper/dists\n")


# ============================================================
# FORGE BUILD GENERATION
# ============================================================

def write_forge_settings_legacy(mc, info, path):
    content = f"""rootProject.name = 'minecraft-mcp-forge-{mc}'
"""
    with open(os.path.join(path, "settings.gradle"), "w") as f:
        f.write(content)


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

rootProject.name = 'minecraft-mcp-forge-{mc}'
"""
    with open(os.path.join(path, "settings.gradle"), "w") as f:
        f.write(content)


def write_forge_build_fg12(mc, info, path):
    java = info.get("java", 8)
    forge_ver = info.get("forge", "")
    mappings = FORGE_MAPPINGS_SNAPSHOT.get(mc, "stable_12")
    depth = maven_local_depth()
    content = f"""buildscript {{
    repositories {{
        maven {{ url = "https://maven.minecraftforge.net/" }}
        mavenCentral()
    }}
    dependencies {{
        classpath "net.minecraftforge.gradle:ForgeGradle:1.2-"
    }}
}}

apply plugin: "net.minecraftforge.gradle.forge"

version = "1.0.0-SNAPSHOT"
group = "xyz.langyo"
archivesBaseName = "minecraft-mcp-mod"

sourceCompatibility = targetCompatibility = "1.{java}"

minecraft {{
    version = "{forge_ver}"
    mappings = "{mappings}"
    runDir = "run"
}}

repositories {{
    mavenCentral()
    flatDir {{
        dirs "{depth}.maven-local/com/mcbbs/mcp/mcp-common/1.0.0-SNAPSHOT"
    }}
}}

dependencies {{
    implementation name: "mcp-common"
    implementation "org.java-websocket:Java-WebSocket:1.5.4"
}}

tasks.withType(JavaCompile) {{
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


def write_forge_build_fg23(mc, info, path):
    java = info.get("java", 8)
    forge_ver = info.get("forge", "")
    mappings = FORGE_MAPPINGS_SNAPSHOT.get(mc, "snapshot_20171003")
    depth = maven_local_depth()
    content = f"""buildscript {{
    repositories {{
        maven {{ url = "https://maven.minecraftforge.net/" }}
        mavenCentral()
    }}
    dependencies {{
        classpath "net.minecraftforge.gradle:ForgeGradle:2.3-SNAPSHOT"
    }}
}}

apply plugin: "net.minecraftforge.gradle.forge"

version = "1.0.0-SNAPSHOT"
group = "xyz.langyo"
archivesBaseName = "minecraft-mcp-mod"

sourceCompatibility = targetCompatibility = "1.{java}"

minecraft {{
    version = "{forge_ver}"
    mappings = "{mappings}"
    runDir = "run"
}}

repositories {{
    mavenCentral()
    flatDir {{
        dirs "{depth}.maven-local/com/mcbbs/mcp/mcp-common/1.0.0-SNAPSHOT"
    }}
}}

dependencies {{
    implementation name: "mcp-common"
    implementation "org.java-websocket:Java-WebSocket:1.5.4"
}}

tasks.withType(JavaCompile) {{
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


def write_forge_build_fg3(mc, info, path):
    java = info.get("java", 8)
    forge_ver = info.get("forge", "")
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

version = "1.0.0-SNAPSHOT"
group = "xyz.langyo"
archivesBaseName = "minecraft-mcp-mod"

sourceCompatibility = targetCompatibility = "1.{java}"

minecraft {{
    mappings channel: "snapshot", version: "20190719-1.14.3"
    runs {{
        client {{ workingDirectory = file("run") }}
    }}
}}

repositories {{
    mavenCentral()
    flatDir {{
        dirs "{depth}.maven-local/com/mcbbs/mcp/mcp-common/1.0.0-SNAPSHOT"
    }}
}}

dependencies {{
    minecraft "net.minecraftforge:forge:{forge_ver}"
    implementation name: "mcp-common"
    implementation "org.java-websocket:Java-WebSocket:1.5.4"
}}

tasks.withType(JavaCompile) {{
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


def write_forge_build_fg4(mc, info, path):
    java = info.get("java", 8)
    forge_ver = info.get("forge", "")
    depth = maven_local_depth()
    content = f"""buildscript {{
    repositories {{
        maven {{ url = "https://maven.minecraftforge.net/" }}
        mavenCentral()
    }}
    dependencies {{
        classpath "net.minecraftforge.gradle:ForgeGradle:4.1.+"
    }}
}}

apply plugin: "net.minecraftforge.gradle"

version = "1.0.0-SNAPSHOT"
group = "xyz.langyo"
archivesBaseName = "minecraft-mcp-mod"

sourceCompatibility = targetCompatibility = "1.{java}"

minecraft {{
    mappings channel: "official", version: "{mc}"
    runs {{
        client {{ workingDirectory = file("run") }}
    }}
}}

repositories {{
    mavenCentral()
    flatDir {{
        dirs "{depth}.maven-local/com/mcbbs/mcp/mcp-common/1.0.0-SNAPSHOT"
    }}
}}

dependencies {{
    minecraft "net.minecraftforge:forge:{forge_ver}"
    implementation name: "mcp-common"
    implementation "org.java-websocket:Java-WebSocket:1.5.4"
}}

tasks.withType(JavaCompile) {{
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


def write_forge_build_fg5(mc, info, path):
    java = info.get("java", 16)
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

version = "1.0.0-SNAPSHOT"
group = "xyz.langyo"
archivesBaseName = "minecraft-mcp-mod"

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
    flatDir {{
        dirs "{depth}.maven-local/com/mcbbs/mcp/mcp-common/1.0.0-SNAPSHOT"
    }}
}}

dependencies {{
    minecraft "net.minecraftforge:forge:{forge_ver}"
    implementation name: "mcp-common"
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


def write_forge_build_fg6(mc, info, path):
    java = info.get("java", 17)
    forge_ver = info.get("forge", "")
    depth = maven_local_depth()
    content = f"""plugins {{
    id 'java'
    id 'idea'
    id 'eclipse'
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
}}

version = '1.0.0-SNAPSHOT'
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

dependencies {{
    minecraft "net.minecraftforge:forge:{forge_ver}"
    implementation 'com.mcbbs.mcp:mcp-common:1.0.0-SNAPSHOT'
    implementation 'org.java-websocket:Java-WebSocket:1.5.4'
}}

tasks.withType(JavaCompile).configureEach {{
    options.encoding = 'UTF-8'
}}

jar {{
    manifest {{
        attributes 'FMLModType': 'GAMELIB'
        attributes 'Automatic-Module': 'xyz.langyo.minecraftmcp'
    }}
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)


def write_forge_build_fg7(mc, info, path):
    java = info.get("java", 21)
    forge_ver = info.get("forge", "")
    fg = info.get("fg", "[7.0.23,8)")
    depth = maven_local_depth()
    content = f"""plugins {{
    id 'java'
    id 'idea'
    id 'eclipse'
    id 'net.minecraftforge.gradle' version '{fg}'
}}

version = '1.0.0-SNAPSHOT'
group = 'xyz.langyo'

java.toolchain.languageVersion = JavaLanguageVersion.of({java})

minecraft {{
    runs {{
        configureEach {{
            workingDir = layout.projectDirectory.dir('run')
            systemProperty 'eventbus.api.strictRuntimeChecks', 'true'
            systemProperty 'forge.enabledGameTestNamespaces', 'minecraftmcp'
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

dependencies {{
    implementation minecraft.dependency('net.minecraftforge:forge:{forge_ver}')
    implementation 'com.mcbbs.mcp:mcp-common:1.0.0-SNAPSHOT'
    implementation 'org.java-websocket:Java-WebSocket:1.5.4'
}}

tasks.withType(JavaCompile).configureEach {{
    options.encoding = 'UTF-8'
}}

tasks.jar {{
    manifest {{
        attributes 'ModSide' : 'BOTH'
        attributes 'Automatic-Module' : 'xyz.langyo.minecraftmcp'
        attributes 'Implementation-Version' : project.version
    }}
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)


# ============================================================
# NEOFORGE BUILD GENERATION
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

rootProject.name = 'minecraft-mcp-neoforge-{mc}'
"""
    with open(os.path.join(path, "settings.gradle"), "w") as f:
        f.write(content)


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

version = '1.0.0-SNAPSHOT'
group = 'xyz.langyo'

java.toolchain.languageVersion = JavaLanguageVersion.of({java})

repositories {{
    maven {{
        url = rootProject.projectDir.toPath().resolve('{depth}.maven-local').toUri()
        allowInsecureProtocol = false
    }}
    mavenCentral()
}}

dependencies {{
    implementation 'com.mcbbs.mcp:mcp-common:1.0.0-SNAPSHOT'
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
        minecraftmcp {{
            sourceSet sourceSets.main
        }}
    }}
}}

tasks.withType(JavaCompile).configureEach {{
    options.encoding = 'UTF-8'
}}

tasks.jar {{
    manifest {{
        attributes 'ModSide' : 'CLIENT'
        attributes 'Automatic-Module' : 'xyz.langyo.minecraftmcp'
        attributes 'Implementation-Version' : project.version
    }}
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)


# ============================================================
# FABRIC BUILD GENERATION
# ============================================================

def write_fabric_settings(mc, info, path):
    content = f"""pluginManagement {{
    repositories {{
        maven {{ url = 'https://maven.fabricmc.net/' }}
        gradlePluginPortal()
    }}
}}

rootProject.name = 'minecraft-mcp-fabric-{mc}'
"""
    with open(os.path.join(path, "settings.gradle"), "w") as f:
        f.write(content)


def write_fabric_build(mc, info, path):
    java = info.get("java", 17)
    yarn = info.get("fabric_yarn", "")
    depth = maven_local_depth()
    loom_ver = FABRIC_LOOM_VERSIONS.get(mc, "1.7-SNAPSHOT")
    loader_ver = FABRIC_LOADER_VERSIONS.get(mc, "0.16.0")
    fabric_api = FABRIC_API_VERSIONS.get(mc, "")

    deps = f"""    minecraft "com.mojang:minecraft:{mc}"
    mappings "net.fabricmc:yarn:{yarn}:v2"
    modImplementation "net.fabricmc:fabric-loader:{loader_ver}"
"""
    if fabric_api:
        deps += f"""    modImplementation "net.fabricmc.fabric-api:fabric-api:{fabric_api}"
"""
    deps += """    implementation 'com.mcbbs.mcp:mcp-common:1.0.0-SNAPSHOT'
    implementation 'org.java-websocket:Java-WebSocket:1.5.4'
"""

    content = f"""plugins {{
    id 'java'
    id 'fabric-loom' version '{loom_ver}'
}}

version = '1.0.0-SNAPSHOT'
group = 'xyz.langyo'

java.toolchain.languageVersion = JavaLanguageVersion.of({java})

repositories {{
    maven {{
        url = rootProject.projectDir.toPath().resolve('{depth}.maven-local').toUri()
        allowInsecureProtocol = false
    }}
    mavenCentral()
}}

dependencies {{
{deps}}}
tasks.withType(JavaCompile).configureEach {{
    options.encoding = 'UTF-8'
}}
"""
    with open(os.path.join(path, "build.gradle"), "w") as f:
        f.write(content)


def write_fabric_mod_json(mc, info, path):
    content = """{
  "schemaVersion": 1,
  "id": "minecraftmcp",
  "version": "${version}",
  "name": "Minecraft MCP Bridge",
  "description": "WebSocket bridge for AI agent interaction",
  "authors": ["langyo"],
  "environment": "client",
  "entrypoints": {
    "client": ["xyz.langyo.minecraftmcp.MinecraftMcpMod"]
  }
}
"""
    os.makedirs(os.path.join(path, "src", "main", "resources"), exist_ok=True)
    with open(os.path.join(path, "src", "main", "resources", "fabric.mod.json"), "w") as f:
        f.write(content)


# ============================================================
# MAIN
# ============================================================

def create_mod(mc, loader, info):
    base = os.path.join(MODS_DIR, mc, loader)
    os.makedirs(base, exist_ok=True)
    fg = info.get("fg", "")
    mdg = info.get("mdg", "")
    gradle_ver = get_gradle_ver(mc, loader, info)
    copy_wrapper(base, gradle_ver)

    if loader == "forge":
        if fg == "1.2":
            write_forge_settings_legacy(mc, info, base)
            write_forge_build_fg12(mc, info, base)
        elif fg == "2.3":
            write_forge_settings_legacy(mc, info, base)
            write_forge_build_fg23(mc, info, base)
        elif fg == "3.+":
            write_forge_settings_legacy(mc, info, base)
            write_forge_build_fg3(mc, info, base)
        elif fg == "4.1":
            write_forge_settings_legacy(mc, info, base)
            write_forge_build_fg4(mc, info, base)
        elif fg == "5.1":
            write_forge_settings_legacy(mc, info, base)
            write_forge_build_fg5(mc, info, base)
        elif fg == "[6.0,6.2)":
            write_forge_settings_modern(mc, info, base)
            write_forge_build_fg6(mc, info, base)
        elif "[7.0" in fg:
            write_forge_settings_modern(mc, info, base)
            write_forge_build_fg7(mc, info, base)
    elif loader == "neoforge":
        write_neoforge_settings(mc, info, base)
        write_neoforge_build(mc, info, base)
    elif loader == "fabric":
        write_fabric_settings(mc, info, base)
        write_fabric_build(mc, info, base)
        write_fabric_mod_json(mc, info, base)

    return base


if __name__ == "__main__":
    total = 0
    for mc, info in ALL_VERSIONS.items():
        loaders = []
        if "forge" in info:
            loaders.append("forge")
        if "neoforge" in info:
            loaders.append("neoforge")
        if "fabric_yarn" in info:
            loaders.append("fabric")
        for loader in loaders:
            path = create_mod(mc, loader, info)
            total += 1
            print(f"  Created: {mc}/{loader} (Gradle {get_gradle_ver(mc, loader, info)})")
    print(f"\nTotal: {total} mod projects created")
