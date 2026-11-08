package com.emprecio.soundpadplus.config

import com.emprecio.soundpadplus.api.AudioSource

var AudioSource.category: String
    get() = Settings["cat.$id", defaultCategory]
    set(value) { Settings["cat.$id"] = cleanCategory(value).ifEmpty { defaultCategory } }

/** Plugins are on unless the user switched them off in Settings. */
var AudioSource.enabled: Boolean
    get() = Settings["off.$id", "false"] != "true"
    set(value) { Settings["off.$id"] = (!value).toString() }

fun cleanCategory(name: String) = name.filter { !it.isISOControl() && it != '"' && it != '\\' }.trim().take(60)
