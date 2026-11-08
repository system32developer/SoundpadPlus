package com.emprecio.soundpadplus.tools

import com.emprecio.soundpadplus.api.Dependency
import com.emprecio.soundpadplus.api.Downloads
import com.emprecio.soundpadplus.config.AppPaths
import com.emprecio.soundpadplus.security.Checksums
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/** Downloads the [Dependency] files the sources ask for into the `tools` folder, checking their SHA-256. */
object ToolInstaller {
    private const val MAX_TOOL_BYTES = 300L * 1024 * 1024

    fun missing(deps: List<Dependency>) = deps.distinctBy { it.fileName }.filter { !it.file.isFile }

    /** Installs whatever is missing; [progress] gets the dependency name and 0-100 (-1 = unknown). */
    fun ensure(deps: List<Dependency>, progress: (name: String, percent: Int) -> Unit) {
        AppPaths.tools.mkdirs()
        for (dep in missing(deps)) {
            val zip = dep.zipEntry != null
            val download = File(AppPaths.tools, dep.fileName + if (zip) ".zip.part" else ".part")
            Downloads.saveTo(download, dep.url, { progress(dep.name, it) }, dep.hosts, MAX_TOOL_BYTES)
            Checksums.verify(download, Checksums.parse(Downloads.readText(dep.checksumUrl, dep.hosts), dep.checksumEntry))
            if (!zip) {
                Files.move(download.toPath(), dep.file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                continue
            }
            val part = File(AppPaths.tools, dep.fileName + ".part")
            ZipInputStream(download.inputStream()).use { z ->
                generateSequence { z.nextEntry }.first { it.name.endsWith(dep.zipEntry) }
                val bytes = z.readNBytes(MAX_TOOL_BYTES.toInt() + 1)
                require(bytes.size <= MAX_TOOL_BYTES) { "${dep.fileName} is too large" }
                part.writeBytes(bytes)
            }
            Files.move(part.toPath(), dep.file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            download.delete()
        }
    }
}
