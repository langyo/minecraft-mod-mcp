rootProject.name = "minecraft-moddev-mcp"

// Mod subprojects are NOT included here. Each lives under
// packages/mods/{mc}/{loader}/ with its own gradlew + wrapper, because different
// ForgeGradle/NeoForge/Fabric eras need incompatible Gradle versions (2.14 .. 9.3.1)
// and plugin ecosystems that cannot coexist in a single Gradle build.
//
// The root build.gradle.kts delegates to those subprojects via Exec tasks
// (./gradlew build | ./gradlew jar). The common module's sources are consumed
// directly via srcDir at build time, so it needs no root-level include.
