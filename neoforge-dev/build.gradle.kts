plugins {
    id("net.neoforged.gradle.userdev") version "7.0.175"
    kotlin("jvm") version "2.3.21"
    idea
}

group = "xyz.langyo"
version = "1.0.0-SNAPSHOT"

base { archivesName.set("minecraft-mcp-neoforge") }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    implementation("net.neoforged:neoforge:${property("neo_version")}")
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    implementation("com.google.code.gson:gson:2.11.0")
}

afterEvaluate {
    sourceSets {
        main {
            java.srcDirs(
                file("../../example-mod/src/main/java"),
                file("../../test-example-mod/src/main/java")
            )
            resources.srcDirs(
                file("../../example-mod/src/main/resources"),
                file("../../test-example-mod/src/main/resources")
            )
        }
    }
}

tasks.jar {
    manifest {
        attributes["ModSide"] = "BOTH"
        attributes["Automatic-Module"] = "xyz.langyo.minecraftmcp"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
