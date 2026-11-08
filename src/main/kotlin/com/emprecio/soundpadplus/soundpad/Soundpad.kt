package com.emprecio.soundpadplus.soundpad

import com.emprecio.soundpadplus.config.cleanCategory
import com.emprecio.soundpadplus.i18n.I18n
import com.emprecio.soundpadplus.util.unescapeXml
import java.io.File
import java.io.RandomAccessFile

object Soundpad {
    val ANSI = charset("windows-1252")
    private const val PIPE = "\\\\.\\pipe\\sp_remote_control"

    fun send(cmd: String): String = try {
        RandomAccessFile(PIPE, "rw").use { pipe ->
            pipe.write(cmd.toByteArray(ANSI))
            val buf = java.io.ByteArrayOutputStream().apply { write(pipe.readByte().toInt()) }
            var last = System.nanoTime()
            while (System.nanoTime() - last < 80_000_000) {
                val n = pipe.length().toInt()
                if (n > 0) { buf.write(ByteArray(n).also { pipe.readFully(it) }); last = System.nanoTime() } else Thread.sleep(10)
            }
            buf.toString(Charsets.UTF_8)
        }
    } catch (e: java.io.IOException) {
        ""
    }

    private fun categoryIndex(category: String): Int? {
        val xml = send("GetCategories(false, false)")
        return Regex("<Category\\b[^>]*>").findAll(xml).firstNotNullOfOrNull { m ->
            val tag = m.value
            val name = Regex("name=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)
            val index = Regex("index=\"(\\d+)\"").find(tag)?.groupValues?.get(1)
            if (name == category) index?.toInt() else null
        }
    }

    fun categories(): List<String> =
        Regex("<Category\\b[^>]*>").findAll(send("GetCategories(false, false)")).mapNotNull { m ->
            if (m.value.contains("hidden=\"true\"")) null
            else Regex("name=\"([^\"]*)\"").find(m.value)?.groupValues?.get(1)?.let(::unescapeXml)
        }.toList()

    fun add(file: File, category: String, play: Boolean, remove: Boolean = false) {
        require(category == cleanCategory(category) && category.isNotEmpty()) { I18n.t("soundpad.badCategory") }
        if (send("GetCategories(false, false)").isEmpty()) error(I18n.t("soundpad.notRunning"))
        val idx = categoryIndex(category) ?: run {
            send("DoAddCategory(\"$category\", -1)")
            categoryIndex(category) ?: error(I18n.t("soundpad.categoryFailed", category))
        }
        val path = file.absolutePath.replace("\\", "\\\\")
        val r = send("DoAddSound(\"$path\", $idx, 1)")
        if (!r.startsWith("R-200")) error(I18n.t("soundpad.replied", r))
        if (play || remove) {
            val soundIdx = (1..15).firstNotNullOfOrNull { soundIndex(file) ?: run { Thread.sleep(200); null } }
                ?: error(I18n.t("soundpad.notFoundPlay"))
            send("DoPlaySound($soundIdx)")
            if (remove) {
                waitUntilStopped()
                val i = soundIndex(file) ?: error(I18n.t("soundpad.notFoundDelete"))
                send("DoSelectIndex($i)")
                val r2 = send("DoRemoveSelectedEntries()")
                if (!r2.startsWith("R-200")) error(I18n.t("soundpad.deleteFailed", r2))
            }
        }
    }

    private fun waitUntilStopped() {
        var started = false
        val t0 = System.nanoTime()
        while (true) {
            val playing = send("GetPlayStatus()").let { it.contains("PLAYING") || it.contains("PAUSED") }
            if (playing) started = true
            else if (started || System.nanoTime() - t0 > 3_000_000_000) return
            Thread.sleep(150)
        }
    }

    private fun soundIndex(file: File): Int? {
        fun norm(s: String) = s.replace('/', '\\').lowercase()
        val full = norm(file.absolutePath)
        val tags = Regex("<Sound\\b[^>]*>").findAll(send("GetSoundlist()")).mapNotNull { m ->
            val url = Regex("url=\"([^\"]*)\"").find(m.value)?.groupValues?.get(1)?.let { norm(unescapeXml(it)) }
            val index = Regex("index=\"(\\d+)\"").find(m.value)?.groupValues?.get(1)?.toInt()
            if (url != null && index != null) url to index else null
        }.toList()
        return (tags.lastOrNull { it.first == full } ?: tags.lastOrNull { it.first.substringAfterLast('\\') == full.substringAfterLast('\\') })?.second
    }
}
