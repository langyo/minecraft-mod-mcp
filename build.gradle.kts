// Root build script: delegates mod builds to self-contained subprojects.
//
// Each mod lives under packages/mods/{mc}/{loader}/ with its own gradlew + wrapper,
// because different ForgeGradle/NeoForge/Fabric eras require incompatible Gradle
// versions (2.14 .. 9.3.1) and plugin ecosystems. The root gradlew never loads those
// plugins; it invokes the subproject's own wrapper, then exposes the produced JAR via
// a real `:jar` task (so tooling that reads `:jar.archiveFileName` works as expected).

val neoforge2612 = layout.projectDirectory.dir("packages/mods/26.1.2/neoforge").asFile

/** Finds the JAR produced by the neoforge subproject build. */
fun findSubprojectJar(): File {
    val libsDir = neoforge2612.resolve("build/libs")
    val jars = libsDir.listFiles()?.filter {
        it.isFile && it.name.endsWith(".jar")
            && !it.name.contains("-sources", ignoreCase = true)
            && !it.name.contains("-dev", ignoreCase = true)
            && !it.name.contains("-slim", ignoreCase = true)
    }
    if (jars.isNullOrEmpty()) {
        throw GradleException("No JAR found in $libsDir — did the delegated build succeed?")
    }
    if (jars.size > 1) {
        logger.warn("Multiple JARs found in $libsDir, using first: ${jars.first().name}")
    }
    return jars.first()
}

fun gradlewCommand(task: String): List<String> =
    if (System.getProperty("os.name").lowercase().contains("windows"))
        listOf("cmd", "/c", "gradlew.bat", task)
    else
        // Use "sh gradlew" instead of "./gradlew": the gradlew script may lack the
        // executable bit in CI checkouts (git doesn't always preserve mode bits),
        // and invoking it via sh avoids the "Permission denied" error.
        listOf("sh", "gradlew", task)

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
    // Fixed output name for the root project — the input comes from the
    // subproject's JAR (discovered dynamically at execution time).
    archiveFileName.set("minecraft-moddev-mcp-neoforge-26.1.2-0.2.1.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    dependsOn(buildNeoforge)
    duplicatesStrategy = DuplicatesStrategy.FAIL
    // Defer from() to doFirst so findSubprojectJar() only runs at execution time,
    // after buildNeoforge has produced the JAR. Using a Provider causes the
    // closure to be resolved during task dependency resolution, which is too early.
    doFirst {
        from(zipTree(findSubprojectJar()))
    }
}

tasks.register("build") {
    group = "build"
    description = "Build the MC 26.1.2 neoforge mod (full, via the subproject's own gradlew)."
    dependsOn("jar")
    doLast {
        runDelegated("build")
    }
}
