package com.m1xamm.mxtube.data.network

enum class NetworkMode {
    Direct,
    PreferIpv6,
    Proxy,
}

enum class ProxyType {
    Http,
    Socks,
}

data class NetworkSettings(
    val mode: NetworkMode = NetworkMode.Direct,
    val proxyType: ProxyType = ProxyType.Http,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyUser: String = "",
    val proxyPassword: String = "",
) {
    val proxyValid: Boolean
        get() =
            proxyHost.isNotBlank() &&
                !proxyHost.contains("://") &&
                !proxyHost.any(Char::isWhitespace) &&
                proxyPort in 1..65535

    val proxyReady: Boolean
        get() = mode == NetworkMode.Proxy && proxyValid

    fun sanitized(): NetworkSettings =
        copy(
            proxyHost = proxyHost.trim(),
            proxyUser = proxyUser.trim(),
        )
}
