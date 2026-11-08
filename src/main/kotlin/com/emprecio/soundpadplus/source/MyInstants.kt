package com.emprecio.soundpadplus.source

import com.emprecio.soundpadplus.api.AudioSource
import com.emprecio.soundpadplus.api.Downloads
import com.emprecio.soundpadplus.api.Entry
import com.emprecio.soundpadplus.i18n.I18n
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URLEncoder

object MyInstants : AudioSource {
    private const val API = "https://www.myinstants.com/api/v1/instants/"

    override val id = "MyInstants"
    override val name = "MyInstants"
    override val defaultCategory get() = I18n.t("source.myinstants.category")
    override val searchHint get() = I18n.t("source.myinstants.hint")

    private fun JsonObject.str(k: String) = get(k)?.takeIf { !it.isJsonNull }?.asString ?: ""

    override fun search(query: String): List<Entry> {
        val body = Downloads.readText("$API?name=${URLEncoder.encode(query, Charsets.UTF_8)}&page=1")
        return JsonParser.parseString(body).asJsonObject.getAsJsonArray("results").map { it.asJsonObject }
            .filter { it.str("sound").isNotEmpty() }
            .map { o ->
                val url = o.str("sound")
                Entry(o.str("name"), o.str("tags").ifEmpty { o.str("description") }, url) { progress ->
                    Downloads.fromUrl(url, o.str("name"), progress, o.str("slug"))
                }
            }
    }
}
