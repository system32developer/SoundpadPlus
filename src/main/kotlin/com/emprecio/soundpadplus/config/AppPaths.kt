package com.emprecio.soundpadplus.config

import java.io.File

/**
 * Everything the app stores lives next to the executable, so "open file location" shows it all:
 * `settings.properties`, `plugins/`, `lang/` and `tools/`.
 */
object AppPaths {
    val home: File = (System.getProperty("soundpadplus.home")?.let(::File)
        ?: System.getProperty("jpackage.app-path")?.let { File(it).parentFile }
        ?: runCatching { File(AppPaths::class.java.protectionDomain.codeSource.location.toURI()).parentFile.parentFile }.getOrNull()
        ?: File(".")).also { it.mkdirs() }

    val settings = File(home, "settings.properties")
    val plugins = File(home, "plugins").also { it.mkdirs() }
    val languages = File(home, "lang").also { it.mkdirs() }
    val tools = File(home, "tools")
}
