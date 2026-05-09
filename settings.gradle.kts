plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "minecraft-neoforge-mcp"

include("example-mod")
project(":example-mod").name = "minecraft-mcp-example-mod"
