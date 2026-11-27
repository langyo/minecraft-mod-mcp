plugins {
    `java`
    `maven-publish`
}

group = "xyz.langyo.minecraft.mcp"
version = "0.4.38"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

// Compile against the Java 8 API, not merely emit Java 8 bytecode:
// sourceCompatibility/targetCompatibility alone let the JDK 21 compiler
// resolve calls against the modern API signatures (e.g. the covariant
// ByteBuffer.rewind() -> ByteBuffer, added in Java 9), which then blew up
// with NoSuchMethodError on the real JDK 8 lanes (1.7-1.15.2) the moment
// they took a screenshot.
tasks.withType<JavaCompile> {
    options.release = 8
    // Sources carry UTF-8 punctuation in comments; the platform default
    // charset (GBK on a zh-CN Windows dev box) mojibakes them into compile
    // errors. Runners default to UTF-8 via JEP 400, but do not rely on it.
    options.encoding = "UTF-8"
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
