import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.jar.JarFile

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    `maven-publish`
}


configurations.all {
    resolutionStrategy {
        force(
            "net.bytebuddy:byte-buddy:1.18.8",
            "net.bytebuddy:byte-buddy-agent:1.18.8",
            "com.google.guava:guava:33.5.0-jre",
            "com.google.code.gson:gson:2.13.2",
            "it.unimi.dsi:fastutil:8.5.18"
        )
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.cyr1en.dev/snapshots")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
    maven("https://repo.glaremasters.me/repository/towny/")
    maven("https://repo.william278.net/releases")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":prompt-core"))
    implementation(project(":prompt-ui-api"))
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.26.1")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("org.openjdk.nashorn:nashorn-core:15.4")

    // Hook dependencies (all compileOnly — user must have them on the server)
    compileOnly("com.github.LeonMangler:PremiumVanishAPI:2.9.0-4")
    compileOnly("com.github.LeonMangler:SuperVanish:6.2.18-3")
    compileOnly("com.github.mbax:VanishNoPacket:3.22")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.palmergames.bukkit.towny:towny:0.100.3.0")
    compileOnly("net.william278.husktowns:husktowns-common:3.0.5")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.1.0-SNAPSHOT")
    compileOnly("de.hexaoxi:carbonchat-api:3.0.0-beta.26")

    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.0.0")
    testImplementation("org.mockito:mockito-core:5.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand(project.properties)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs("-Dnet.bytebuddy.experimental=true")
}

tasks.shadowJar {
    archiveBaseName.set("CommandPrompterPaper")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    dependsOn(":prompt-ui-26.1:jar", ":prompt-ui-26.2:jar")
    // MUST use zipTree — avoids Java 25 NMS classes on Java 21 API classpath
    val nms26_1 = project(":prompt-ui-26.1").tasks.named("jar", Jar::class)
    from(nms26_1.map { zipTree(it.archiveFile) })
    
    val nms26_2 = project(":prompt-ui-26.2").tasks.named("jar", Jar::class)
    from(nms26_2.map { zipTree(it.archiveFile) })

    // Paper supplies Adventure. Keep those API/serializer classes out of the plugin jar and
    // isolate bStats from other plugins that may ship a different bStats version.
    relocate("org.bstats", "dev.cyr1en.promptpaper.libs.bstats")
    mergeServiceFiles()
}

// The shadow jar is the only distributable prompt-paper artifact. Keeping the ordinary Java jar
// disabled prevents it from being mistaken for a release plugin by local tooling or CI uploads.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// =============================================================================
// Test server management
// =============================================================================
//
// Tasks:
//   - prepareServer : ensure <rootDir>/testserver/<version>/ exists with a
//                     current Paper jar and accepted EULA.
//   - stopServer    : stop only the process recorded in the selected server root.
//
// Properties (all optional):
//   -PpaperVersion=<v>   Paper version (e.g. 26.1.2, 26.2-rc-2). Default: latest stable.
//   -PpaperBuild=<n>     Pin a specific build number. Default: latest matching -PpaperChannel.
//   -PpaperChannel=<c>   stable | beta | alpha | default. Default: stable.
//   -PtestServer=<path>  Override server root. Default: <rootDir>/testserver/<paperVersion>/.
//
// API: PaperMC v3 at https://fill.papermc.io/v3/projects/paper
//   (v2 /download endpoint is being sunset 2026-07-01 — already returns 404)

val paperApiBase = "https://fill.papermc.io/v3/projects/paper"
val paperJarPattern = Regex("""paper-(.+)-(\d+)\.jar""")
val serverPidFileName = ".commandprompter-server.properties"

data class PaperBuild(
    val id: Int,
    val channel: String,
    val downloadUrl: String,
    val jarName: String,
    val sha256: String?
)

data class ExistingPaperJar(val file: File, val version: String, val build: Int)

data class ServerProcessRecord(
    val pid: Long,
    val serverRoot: String,
    val jar: String,
    val startMillis: Long
)

fun httpGet(url: String): String {
    // nosemgrep
    val conn = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 60_000
        setRequestProperty("User-Agent", "CommandPrompter-test-server/3.1")
    }
    return conn.inputStream.bufferedReader().use { it.readText() }
}

