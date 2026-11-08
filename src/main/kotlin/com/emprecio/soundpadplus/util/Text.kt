package com.emprecio.soundpadplus.util

fun unescapeXml(s: String) = s.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

/** Text from outside the app, safe to show in a Swing label: no control characters and no HTML (Swing renders `<html>`). */
fun plain(s: String, max: Int = 300) = s.filter { !it.isISOControl() }.replace('<', '‹').replace('>', '›').take(max)
