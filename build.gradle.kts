// Root build script: delegates mod builds to self-contained subprojects.
//
// Each mod lives under packages/mods/{mc}/{loader}/ with its own gradlew + wrapper,
// because different ForgeGradle/NeoForge/Fabric eras require incompatible Gradle
// versions (2.14 .. 9.3.1) and plugin ecosystems. The root gradlew never loads those
// plugins; it invokes the subproject's own wrapper, then exposes the produced JAR via
// a real `:jar` task (so tooling that reads `:jar.archiveFileName` works as expected).

val neoforge2612 = layout.projectDirectory.dir("packages/mods/26.1.2/neoforge").asFile
val subprojectJar = neoforge2612.resolve("build/libs/minecraft-moddev-mcp-neoforge-26.1.2-0.2.0.jar")

fun gradlewCommand(task: String): List<String> =
    if (System.getProperty("os.name").lowercase().contains("windows"))
        listOf("cmd", "/c", "gradlew.bat", task)
    else
        listOf("./gradlew", task)

fun runDelegated(task: String) {
    val builder = ProcessBuilder(gradlewCommand(task)).directory(neoforge2612).redirectErrorStream(true)
    val process = builder.start()
    process.inputStream.use { stream ->
        val reader = stream.bufferedReader()
        var line = reader.readLine()
        while (line != null) {
            println(line)
            line = reader.readLine()
        }
    }
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw GradleException("Delegated './gradlew $task' in $neoforge2612 failed with exit code $exitCode")
    }
}

// Runs the neoforge subproject build and makes the produced JAR available to `:jar`.
val buildNeoforge by tasks.registering {
    group = "build"
    description = "Builds the MC 26.1.2 neoforge mod via its own gradlew (clean + jar)."
    doLast {
        runDelegated("clean")
        runDelegated("jar")
    }
}

// A real Jar task so tooling (e.g. teacon CI) can read `archiveFileName`.
// It packages the JAR produced by the delegated neoforge build.
tasks.register<Jar>("jar") {
    group = "build"
    description = "Expose the MC 26.1.2 neoforge mod JAR at the root project."
    archiveFileName.set("minecraft-moddev-mcp-neoforge-26.1.2-0.2.0.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    dependsOn(buildNeoforge)
    from(zipTree(subprojectJar))
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.register("build") {
    group = "build"
    description = "Build the MC 26.1.2 neoforge mod (full, via the subproject's own gradlew)."
    dependsOn("jar")
    doLast {
        runDelegated("build")
    }
}
