package com.emprecio.soundpadplus.config

object Settings {
    private val props = java.util.Properties().apply { runCatching { AppPaths.settings.reader(Charsets.UTF_8).use { load(it) } } }
    operator fun get(key: String, default: String): String = props.getProperty(key, default)
    operator fun set(key: String, value: String) { props.setProperty(key, value) }
    fun save() = AppPaths.settings.writer(Charsets.UTF_8).use { props.store(it, null) }
}
