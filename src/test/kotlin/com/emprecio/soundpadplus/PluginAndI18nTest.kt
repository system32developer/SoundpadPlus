package com.emprecio.soundpadplus

import com.emprecio.soundpadplus.api.Entry
import com.emprecio.soundpadplus.config.cleanCategory
import com.emprecio.soundpadplus.plugin.JsonSource
import com.emprecio.soundpadplus.plugin.PluginLoader
import com.emprecio.soundpadplus.security.Checksums
import com.emprecio.soundpadplus.security.UrlGuard
import com.emprecio.soundpadplus.update.Updater
import com.emprecio.soundpadplus.util.plain
import java.io.File
import java.net.URI
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginAndI18nTest {
    private fun keys(code: String) = Properties().apply {
        File("src/main/resources/i18n/$code.properties").reader(Charsets.UTF_8).use(::load)
    }.keys.map { it.toString() }.toSet()

    private val validJson = File("examples/json-source/example.json").readText()

    @Test
    fun everyLanguageHasTheSameKeysAsEnglish() = assertEquals(keys("en"), keys("es"))

    @Test
    fun jarPluginsAreOnlyLoadedWhenApproved() {
        val dir = File("examples/example-source/build/libs")
        assertFalse("example" in PluginLoader.load(dir).map { it.id })
        assertFalse("example" in PluginLoader.load(dir) { _, _ -> false }.map { it.id })
        val ids = PluginLoader.load(dir) { _, _ -> true }.map { it.id }
        assertTrue(ids.containsAll(listOf("YouTube", "MyInstants", "example")), "got $ids")
    }

    @Test
    fun jsonSourceAcceptsTheExample() = assertEquals("example-json", JsonSource.parse(validJson).id)

    @Test
    fun tunaPluginParses() = assertEquals("tuna", JsonSource.parse(File("examples/json-source/tuna.json").readText()).id)

    @Test
    fun jsonSourceRejectsBadHeaders() = assertFailsWith<IllegalArgumentException> {
        JsonSource.parse(validJson.replace("\"resultsPath\"", "\"headers\": {\"X\": \"a\\r\\nb: c\"}, \"resultsPath\""))
    }

    @Test
    fun memeSoundboardPluginParses() = assertEquals("memesoundboard", JsonSource.parse(File("examples/json-source/memesoundboard.json").readText()).id)

    @Test
    fun jsonSourceRejectsUnsafeDefinitions() {
        listOf(
            validJson.replace("https://api.example.com", "http://api.example.com"),
            validJson.replace("https://api.example.com/search", "https://evil.com/search"),
            validJson.replace("\"hosts\": [\"api.example.com\", \"cdn.example.com\"]", "\"hosts\": [\"127.0.0.1\"]"),
            validJson.replace("{query}", "x"),
            validJson.replace("\"id\": \"example-json\"", "\"id\": \"../evil\""),
            validJson.replace("data.items", "data/items"),
            validJson.replace("\"resultsPath\"", "\"audioBase\": \"https://evil.com/\", \"resultsPath\""),
            "[]",
        ).forEach { assertFailsWith<IllegalArgumentException>(it) { JsonSource.parse(it) } }
    }

    @Test
    fun urlGuardBlocksUnsafeUrls() {
        listOf("http://example.com/a.mp3", "https://user:pw@example.com/a.mp3", "https://127.0.0.1/a.mp3",
            "https://localhost/a.mp3", "https://192.168.1.5/a.mp3", "https://169.254.169.254/latest", "https://[::1]/a.mp3")
            .forEach { assertFailsWith<IllegalArgumentException>(it) { UrlGuard.check(URI(it)) } }
        assertFailsWith<IllegalArgumentException> { UrlGuard.check(URI("https://example.com/a.mp3"), setOf("other.com")) }
    }

    @Test
    fun externalTextCannotInjectHtmlOrControlCharacters() {
        assertEquals("‹html›‹img src=x›ab", plain("<html><img src=x>a\u0000\nb"))
        val e = Entry("<html>x", "<b>y", null, "1:00") { File("x") }
        assertFalse('<' in e.title || '<' in e.subtitle)
    }

    @Test
    fun categoryNamesCannotInjectSoundpadCommands() = assertEquals("a, -1) DoRemove", cleanCategory("a\", -1)\\ DoRemove\n\""))

    @Test
    fun versionsAreComparedNumerically() {
        assertTrue(Updater.isNewer("1.0.10", "1.0.9"))
        assertTrue(Updater.isNewer("1.1", "1.0.9"))
        assertFalse(Updater.isNewer("1.0.7", "1.0.7"))
        assertFalse(Updater.isNewer("1.0.6", "1.0.7"))
    }

    @Test
    fun checksumListingsAreParsed() {
        val hash = "a".repeat(64)
        assertEquals(hash, Checksums.parse("$hash  yt-dlp.exe\n${"b".repeat(64)} *other.exe", "yt-dlp.exe"))
        assertEquals(hash, Checksums.parse(hash))
    }
}
