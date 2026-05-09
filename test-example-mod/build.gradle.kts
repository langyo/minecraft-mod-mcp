plugins {
    kotlin("jvm") version "2.3.21"
    `java-library`
}

group = "xyz.langyo"
version = "1.0.0"
base { archivesName.set("minecraft-mcp-test-example") }

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
    compileOnly("net.neoforged:neoforge:21.0.143-beta")
}

tasks.jar {
    manifest {
        attributes["ModSide"] = "BOTH"
        attributes["Automatic-Module"] = "xyz.langyo.testmod"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
