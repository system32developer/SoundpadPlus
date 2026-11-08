package example

import com.emprecio.soundpadplus.api.AudioSource
import com.emprecio.soundpadplus.api.Downloads
import com.emprecio.soundpadplus.api.Entry

class ExampleSource : AudioSource {
    override val id = "example"
    override val name = "Example"
    override val defaultCategory = "Examples"
    override val searchHint = "Type anything: this source returns one fake result"

    override fun search(query: String): List<Entry> {
        val url = "https://example.com/sounds/${query.trim()}.mp3"
        return listOf(
            Entry(title = query.trim(), subtitle = "Example source", previewUrl = url, duration = "0:05") { progress ->
                Downloads.fromUrl(url, query.trim(), progress)
            }
        )
    }
}
