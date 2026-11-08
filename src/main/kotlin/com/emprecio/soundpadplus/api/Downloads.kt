package com.emprecio.soundpadplus.api

import com.emprecio.soundpadplus.i18n.I18n
import com.emprecio.soundpadplus.security.UrlGuard
import com.emprecio.soundpadplus.soundpad.Soundpad
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Network and file helpers for [Entry.download] and [AudioSource.search] implementations.
 * Every request goes through [UrlGuard]: HTTPS only, no credentials in the URL, and no private or loopback addresses.
 */
object Downloads {
    /** Folder where downloaded audios are saved. */
    val dir: File = File(System.getProperty("user.home"), "Music/SoundpadPlus").also { it.mkdirs() }

    const val MAX_AUDIO_BYTES = 100L * 1024 * 1024
    const val MAX_TEXT_BYTES = 2L * 1024 * 1024
    private const val MAX_REDIRECTS = 5
    private val extensions = setOf("mp3", "wav", "ogg", "m4a")

    /**
     * Opens [url]. Redirects are followed by hand so each hop is checked again.
     * [allowedHosts], when given, restricts the first URL to those hosts (and their subdomains).
     */
    fun open(url: String, allowedHosts: Set<String>? = null, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        var target = URI(url.replace(" ", "%20"))
        repeat(MAX_REDIRECTS + 1) { hop ->
            UrlGuard.check(target, if (hop == 0) allowedHosts else null)
            val connection = (target.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "SoundpadPlus/1.0")
                // Only the first hop: never forward custom headers to a redirect target.
                if (hop == 0) headers.forEach { (k, v) -> setRequestProperty(k, v) }
                connectTimeout = 10_000
                readTimeout = 20_000
            }
            if (connection.responseCode !in setOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location") ?: error(I18n.t("security.blockedUrl", target.host))
            connection.disconnect()
            target = target.resolve(location)
        }
        error(I18n.t("security.blockedUrl", target.host))
    }

    /** Reads a text response (JSON, HTML…), failing if it is larger than [maxBytes]. */
    fun readText(url: String, allowedHosts: Set<String>? = null, maxBytes: Long = MAX_TEXT_BYTES, headers: Map<String, String> = emptyMap()): String =
        open(url, allowedHosts, headers).inputStream.use { input ->
            val bytes = input.readNBytes(maxBytes.toInt() + 1)
            require(bytes.size <= maxBytes) { I18n.t("security.tooLarge") }
            bytes.toString(Charsets.UTF_8)
        }

    /** Turns [title] into a file name Soundpad accepts (it only understands ANSI paths). */
    fun safeName(title: String, fallback: String): String {
        val enc = Soundpad.ANSI.newEncoder()
        return title.filter { enc.canEncode(it) && it !in "\\/:*?\"<>|" && !it.isISOControl() }
            .replace(Regex("\\s+"), " ").trim().trim('.').take(120).ifEmpty { fallback }
    }

    /** Downloads [url] into [dir] as `<title>.<ext>`, reporting progress, and returns the file. */
    fun fromUrl(
        url: String, title: String, progress: (Int) -> Unit, fallbackName: String = "audio",
        allowedHosts: Set<String>? = null, maxBytes: Long = MAX_AUDIO_BYTES
    ): File {
        val ext = url.substringBefore('?').substringAfterLast('.').lowercase().takeIf { it in extensions } ?: "mp3"
        val dest = File(dir, "${safeName(title, fallbackName)}.$ext")
        saveTo(dest, url, progress, allowedHosts, maxBytes)
        return dest
    }

    /** Saves [url] to [dest]. Fails (and removes the partial file) when it is larger than [maxBytes]. */
    fun saveTo(dest: File, url: String, progress: (Int) -> Unit, allowedHosts: Set<String>? = null, maxBytes: Long = MAX_AUDIO_BYTES) {
        val connection = open(url, allowedHosts)
        val total = connection.contentLengthLong
        require(total <= maxBytes) { I18n.t("security.tooLarge") }
        if (total <= 0) progress(-1)
        try {
            connection.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(16_384)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        done += n
                        require(done <= maxBytes) { I18n.t("security.tooLarge") }
                        out.write(buf, 0, n)
                        if (total > 0) progress((done * 100 / total).toInt())
                    }
                }
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }
}
