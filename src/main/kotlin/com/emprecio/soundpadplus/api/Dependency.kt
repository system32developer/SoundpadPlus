package com.emprecio.soundpadplus.api

import com.emprecio.soundpadplus.config.AppPaths
import java.io.File

/**
 * A file a source needs before it can work (an external program, for example). List them in
 * [AudioSource.dependencies]: the app downloads whatever is missing into the `tools` folder at start-up,
 * checks its SHA-256 and shows the progress. Read the downloaded file back with [file].
 *
 * @param name shown in the progress window.
 * @param fileName name of the file in the `tools` folder (letters, digits, `.`, `_` and `-` only).
 * @param url https URL of the file, or of a zip that contains it (then set [zipEntry]).
 * @param hosts the only hosts (and their subdomains) [url] and [checksumUrl] may use.
 * @param checksumUrl URL of a text file with the SHA-256 of what [url] returns.
 * @param checksumEntry name to look for in a `sha256sum`-style listing; null when the file holds just one hash.
 * @param zipEntry when [url] is a zip, the end of the path of the entry to save as [fileName].
 */
class Dependency(
    val name: String,
    val fileName: String,
    val url: String,
    val hosts: Set<String>,
    val checksumUrl: String,
    val checksumEntry: String? = null,
    val zipEntry: String? = null
) {
    init {
        require(FILE.matches(fileName) && !fileName.startsWith(".")) { "Invalid dependency file name: $fileName" }
        require(hosts.isNotEmpty()) { "A dependency needs at least one host" }
    }

    /** Where the file is once downloaded. */
    val file: File get() = File(AppPaths.tools, fileName)

    private companion object {
        val FILE = Regex("[A-Za-z0-9._-]{1,60}")
    }
}
