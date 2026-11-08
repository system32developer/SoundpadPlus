package com.emprecio.soundpadplus.plugin

import com.emprecio.soundpadplus.api.AudioSource
import com.emprecio.soundpadplus.config.AppPaths
import com.emprecio.soundpadplus.config.Settings
import com.emprecio.soundpadplus.source.MyInstants
import com.emprecio.soundpadplus.source.YtDlp
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.security.MessageDigest
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

/**
 * Built-in sources, plus the plugins found in `~/.soundpadplus/plugins`:
 *
 * - `*.json`: declarative sources ([JsonSource]). They contain no code, so they load without asking.
 * - `*.jar`: code plugins. They run with the full permissions of the user, so a jar is only loaded after
 *   [approve] accepted its SHA-256; the approval is remembered for that exact file, and a changed jar asks again.
 */
object PluginLoader {
    private const val APPROVED = "approvedPlugins"

    fun load(pluginsDir: File = AppPaths.plugins, approve: (jar: File, sha256: String) -> Boolean = { _, _ -> false }): List<AudioSource> {
        val sources = mutableListOf<AudioSource>(YtDlp, MyInstants)
        fun add(plugin: AudioSource) {
            if (sources.any { it.id == plugin.id }) System.err.println("Plugin skipped, duplicate id: ${plugin.id}") else sources += plugin
        }
        val files = pluginsDir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
        files.filter { it.extension == "json" }.forEach { file ->
            runCatching { add(JsonSource.parse(file.readText(Charsets.UTF_8))) }
                .onFailure { System.err.println("Plugin ${file.name} skipped: ${it.message}") }
        }
        files.filter { it.extension == "jar" }.forEach { jar ->
            runCatching { loadJar(jar, approve).forEach(::add) }
                .onFailure { System.err.println("Plugin ${jar.name} skipped: ${it.message}") }
        }
        return sources
    }

    private fun loadJar(jar: File, approve: (File, String) -> Boolean): List<AudioSource> {
        // Work on a private copy: the file that was hashed and approved is the one that gets loaded.
        val copy = Files.createTempFile("soundpadplus-plugin", ".jar").toFile().apply { deleteOnExit() }
        jar.copyTo(copy, overwrite = true)
        val sha = MessageDigest.getInstance("SHA-256").digest(copy.readBytes()).joinToString("") { "%02x".format(it) }
        val approved = Settings[APPROVED, ""].split(',').filter { it.isNotBlank() }.toMutableSet()
        if (sha !in approved) {
            if (!approve(jar, sha)) return emptyList()
            approved += sha
            Settings[APPROVED] = approved.joinToString(",")
            Settings.save()
        }
        val loader = URLClassLoader(arrayOf(copy.toURI().toURL()), AudioSource::class.java.classLoader)
        val found = mutableListOf<AudioSource>()
        val iterator = ServiceLoader.load(AudioSource::class.java, loader).iterator()
        while (true) {
            try {
                if (!iterator.hasNext()) break
                found += iterator.next()
            } catch (e: ServiceConfigurationError) {
                System.err.println("Plugin ${jar.name} skipped: ${e.message}")
            }
        }
        return found
    }
}
