// Root build script: delegates mod builds to self-contained subprojects.
//
// Each mod lives under packages/mods/{mc}/{loader}/ with its own gradlew + wrapper,
// because different ForgeGradle/NeoForge/Fabric eras require incompatible Gradle
// versions (2.14 .. 9.3.1) and plugin ecosystems. The root gradlew never loads those
// plugins; it just invokes the subproject's own wrapper, so each subproject runs in
// its own projectDir with its relative paths (../../../common, .maven-local) intact.

val neoforge2612 = layout.projectDirectory.dir("packages/mods/26.1.2/neoforge").asFile

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

tasks.register("build") {
    group = "build"
    description = "Build the MC 26.1.2 neoforge mod (delegates to its own gradlew: clean + build)."
    doLast {
        runDelegated("clean")
        runDelegated("build")
    }
}

tasks.register("jar") {
    group = "build"
    description = "Assemble the MC 26.1.2 neoforge mod JAR (delegates to its own gradlew: clean + jar)."
    doLast {
        runDelegated("clean")
        runDelegated("jar")
    }
}