fun listAllPaperVersions(): List<String> {
    val body = httpGet(paperApiBase)
    // v3 response: {"versions":{"26.2":["26.2-rc-2"],"26.1":["26.1.2","26.1.1"],...}}
    // The keys are version *groups* (e.g. "26.2"); the values are arrays of
    // actual version strings, newest first within each group. The list is
    // also newest-first across groups, so the first element is the latest.
    val versionsObj = Regex(""""versions"\s*:\s*(\{(?:[^{}]|\{[^{}]*\})*\})""")
        .find(body)?.groupValues?.get(1)
        ?: error("Could not parse versions object from Paper API response")
    // Split top-level to isolate each group object, then read its values.
    val groupPattern = Regex(""""([0-9][^"]*)"\s*:\s*\[([^\]]*)\]""")
    return groupPattern.findAll(versionsObj).flatMap { g ->
        Regex(""""([^"]+)"""").findAll(g.groupValues[2]).map { it.groupValues[1] }
    }.toList()
}

/**
 * Split a JSON array (or object) into its top-level element strings. The
 * PaperMC v3 builds response is an array whose elements are deeply nested
 * objects (`commits`, `downloads.server:default`); a flat regex can't match
 * across those braces, so we walk the characters and split on depth-0 `{}`.
 * `[` and `]` are deliberately ignored — they bracket the outer wrapper or
 * inner arrays, neither of which we want to split on.
 */
fun splitTopLevel(json: String): List<String> {
    val out = mutableListOf<String>()
    var depth = 0
    var start = -1
    var inString = false
    var escape = false
    for ((i, c) in json.withIndex()) {
        when {
            escape -> escape = false
            c == '\\' && inString -> escape = true
            c == '"' -> inString = !inString
            !inString -> when (c) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out.add(json.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
    }
    return out
}

fun resolveLatestVersion(channel: String): String {
    // Walk versions from latest to earliest. For each, take the first build
    // whose channel matches the requested filter (stable / beta / alpha / default).
    val channelOrder = channelOrderFor(channel)
    for (version in listAllPaperVersions()) {
        val build = resolveBuild(version, channelOrder) ?: continue
        println("Resolved latest: $version build ${build.id} (${build.channel})")
        return version
    }
    error("No Paper builds found matching channel=$channel")
}

fun channelOrderFor(channel: String): List<String> = when (channel.lowercase()) {
    "stable" -> listOf("STABLE")
    "beta" -> listOf("BETA", "STABLE")
    "alpha" -> listOf("ALPHA", "BETA", "STABLE")
    else -> listOf("STABLE", "BETA", "ALPHA")  // "default" — anything available
}

fun resolveBuild(version: String, channelOrder: List<String>, pinned: Int? = null): PaperBuild? {
    val body = httpGet("$paperApiBase/versions/$version/builds")
    val idRe = Regex(""""id"\s*:\s*(\d+)""")
    val chRe = Regex(""""channel"\s*:\s*"([^"]+)"""")
    val urlRe = Regex(""""url"\s*:\s*"(https?://[^"]+)"""")
    val nameRe = Regex(""""name"\s*:\s*"(paper-[^"]+\.jar)""")

    val builds = splitTopLevel(body).mapNotNull { obj ->
        val id = idRe.find(obj)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
        val ch = chRe.find(obj)?.groupValues?.get(1) ?: return@mapNotNull null
        val url = urlRe.find(obj)?.groupValues?.get(1) ?: return@mapNotNull null
        val name = nameRe.find(obj)?.groupValues?.get(1) ?: return@mapNotNull null
        val sha256 = Regex(""""sha256"\s*:\s*"([0-9a-fA-F]{64})"""")
            .find(obj)?.groupValues?.get(1)?.lowercase()
        PaperBuild(id, ch, url, name, sha256)
    }

    if (pinned != null) {
        return builds.firstOrNull { it.id == pinned }
            ?: error("Build $pinned not found for Paper $version")
    }
    for (ch in channelOrder) {
        builds.firstOrNull { it.channel == ch }?.let { return it }
    }
    return null
}

fun findExistingPaperJar(dir: File): ExistingPaperJar? {
    if (!dir.exists()) return null
    val candidates = dir.listFiles { f -> f.isFile && paperJarPattern.matches(f.name) } ?: return null
    val latest = candidates.maxWithOrNull(compareBy<File> { it.lastModified() }.thenBy { it.name })
        ?: return null
    val match = paperJarPattern.matchEntire(latest.name) ?: return null
    return ExistingPaperJar(latest, match.groupValues[1], match.groupValues[2].toInt())
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file.toPath()).use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

fun isReadableJar(file: File): Boolean = try {
    JarFile(file).use { true }
} catch (e: Exception) {
    false
}

fun downloadFile(url: String, target: File, expectedSha256: String?) {
    println("Downloading $url")
    println("         → $target")
    target.parentFile.mkdirs()
    val temporary = Files.createTempFile(target.parentFile.toPath(), ".${target.name}.", ".part")
    // nosemgrep
    val conn = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 120_000
        setRequestProperty("User-Agent", "CommandPrompter-test-server/3.1")
    }
    try {
        if (conn.responseCode !in 200..299) {
            error("Paper download failed with HTTP ${conn.responseCode}: $url")
        }
        conn.inputStream.use { input ->
            Files.newOutputStream(temporary).use { output ->
                input.copyTo(output)
            }
        }
        if (Files.size(temporary) <= 0L) error("Paper download produced an empty file: $url")
        if (!isReadableJar(temporary.toFile())) error("Paper download is not a readable jar: $url")
        if (expectedSha256 != null) {
            val actual = sha256(temporary.toFile())
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                error("Paper checksum mismatch for $url: expected $expectedSha256, got $actual")
            }
        } else {
            println("Paper API did not provide a SHA-256 checksum; accepting non-empty download.")
        }
        // Keep the temporary file beside the target and require an atomic replacement. A failed
        // download therefore cannot leave a partial jar at the final path.
        Files.move(
            temporary,
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
    } finally {
        conn.disconnect()
        Files.deleteIfExists(temporary)
    }
}

fun resolveVersionProperty(): String =
    (project.findProperty("paperVersion") as? String) ?: resolveLatestVersion(
        (project.findProperty("paperChannel") as? String) ?: "stable"
    )

fun resolveServerRoot(version: String): File {
    val override = project.findProperty("testServer") as? String
    return if (override != null) file(override) else file("$rootDir/testserver/$version")
}

fun resolveStopServerRoot(): File {
    val override = project.findProperty("testServer") as? String
    if (override != null) return file(override)

    val configuredVersion = project.findProperty("paperVersion") as? String
    if (configuredVersion != null) return file("$rootDir/testserver/$configuredVersion")

    val base = file("$rootDir/testserver")
    val tracked = base.listFiles { f ->
        f.isDirectory && File(f, serverPidFileName).isFile
    }?.toList() ?: emptyList()
    if (tracked.size == 1) return tracked.single()
    if (tracked.size > 1) {
        error("Multiple tracked test servers exist; select one with -PtestServer=<directory>.")
    }

    // A server created before PID tracking cannot be stopped safely. Select a sole existing
    // directory only to report that no tracked process is available; never scan or kill by name.
    val existing = base.listFiles { f ->
        f.isDirectory && (f.listFiles { jar -> jar.isFile && paperJarPattern.matches(jar.name) }?.isNotEmpty() == true)
    }?.toList() ?: emptyList()
    if (existing.size == 1) return existing.single()
    if (existing.size > 1) {
        error("Multiple test server directories exist; select one with -PtestServer=<directory>.")
    }
    return base
}

fun canonicalPath(file: File): String = file.canonicalFile.path

fun readServerProcessRecord(serverRoot: File): ServerProcessRecord? {
    val pidFile = File(serverRoot, serverPidFileName)
    if (!pidFile.isFile) return null
    return try {
        val properties = Properties()
        Files.newInputStream(pidFile.toPath()).use { properties.load(it) }
        ServerProcessRecord(
            properties.getProperty("pid").toLong(),
            properties.getProperty("serverRoot"),
            properties.getProperty("jar"),
            properties.getProperty("startMillis").toLong())
    } catch (e: Exception) {
        println("Ignoring malformed server PID file $pidFile: ${e.message}")
        null
    }
}

fun writeServerProcessRecord(serverRoot: File, process: Process, jar: File) {
    val handle = process.toHandle()
    val properties = Properties()
    properties["pid"] = handle.pid().toString()
    properties["serverRoot"] = canonicalPath(serverRoot)
    properties["jar"] = canonicalPath(jar)
    properties["startMillis"] = handle.info().startInstant().map { it.toEpochMilli() }.orElse(0L).toString()

    val temporary = Files.createTempFile(serverRoot.toPath(), ".commandprompter-server.", ".tmp")
    try {
        Files.newOutputStream(temporary).use { properties.store(it, "CommandPrompter test server process") }
        Files.move(
            temporary,
            File(serverRoot, serverPidFileName).toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

fun processMatches(handle: ProcessHandle, record: ServerProcessRecord, serverRoot: File): Boolean {
    if (!handle.isAlive) return false
    if (record.serverRoot != canonicalPath(serverRoot)) return false

    val expectedJar = File(record.jar).canonicalFile
    if (canonicalPath(expectedJar.parentFile) != canonicalPath(serverRoot)) return false

    val info = handle.info()
    val arguments = info.arguments().orElse(emptyArray<String>()).toList()
    val jarArgument = arguments.indexOf("-jar").takeIf { it >= 0 }
        ?.let { arguments.getOrNull(it + 1) }
    val commandLine = info.commandLine().orElse("")
    val jarMatches = jarArgument == expectedJar.path || commandLine.contains(expectedJar.path)
    if (!jarMatches) return false

    if (record.startMillis > 0L) {
        val actualStart = info.startInstant().map { it.toEpochMilli() }.orElse(0L)
        if (actualStart == 0L || actualStart != record.startMillis) return false
    }
    return true
}

fun waitForExit(handle: ProcessHandle, timeoutMillis: Long): Boolean {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
    while (handle.isAlive && System.nanoTime() < deadline) {
        try {
            Thread.sleep(100L)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
    }
    return !handle.isAlive
}

fun deleteServerProcessRecord(serverRoot: File, expected: ServerProcessRecord) {
    if (readServerProcessRecord(serverRoot) == expected) {
        Files.deleteIfExists(File(serverRoot, serverPidFileName).toPath())
    }
}

fun stopServerIn(serverRoot: File) {
    if (!serverRoot.exists()) {
        println("Server root $serverRoot does not exist; nothing to stop.")
        return
    }
    val record = readServerProcessRecord(serverRoot)
    if (record == null) {
        println("No tracked server process in $serverRoot; refusing to search for other processes.")
        return
    }

    val handle = ProcessHandle.of(record.pid).orElse(null)
    if (handle == null || !handle.isAlive) {
        deleteServerProcessRecord(serverRoot, record)
        println("Tracked server process ${record.pid} is no longer running.")
        return
    }
    if (!processMatches(handle, record, serverRoot)) {
        println("Refusing to stop PID ${record.pid}: it no longer matches $serverRoot/${File(record.jar).name}.")
        return
    }

    println("Requesting graceful stop for Paper PID ${record.pid} in $serverRoot …")
    handle.destroy()
    if (!waitForExit(handle, 15_000L)) {
        // Revalidate immediately before the forceful operation. Only the PID recorded for this
        // exact root/jar may receive the forceful request; unrelated servers are never searched.
        if (processMatches(handle, record, serverRoot)) {
            println("Paper PID ${record.pid} did not stop gracefully; forcing that PID only.")
            handle.destroyForcibly()
            waitForExit(handle, 5_000L)
        } else {
            println("PID ${record.pid} changed before forceful stop; refusing to terminate it.")
        }
    }
    if (!handle.isAlive) {
        deleteServerProcessRecord(serverRoot, record)
        println("Paper PID ${record.pid} stopped.")
    } else {
        println("Paper PID ${record.pid} is still running; PID file retained for a later stop.")
    }
}

tasks.register("prepareServer") {
    description = "Ensure a Paper test server directory exists with an up-to-date jar and accepted EULA."
    group = "commandprompter"

    doLast {
        val version = resolveVersionProperty()
        val pinnedBuild = (project.findProperty("paperBuild") as? String)?.toIntOrNull()
        val channel = (project.findProperty("paperChannel") as? String) ?: "stable"
        val channelOrder = channelOrderFor(channel)

        val build = resolveBuild(version, channelOrder, pinnedBuild)
            ?: error("No build found for Paper $version (channel=$channel)")
        println("Targeting Paper $version build ${build.id} (${build.channel})")

        val serverRoot = resolveServerRoot(version)
        serverRoot.mkdirs()
        println("Server root: $serverRoot")

        val existing = findExistingPaperJar(serverRoot)
        val existingIsValid = existing != null
            && existing.version == version
            && existing.build >= build.id
            && existing.file.length() > 0L
            && isReadableJar(existing.file)
            && existing.file.name == build.jarName
            && (build.sha256 == null || runCatching { sha256(existing.file) == build.sha256 }.getOrDefault(false))
        val needsDownload = !existingIsValid
        if (needsDownload) {
            existing?.let { current ->
                if (current.version != version) {
                    println("Existing jar is for a different version (${current.version}); replacing.")
                } else {
                    println("Existing jar is build ${current.build}; upgrading to ${build.id}.")
                }
            } ?: println("No existing paper jar; downloading fresh.")
            val target = File(serverRoot, build.jarName)
            downloadFile(build.downloadUrl, target, build.sha256)
            // Remove superseded jars only after the new jar has been downloaded, checked, and
            // atomically installed.
            serverRoot.listFiles { f ->
                f.isFile && paperJarPattern.matches(f.name) && canonicalPath(f) != canonicalPath(target)
            }?.forEach { it.delete() }
        } else {
            println("Existing jar ${existing!!.file.name} build ${existing.build} is up to date.")
        }

        val eula = File(serverRoot, "eula.txt")
        if (!eula.exists() || !eula.readText().contains("eula=true")) {
            eula.writeText("eula=true\n")
            println("EULA accepted at $eula")
        } else {
            println("EULA already accepted.")
        }
    }
}

tasks.register("stopServer") {
    description = "Stop the running Paper test server. " +
        "Uses -PtestServer, -PpaperVersion, or the sole existing tracked server directory."
    group = "commandprompter"

    doLast {
        stopServerIn(resolveStopServerRoot())
        println("Server stopped.")
    }
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            afterEvaluate {
                artifact(tasks.shadowJar)
                artifactId = "CommandPrompterPaper"
            }
        }
    }
}

tasks.register("copyPlugin") {
    description = "Copy the built paper shadow jar to the testserver plugins directory."
    group = "commandprompter"

    dependsOn(tasks.shadowJar, "prepareServer")

    doLast {
        val version = resolveVersionProperty()
        val serverRoot = resolveServerRoot(version)
        val pluginsDir = File(serverRoot, "plugins").apply { mkdirs() }

        val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val dest = File(pluginsDir, shadowJarFile.name)
        if (dest.exists()) dest.delete()
        shadowJarFile.copyTo(dest, overwrite = true)
        println("Deployed ${shadowJarFile.name} → $dest")
    }
}

tasks.register("startServer") {
    description = "Start the Paper test server in the foreground. " +
        "Properties: -PpaperVersion=<v> -PpaperBuild=<n> -PpaperChannel=<c> -PtestServer=<path>."
    group = "commandprompter"

    dependsOn("copyPlugin")

    doLast {
        val version = resolveVersionProperty()
        val serverRoot = resolveServerRoot(version)

        val jar = (serverRoot.listFiles { f -> paperJarPattern.matches(f.name) } ?: emptyArray())
            .maxByOrNull { it.lastModified() }
            ?: error("No paper-*.jar in $serverRoot — did prepareServer run?")
        val existingRecord = readServerProcessRecord(serverRoot)
        if (existingRecord != null) {
            val existingHandle = ProcessHandle.of(existingRecord.pid).orElse(null)
            if (existingHandle != null && existingHandle.isAlive) {
                if (processMatches(existingHandle, existingRecord, serverRoot)) {
                    error("Paper test server is already running with PID ${existingRecord.pid}.")
                }
                error("PID file ${File(serverRoot, serverPidFileName)} points to an unrelated process; refusing to overwrite it.")
            }
            deleteServerProcessRecord(serverRoot, existingRecord)
        }

        val javaLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
        }.get().executablePath.asFile
        println("Starting $jar in $serverRoot with Java 25 launcher $javaLauncher …")

        val process = ProcessBuilder(javaLauncher.absolutePath, "-jar", jar.absolutePath, "-nogui")
            .directory(serverRoot)
            .inheritIO()
            .start()
        try {
            writeServerProcessRecord(serverRoot, process, jar)
        } catch (e: Exception) {
            process.destroyForcibly()
            throw e
        }

        try {
            process.waitFor()
        } finally {
            if (!process.isAlive) {
                val record = readServerProcessRecord(serverRoot)
                if (record != null && record.pid == process.pid()) {
                    deleteServerProcessRecord(serverRoot, record)
                }
            } else {
                println("Paper PID ${process.pid()} is still running; retaining $serverPidFileName.")
            }
        }
    }
}
