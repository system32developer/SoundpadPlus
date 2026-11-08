package com.emprecio.soundpadplus.plugin

import com.emprecio.soundpadplus.api.AudioSource
import com.emprecio.soundpadplus.config.AppPaths
import com.emprecio.soundpadplus.config.enabled
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/** The plugins currently loaded from [dir]; [reload] picks up files that were added, removed or changed. */
class PluginManager(
    private val dir: File = AppPaths.plugins,
    private val approve: (jar: File, sha256: String) -> Boolean = { _, _ -> false }
) {
    /** Every plugin that loaded, enabled or not (Settings lists these). */
    var all: List<AudioSource> = emptyList()
        private set

    /** The ones the user has not switched off; these are the sources shown in the window. */
    val enabled: List<AudioSource> get() = all.filter { it.enabled }

    /** Source selected in the window, kept so a reload does not jump back to the first one. */
    var selectedId: String? = null

    private var signature: String? = null
    private val declined = mutableSetOf<String>()

    /** Reloads when the files in the folder changed since the last call; returns true if the plugin list was replaced. */
    fun reload(): Boolean {
        PluginLoader.installBundled(dir)
        val now = dir.listFiles().orEmpty().sortedBy { it.name }.joinToString("|") { "${it.name}:${it.length()}:${it.lastModified()}" }
        if (now == signature) return false
        signature = now
        // A jar the user refused is not asked about again on every reload of this session.
        val loaded = PluginLoader.load(dir) { jar, sha -> sha !in declined && approve(jar, sha).also { if (!it) declined += sha } }
        if (loaded.isEmpty()) return false // keep what works rather than showing an app with no sources
        all = loaded
        return true
    }
}

/** Calls [onChange] on the Swing thread once [dir] has been quiet for a second after a file was added, removed or changed. */
fun watchPlugins(dir: File, onChange: () -> Unit) {
    val service = runCatching {
        FileSystems.getDefault().newWatchService().also { dir.toPath().register(it, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY) }
    }.getOrNull() ?: return
    thread(isDaemon = true, name = "plugin-watcher") {
        while (true) {
            service.take().also { it.pollEvents(); it.reset() }
            // A file may still be being copied: wait until nothing else happens.
            while (true) service.poll(1, TimeUnit.SECONDS)?.also { it.pollEvents(); it.reset() } ?: break
            SwingUtilities.invokeLater(onChange)
        }
    }
}
