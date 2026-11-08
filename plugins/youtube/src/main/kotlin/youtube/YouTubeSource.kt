package youtube

import com.emprecio.soundpadplus.api.AudioSource
import com.emprecio.soundpadplus.api.Dependency
import com.emprecio.soundpadplus.api.Downloads
import com.emprecio.soundpadplus.api.Entry
import com.emprecio.soundpadplus.i18n.I18n
import java.io.File

class YouTubeSource : AudioSource {
    override val id = "YouTube"
    override val name = "YouTube"
    override val defaultCategory get() = I18n.t("source.youtube.category")
    override val searchHint get() = I18n.t("source.youtube.hint")

    private val ytdlp = Dependency(
        "yt-dlp", "yt-dlp.exe", "$YTDLP/yt-dlp.exe", setOf("github.com"), "$YTDLP/SHA2-256SUMS", checksumEntry = "yt-dlp.exe"
    )
    private val ffmpeg = Dependency(
        "ffmpeg", "ffmpeg.exe", FFMPEG, setOf("www.gyan.dev", "gyan.dev"), "$FFMPEG.sha256", zipEntry = "/bin/ffmpeg.exe"
    )

    // The app downloads these at start-up, checks their SHA-256 and shows the progress.
    override val dependencies = listOf(ytdlp, ffmpeg)

    private fun bin(): String = ytdlp.file.takeIf { it.isFile }?.absolutePath ?: error(I18n.t("tools.notReady"))
    private fun ffmpegArgs() = listOf("--ffmpeg-location", ffmpeg.file.parent)

    private fun process(args: List<String>, merge: Boolean) = ProcessBuilder(listOf(bin()) + args).redirectErrorStream(merge).apply {
        environment()["PYTHONUTF8"] = "1"
    }.start()

    override fun search(query: String): List<Entry> {
        val p = process(listOf("ytsearch10:$query", "--flat-playlist", "--print", "%(id)s\t%(title)s\t%(channel)s\t%(duration_string)s"), false)
        val err = p.errorStream.bufferedReader(Charsets.UTF_8).readText()
        val out = p.inputStream.bufferedReader(Charsets.UTF_8).readLines()
        if (p.waitFor() != 0) error(err.lines().lastOrNull { it.isNotBlank() } ?: I18n.t("ytdlp.failed"))
        return out.mapNotNull { it.split('\t').takeIf { f -> f.size == 4 } }.map { f ->
            Entry(f[1], f[2], null, f[3]) { progress -> download(f[0], f[1], progress) }
        }
    }

    private fun download(id: String, title: String, progress: (Int) -> Unit): File {
        require(VIDEO_ID.matches(id)) { I18n.t("security.blockedUrl", id) }
        val name = Downloads.safeName(title, id)
        val p = process(
            listOf(
                "-x", "--audio-format", "mp3", "--no-playlist", "--windows-filenames", "--newline",
                "-o", "${Downloads.dir.path}/${name.replace("%", "%%")}.%(ext)s"
            ) + ffmpegArgs() + listOf("--", "https://www.youtube.com/watch?v=$id"), true
        )
        var last = ""
        p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
            if (line.isNotBlank()) last = line
            Regex("\\[download]\\s+(\\d+(?:\\.\\d+)?)%").find(line)?.let { progress(it.groupValues[1].toDouble().toInt()) }
            if (line.startsWith("[ExtractAudio]")) progress(-1)
        }
        if (p.waitFor() != 0) error(last.ifEmpty { I18n.t("ytdlp.failed") })
        return File(Downloads.dir, "$name.mp3")
    }

    private companion object {
        const val YTDLP = "https://github.com/yt-dlp/yt-dlp/releases/latest/download"
        const val FFMPEG = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
        val VIDEO_ID = Regex("[A-Za-z0-9_-]{6,20}")
    }
}
