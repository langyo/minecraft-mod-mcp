plugins {
    id("net.neoforged.gradle.userdev") version "7.0.147"
    kotlin("jvm") version "2.3.21"
}

group = "xyz.langyo"
version = "1.0.0"

base {
    archivesName.set("minecraft-mcp-example")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
}

configurations {
    runtimeClasspath.get().exclude(group = "com.mojang", module = "datafixerupper")
}

repositories {
    mavenCentral()
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
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { config ->
        config.filter { it.name.endsWith("jar") && !it.name.contains("minecraft") && !it.name.contains("forge") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
