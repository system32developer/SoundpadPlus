package com.emprecio.soundpadplus.i18n

import com.emprecio.soundpadplus.config.AppPaths
import com.emprecio.soundpadplus.config.Settings
import com.emprecio.soundpadplus.util.plain
import java.io.File
import java.util.Locale
import java.util.Properties

/**
 * Translations live in `<code>.properties` files (UTF-8). Built-in ones are bundled in `/i18n/`;
 * extra languages can be dropped in the `lang` folder next to the app and override the built-in ones.
 * Missing keys fall back to English, and then to the key itself.
 */
object I18n {
    private const val FALLBACK = "en"
    private val bundled = listOf("en", "es")

    init {
        bundled.forEach { code ->
            val target = File(AppPaths.languages, "$code.properties")
            if (!target.exists()) runCatching {
                I18n::class.java.getResourceAsStream("/i18n/$code.properties")?.use { target.outputStream().use(it::copyTo) }
            }
        }
    }

    private fun load(code: String): Properties {
        val props = Properties()
        runCatching {
            I18n::class.java.getResourceAsStream("/i18n/$code.properties")?.reader(Charsets.UTF_8)?.use(props::load)
            File(AppPaths.languages, "$code.properties").takeIf { it.isFile }?.reader(Charsets.UTF_8)?.use(props::load)
        }
        return props
    }

    /** Language codes that can be selected, mapped to their own display name (`language.name` key). */
    val available: Map<String, String> by lazy {
        val external = AppPaths.languages.listFiles { f -> f.extension == "properties" }.orEmpty().map { it.nameWithoutExtension }
        (bundled + external).distinct().associateWith { load(it).getProperty("language.name", it) }
    }

    private val english = load(FALLBACK)

    /** Code of the language in use: the saved one, else the system language, else English. */
    var language: String = Settings["language", Locale.getDefault().language].takeIf { it in available } ?: FALLBACK
        private set

    private var current = if (language == FALLBACK) english else load(language)

    /** Switches language immediately and remembers it. The caller rebuilds the UI. */
    fun select(code: String) {
        if (code !in available || code == language) return
        language = code
        current = if (code == FALLBACK) english else load(code)
        Settings["language"] = code
        Settings.save()
    }

    /** Translated text for [key]; `{0}`, `{1}`… are replaced by [args]. */
    fun t(key: String, vararg args: Any): String {
        var text = current.getProperty(key) ?: english.getProperty(key) ?: key
        args.forEachIndexed { i, arg -> text = text.replace("{$i}", plain(arg.toString())) }
        return text
    }
}
