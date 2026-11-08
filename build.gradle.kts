import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    kotlin("jvm") version "2.4.10"
    application
}

application {
    mainClass.set("com.emprecio.soundpadplus.MainKt")
}

group = "com.emprecio"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.5.4")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("javazoom:jlayer:1.0.1") // mp3 preview
    testImplementation(kotlin("test"))
}

val toolsDir = layout.projectDirectory.dir("tools").asFile
val fetchTools = tasks.register("fetchTools") {
    outputs.dir(toolsDir)
    onlyIf { !File(toolsDir, "yt-dlp.exe").exists() || !File(toolsDir, "ffmpeg.exe").exists() }
    doLast {
        toolsDir.mkdirs()
        fun fetch(url: String) = uri(url).toURL().openStream()
        fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        fun verified(file: File, expected: String) {
            if (sha256(file) != expected.lowercase()) {
                file.delete()
                throw GradleException("SHA-256 mismatch for ${file.name}: refusing to use it")
            }
        }

        val ytdlp = File(toolsDir, "yt-dlp.exe")
        if (!ytdlp.exists()) {
            val part = File(toolsDir, "yt-dlp.exe.part")
            fetch("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe").use { i -> part.outputStream().use { i.copyTo(it) } }
            val sums = fetch("https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS").use { it.readBytes().toString(Charsets.UTF_8) }
            val expected = sums.lines().map { it.trim().split(Regex("\\s+")) }.first { it.getOrNull(1)?.removePrefix("*") == "yt-dlp.exe" }[0]
            verified(part, expected)
            part.renameTo(ytdlp)
        }

        val ffmpeg = File(toolsDir, "ffmpeg.exe")
        if (!ffmpeg.exists()) {
            val zip = File(toolsDir, "ffmpeg.zip.part")
            val zipUrl = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
            fetch(zipUrl).use { i -> zip.outputStream().use { i.copyTo(it) } }
            verified(zip, fetch("$zipUrl.sha256").use { it.readBytes().toString(Charsets.UTF_8) }.trim().split(Regex("\\s+"))[0])
            ZipInputStream(zip.inputStream()).use { z ->
                generateSequence { z.nextEntry }.first { it.name.endsWith("/bin/ffmpeg.exe") }
                File(toolsDir, "ffmpeg.exe.part").outputStream().use { z.copyTo(it) }
            }
            File(toolsDir, "ffmpeg.exe.part").renameTo(ffmpeg)
            zip.delete()
        }
    }
}
tasks.named<JavaExec>("run") { systemProperty("soundpadplus.tools", toolsDir.absolutePath) }
tasks.named<Sync>("installDist") {
    dependsOn(fetchTools)
    from(toolsDir) { into("lib"); include("*.exe") }
}

// jpackage bundles its own JRE. "exe" needs WiX 3 installed; "app-image" does not.
val appVersion = "1.0.6" // bump on every release: Windows only upgrades to a higher version
fun jpackageTask(name: String, type: String) = tasks.register<Exec>(name) {
    dependsOn("installDist")
    val libs = layout.buildDirectory.dir("install/${project.name}/lib").get().asFile
    val dest = layout.buildDirectory.dir("dist").get().asFile
    // portable WiX 3 (no admin needed): unzip wix314-binaries.zip into %LOCALAPPDATA%\wix3
    System.getenv("LOCALAPPDATA")?.let { environment("PATH", "$it\\wix3;" + System.getenv("PATH")) }
    doFirst { File(dest, if (type == "exe") "SoundpadPlus-$appVersion.exe" else "SoundpadPlus").deleteRecursively() }
    commandLine(
        "jpackage", "--type", type, "--name", "SoundpadPlus", "--app-version", appVersion,
        "--input", libs, "--main-jar", "${project.name}-$version.jar", "--main-class", "com.emprecio.soundpadplus.MainKt",
        "--dest", dest
    ).also { if (type == "exe") args("--win-menu", "--win-shortcut", "--win-dir-chooser", "--win-upgrade-uuid", "6f1d2c1e-8a3b-4c57-9d0e-5b7a2f4e9c10") }
}
jpackageTask("packageApp", "app-image")
jpackageTask("packageInstaller", "exe")

kotlin {
    jvmToolchain(21)
}

tasks.test {
    val testSettings = layout.buildDirectory.file("test-settings.properties").get().asFile
    doFirst { testSettings.delete() }
    systemProperty("soundpadplus.settings", testSettings.path)
    dependsOn(":examples:example-source:jar")
    useJUnitPlatform()
}