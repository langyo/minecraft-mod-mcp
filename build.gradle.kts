import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.3.21"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.mcbbs"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    implementation("com.google.code.gson:gson:2.11.0")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

application {
    mainClass.set("com.mcbbs.mcp.MinecraftMcpServerKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("mcp-server")
    archiveClassifier.set("")
    manifest { attributes["Main-Class"] = "com.mcbbs.mcp.MinecraftMcpServerKt" }
}
