plugins {
    `java`
    `maven-publish`
}

group = "xyz.langyo.minecraft.mcp"
version = "0.1.0-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri(layout.projectDirectory.dir("../../.maven-local"))
        }
    }
}
