package com.emprecio.soundpadplus.api

import com.emprecio.soundpadplus.util.plain
import java.io.File

/**
 * A place where audios can be searched and downloaded from.
 *
 * Implement this interface and register the class in
 * `META-INF/services/com.emprecio.soundpadplus.api.AudioSource` to ship it as a plugin jar.
 * The implementation needs a public no-argument constructor.
 * [search] and [Entry.download] run on a background thread, so blocking calls are fine.
 */
interface AudioSource {
    /** Stable unique identifier; user settings are stored under it. Never change it between releases. */
    val id: String

    /** Name shown in the source list. */
    val name: String

    /** Soundpad category used until the user picks another one in Settings. */
    val defaultCategory: String

    /** Placeholder text of the search box while this source is selected. */
    val searchHint: String

    /** Returns the results for [query]. Throw an exception with a readable message to report an error. */
    fun search(query: String): List<Entry>
}

/**
 * One search result.
 *
 * @param previewUrl direct URL of an MP3 the user can listen to before downloading, or null if there is none.
 * @param download saves the audio to disk and returns the file. Call the given function with 0-100 to report
 *   progress, or with -1 when the progress is unknown. [Downloads] has helpers for the common cases.
 */
class Entry(
    title: String,
    subtitle: String,
    val previewUrl: String? = null,
    duration: String = "",
    val download: (progress: (Int) -> Unit) -> File
) {
    val title = plain(title)
    val subtitle = plain(subtitle)
    val duration = plain(duration, 20)

    override fun toString() = title
}
