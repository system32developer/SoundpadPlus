package com.emprecio.soundpadplus.plugin

import com.emprecio.soundpadplus.api.AudioSource
import com.emprecio.soundpadplus.api.Downloads
import com.emprecio.soundpadplus.api.Entry
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder

/**
 * A source described by a JSON file, with no code. It can only call HTTPS URLs on the hosts it declares,
 * read a JSON list from the answer and download the audio files it points to.
 *
 * ```json
 * {
 *   "id": "my-source", "name": "My Source", "defaultCategory": "Effects", "searchHint": "Search…",
 *   "hosts": ["api.example.com", "cdn.example.com"],
 *   "searchUrl": "https://api.example.com/search?q={query}",
 *   "resultsPath": "data.items",
 * ("audioBase": "https://cdn.example.com/media/" is optional and goes in front of the audioUrl value;
 *  "headers": {"Name": "value"} is optional and is sent only with the search request)
 *   "fields": { "title": "name", "subtitle": "tags", "duration": "length", "audioUrl": "files.mp3" }
 * }
 * ```
 */
class JsonSource private constructor(
    override val id: String,
    override val name: String,
    override val defaultCategory: String,
    override val searchHint: String,
    private val hosts: Set<String>,
    private val searchUrl: String,
    private val resultsPath: String,
    private val audioBase: String,
    private val fields: Map<String, String>,
    private val headers: Map<String, String>
) : AudioSource {

    override fun search(query: String): List<Entry> {
        val url = searchUrl.replace("{query}", URLEncoder.encode(query, Charsets.UTF_8))
        val results = JsonParser.parseString(Downloads.readText(url, hosts, headers = headers)).at(resultsPath) as? JsonArray
            ?: error("The answer has no list at '$resultsPath'")
        return results.take(MAX_RESULTS).mapNotNull { item ->
            val title = item.at(fields.getValue("title")).text().ifEmpty { return@mapNotNull null }
            val audio = audioBase + item.at(fields.getValue("audioUrl")).text()
            if (!allowed(audio, hosts)) return@mapNotNull null
            val preview = audio.takeIf { it.substringBefore('?').endsWith(".mp3", true) }
            Entry(title, item.at(fields["subtitle"]).text(), preview, item.at(fields["duration"]).text()) { progress ->
                Downloads.fromUrl(audio, title, progress, id, hosts)
            }
        }
    }

    companion object {
        private const val MAX_RESULTS = 50
        const val MAX_FILE_BYTES = 64 * 1024
        private val ID = Regex("[A-Za-z0-9._-]{1,40}")
        private val HOST = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")
        private val PATH = Regex("[A-Za-z0-9_.-]*")
        private val HEADER_NAME = Regex("[A-Za-z0-9-]{1,40}")
        private val HEADER_VALUE = Regex("[A-Za-z0-9 ._/;:=,()-]{0,100}")

        private fun allowed(url: String, hosts: Set<String>): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme.equals("https", true) && uri.userInfo == null && hosts.any { host == it || host.endsWith(".$it") }
        }

        private fun JsonElement?.at(path: String?): JsonElement? =
            path.orEmpty().split('.').filter { it.isNotEmpty() }.fold(this) { e, key ->
                e?.takeIf { it.isJsonObject }?.asJsonObject?.get(key)
            }

        private fun JsonElement?.text(): String = if (this != null && isJsonPrimitive) asString else ""

        /** Validates [json] and builds the source; throws [IllegalArgumentException] if anything is wrong. */
        fun parse(json: String): JsonSource {
            require(json.length <= MAX_FILE_BYTES) { "File too large" }
            val o = runCatching { JsonParser.parseString(json).asJsonObject }.getOrElse { throw IllegalArgumentException("Not a JSON object") }
            fun str(key: String) = o.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Missing '$key'")

            val id = str("id").also { require(ID.matches(it)) { "Invalid id" } }
            val hosts = o.getAsJsonArray("hosts")?.map { it.asString.lowercase() }?.toSet().orEmpty()
            require(hosts.isNotEmpty() && hosts.all { HOST.matches(it) && !it.all { c -> c.isDigit() || c == '.' } }) { "Invalid 'hosts'" }
            val searchUrl = str("searchUrl")
            require("{query}" in searchUrl && allowed(searchUrl.replace("{query}", "x"), hosts)) { "'searchUrl' must be an https URL on a declared host and contain {query}" }
            val resultsPath = o.get("resultsPath")?.asString.orEmpty().also { require(PATH.matches(it)) { "Invalid 'resultsPath'" } }
            val f = o.getAsJsonObject("fields") ?: throw IllegalArgumentException("Missing 'fields'")
            val fields = listOf("title", "subtitle", "duration", "audioUrl").mapNotNull { k ->
                f.get(k)?.asString?.also { require(PATH.matches(it) && it.isNotEmpty()) { "Invalid fields.$k" } }?.let { k to it }
            }.toMap()
            require("title" in fields && "audioUrl" in fields) { "fields.title and fields.audioUrl are required" }
            val audioBase = o.get("audioBase")?.asString.orEmpty()
            require(audioBase.isEmpty() || allowed(audioBase, hosts)) { "'audioBase' must be an https URL on a declared host" }
            val headers = o.getAsJsonObject("headers")?.entrySet()?.associate { (k, v) ->
                val value = v.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                require(HEADER_NAME.matches(k) && HEADER_VALUE.matches(value)) { "Invalid header '$k'" }
                k to value
            }.orEmpty()
            return JsonSource(id, str("name").take(40), str("defaultCategory").take(60), o.get("searchHint")?.asString.orEmpty().take(120), hosts, searchUrl, resultsPath, audioBase, fields, headers)
        }
    }
}
