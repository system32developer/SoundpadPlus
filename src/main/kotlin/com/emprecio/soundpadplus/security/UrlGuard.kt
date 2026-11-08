package com.emprecio.soundpadplus.security

import com.emprecio.soundpadplus.i18n.I18n
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

object UrlGuard {
    fun check(url: URI, allowedHosts: Set<String>? = null) {
        val host = url.host?.lowercase()
        val ok = url.scheme.equals("https", true) && url.userInfo == null && host != null &&
            (allowedHosts == null || allowedHosts.any { host == it || host.endsWith(".$it") }) &&
            runCatching { InetAddress.getAllByName(host).none(::isInternal) }.getOrDefault(false)
        require(ok) { I18n.t("security.blockedUrl", url.host ?: url) }
    }

    fun isInternal(a: InetAddress): Boolean {
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return true
        val b = a.address
        return when (a) {
            is Inet6Address -> (b[0].toInt() and 0xfe) == 0xfc
            is Inet4Address -> b[0].toInt() == 100 && (b[1].toInt() and 0xc0) == 64
            else -> false
        }
    }
}
