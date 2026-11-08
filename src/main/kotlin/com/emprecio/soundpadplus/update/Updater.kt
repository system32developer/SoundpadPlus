package com.emprecio.soundpadplus.update

import com.emprecio.soundpadplus.api.Downloads
import com.emprecio.soundpadplus.security.Checksums
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files

object BuildInfo {
    val version: String = java.util.Properties().apply {
        BuildInfo::class.java.getResourceAsStream("/version.properties")?.use(::load)
    }.getProperty("version", "0")
}

/** Looks for a newer GitHub release and installs it: the installer is downloaded and checked against its published SHA-256. */
object Updater {
    private const val REPO = "system32developer/SoundpadPlus"
    private const val DOWNLOADS = "https://github.com/$REPO/releases/download/"
    private val TAG = Regex("v?([0-9]+(\\.[0-9]+){1,3})")
    private val INSTALLER = Regex("SoundpadPlus-[0-9.]+\\.exe")

    class Release(val version: String, val installerUrl: String, val checksumUrl: String)

    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** The latest release if it is newer than the running version, else null. */
    fun latest(current: String = BuildInfo.version): Release? {
        val json = JsonParser.parseString(Downloads.readText("https://api.github.com/repos/$REPO/releases/latest", setOf("api.github.com"))).asJsonObject
        val version = TAG.matchEntire(json.get("tag_name").asString)?.groupValues?.get(1) ?: return null
        if (!isNewer(version, current)) return null
        val assets = json.getAsJsonArray("assets").map { it.asJsonObject }
            .filter { it.get("browser_download_url").asString.startsWith(DOWNLOADS) }
        val installer = assets.firstOrNull { INSTALLER.matches(it.get("name").asString) } ?: return null
        val checksum = assets.firstOrNull { it.get("name").asString == installer.get("name").asString + ".sha256" } ?: return null
        return Release(version, installer.get("browser_download_url").asString, checksum.get("browser_download_url").asString)
    }

    /** Downloads and verifies the installer of [release]; throws if the checksum does not match. */
    fun download(release: Release, progress: (Int) -> Unit): File {
        val dir = Files.createTempDirectory("soundpadplus-update").toFile().apply { deleteOnExit() }
        val file = File(dir, "SoundpadPlus-${release.version}.exe").apply { deleteOnExit() }
        Downloads.saveTo(file, release.installerUrl, progress, setOf("github.com"), 300L * 1024 * 1024)
        Checksums.verify(file, Checksums.parse(Downloads.readText(release.checksumUrl, setOf("github.com"))))
        return file
    }

    fun launch(installer: File) {
        ProcessBuilder(installer.absolutePath).start()
    }
}
