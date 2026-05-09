plugins {
    id("net.neoforged.gradle.userdev") version "7.0.147"
    kotlin("jvm") version "2.3.21"
    idea
}

group = "xyz.langyo"
version = "1.0.0-SNAPSHOT"

base {
    archivesName.set("minecraft-mcp-neoforge")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

sourceSets {
    getByName("main").java.srcDirs(
        "src/mcpmod/java",
        "src/testmod/java",
        "src/main/java"
    )
}

configurations {
    implementation.get().extendsFrom(configurations.minecraft.getOrCreate("neoforge"))
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    implementation("com.google.code.gson:gson:2.11.0")
}

tasks.jar {
    manifest {
        attributes["ModSide"] = "BOTH"
        attributes["Automatic-Module"] = "xyz.langyo.minecraftmcp"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

idea {
    module {
        sourceDirs = sourceSets["main"].java.srcDirs + sourceSets["main"].resources.srcDirs
        resourceDirs = []
    }
}
