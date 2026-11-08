package com.emprecio.soundpadplus.config

import java.io.File

object Settings {
    private val file = System.getProperty("soundpadplus.settings")?.let(::File) ?: File(System.getProperty("user.home"), ".soundpadplus.properties")
    private val props = java.util.Properties().apply { runCatching { file.reader(Charsets.UTF_8).use { load(it) } } }
    operator fun get(key: String, default: String): String = props.getProperty(key, default)
    operator fun set(key: String, value: String) { props.setProperty(key, value) }
    fun save() = file.writer(Charsets.UTF_8).use { props.store(it, null) }
}
