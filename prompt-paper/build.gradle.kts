import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files

plugins {
    java
    id("io.github.goooler.shadow") version "8.1.7"
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
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    implementation("net.kyori:adventure-text-minimessage:4.26.1")
    implementation("net.kyori:adventure-text-serializer-legacy:4.26.1")
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

    mergeServiceFiles()
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
//                     current Paper jar and accepted EULA. No start, no deploy.
//   - stopServer    : kill the running paper process in the resolved root.
//   - deploy        : prepareServer + stop + copy shadow jar + start.
//
// Properties (all optional):
//   -PpaperVersion=<v>   Minecraft version (e.g. 26.1.2, 1.21.11). Default: latest stable.
//   -PpaperBuild=<n>     Pin a specific build number. Default: latest matching -PpaperChannel.
//   -PpaperChannel=<c>   stable | beta | alpha | default. Default: stable.
//   -PtestServer=<path>  Override server root. Default: <rootDir>/testserver/<paperVersion>/.
//
// API: PaperMC v3 at https://fill.papermc.io/v3/projects/paper
//   (v2 /download endpoint is being sunset 2026-07-01 — already returns 404)

val paperApiBase = "https://fill.papermc.io/v3/projects/paper"
val paperJarPattern = Regex("""paper-([\d.]+)-(\d+)\.jar""")

data class PaperBuild(val id: Int, val channel: String, val downloadUrl: String, val jarName: String)

fun httpGet(url: String): String {
    // nosemgrep
    val conn = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 60_000
        setRequestProperty("User-Agent", "CommandPrompter-deploy/3.0")
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
        PaperBuild(id, ch, url, name)
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

fun findExistingPaperJar(dir: File): Pair<String, Int>? {
    if (!dir.exists()) return null
    val candidates = dir.listFiles { f -> paperJarPattern.matches(f.name) } ?: return null
    val latest = candidates.maxByOrNull { it.lastModified() } ?: return null
    val match = paperJarPattern.matchEntire(latest.name) ?: return null
    return match.groupValues[1] to match.groupValues[2].toInt()
}

fun downloadFile(url: String, target: File) {
    println("Downloading $url")
    println("         → $target")
    target.parentFile.mkdirs()
    // nosemgrep
    val conn = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 120_000
        setRequestProperty("User-Agent", "CommandPrompter-deploy/3.0")
    }
    conn.inputStream.use { input ->
        Files.newOutputStream(target.toPath()).use { output ->
            input.copyTo(output)
        }
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

@Suppress("TooGenericExceptionCaught")
fun stopServerIn(serverRoot: File) {
    if (!serverRoot.exists()) {
        println("Server root $serverRoot does not exist; nothing to stop.")
        return
    }
    println("Stopping paper process (if any) in $serverRoot …")
    
    try {
        // Use jps to safely find and kill the exact java process, avoiding pkill quirks on macOS
        val jps = ProcessBuilder("jps", "-l").start()
        val output = jps.inputStream.bufferedReader().readText()
        jps.waitFor()
        
        output.lines().forEach { line ->
            if (line.contains("paper-") && line.contains(".jar")) {
                val pid = line.substringBefore(" ").trim()
                println("Found running paper server (PID: $pid), killing...")
                ProcessBuilder("kill", "-9", pid).start().waitFor()
            }
        }
    } catch (e: java.io.IOException) {
        println("jps kill method failed, falling back to pkill: ${e.message}")
    } catch (e: InterruptedException) {
        println("jps kill method failed, falling back to pkill: ${e.message}")
    } catch (e: RuntimeException) {
        println("jps kill method failed, falling back to pkill: ${e.message}")
    }

    // Fallback: simple pkill without the complex regex that macOS often rejects
    val proc = ProcessBuilder("bash", "-c", "pkill -9 -f 'paper-.*\\.jar' || true")
        .directory(serverRoot)
        .redirectErrorStream(true)
        .start()
    proc.inputStream.bufferedReader().use { print(it.readText()) }
    proc.waitFor()
    // Give the OS a moment to release file locks.
    Thread.sleep(2000)
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
        val needsDownload = when {
            existing == null -> true
            existing.first != version -> true
            existing.second < build.id -> true
            else -> false
        }
        if (needsDownload) {
            existing?.let { (v, b) ->
                if (v != version) {
                    println("Existing jar is for a different version ($v); replacing.")
                } else {
                    println("Existing jar is build $b; upgrading to ${build.id}.")
                }
            } ?: println("No existing paper jar; downloading fresh.")
            // Clear any old paper jars to keep the dir tidy.
            serverRoot.listFiles { f -> paperJarPattern.matches(f.name) }?.forEach { it.delete() }
            val target = File(serverRoot, build.jarName)
            downloadFile(build.downloadUrl, target)
        } else {
            println("Existing jar ${existing!!.first} build ${existing.second} is up to date.")
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
        "Uses -PtestServer if set, otherwise <rootDir>/testserver/<paperVersion>/."
    group = "commandprompter"

    doLast {
        val version = resolveVersionProperty()
        stopServerIn(resolveServerRoot(version))
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
        println("Starting $jar in $serverRoot …")
        
        val process = ProcessBuilder("java", "-jar", jar.name, "-nogui")
            .directory(serverRoot)
            .inheritIO()
            .start()
            
        process.waitFor()
    }
}
