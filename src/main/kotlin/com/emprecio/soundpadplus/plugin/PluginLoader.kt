package com.emprecio.soundpadplus.plugin

import com.emprecio.soundpadplus.api.AudioSource
import com.emprecio.soundpadplus.config.AppPaths
import com.emprecio.soundpadplus.config.Settings
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.security.MessageDigest
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

/**
 * Every source is a plugin found in the `plugins` folder next to the app. YouTube and MyInstants ship inside the app
 * and are copied there by [installBundled]; the rest are added by the user:
 *
 * - `*.json`: declarative sources ([JsonSource]). They contain no code, so they load without asking.
 * - `*.jar`: code plugins. They run with the full permissions of the user, so a jar is only loaded after
 *   [approve] accepted its SHA-256; the approval is remembered for that exact file, and a changed jar asks again.
 *   Jars that are byte-for-byte the ones bundled in the app need no approval.
 */
object PluginLoader {
    private const val APPROVED = "approvedPlugins"
    private val BUNDLED_JSON = listOf("MyInstants.json")
    private val BUNDLED_JARS = listOf("YouTube.jar")
    private val FIRST = listOf("YouTube", "MyInstants")

    private fun bundled(name: String) = PluginLoader::class.java.getResourceAsStream("/plugins/$name")

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val bundledShas by lazy { BUNDLED_JARS.mapNotNull { name -> bundled(name)?.use { sha256(it.readBytes()) } }.toSet() }

    /**
     * Copies the plugins bundled in the app into [dir]. JSON files are only created when missing, so edits are kept;
     * jars are refreshed when they differ, so they follow the app version.
     */
    fun installBundled(dir: File = AppPaths.plugins) {
        dir.mkdirs()
        BUNDLED_JSON.forEach { name ->
            val target = File(dir, name)
            if (!target.exists()) runCatching { bundled(name)?.use { input -> target.outputStream().use(input::copyTo) } }
        }
        BUNDLED_JARS.forEach { name ->
            val bytes = runCatching { bundled(name)?.use { it.readBytes() } }.getOrNull() ?: return@forEach
            val target = File(dir, name)
            if (!target.isFile || sha256(target.readBytes()) != sha256(bytes)) runCatching { target.writeBytes(bytes) }
        }
    }

    fun load(pluginsDir: File = AppPaths.plugins, approve: (jar: File, sha256: String) -> Boolean = { _, _ -> false }): List<AudioSource> {
        val sources = mutableListOf<AudioSource>()
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
        return sources.sortedBy { FIRST.indexOf(it.id).let { i -> if (i < 0) FIRST.size else i } }
    }

    private fun loadJar(jar: File, approve: (File, String) -> Boolean): List<AudioSource> {
        // Work on a private copy: the file that was hashed and approved is the one that gets loaded.
        val copy = Files.createTempFile("soundpadplus-plugin", ".jar").toFile().apply { deleteOnExit() }
        jar.copyTo(copy, overwrite = true)
        val sha = sha256(copy.readBytes())
        val approved = Settings[APPROVED, ""].split(',').filter { it.isNotBlank() }.toMutableSet()
        if (sha !in bundledShas && sha !in approved) {
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
