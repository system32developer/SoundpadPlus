package com.emprecio.soundpadplus.config

import com.emprecio.soundpadplus.api.AudioSource

var AudioSource.category: String
    get() = Settings["cat.$id", defaultCategory]
    set(value) { Settings["cat.$id"] = cleanCategory(value).ifEmpty { defaultCategory } }

fun cleanCategory(name: String) = name.filter { !it.isISOControl() && it != '"' && it != '\\' }.trim().take(60)
