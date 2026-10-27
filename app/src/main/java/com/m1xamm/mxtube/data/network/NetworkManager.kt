package com.m1xamm.mxtube.data.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Authenticator as OkHttpAuthenticator
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.Authenticator as JavaAuthenticator
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

object NetworkManager {
    private const val PREFS_NAME = "mx_network"
    private const val KEY_MODE = "mode"
    private const val KEY_PROXY_TYPE = "proxy_type"
    private const val KEY_PROXY_HOST = "proxy_host"
    private const val KEY_PROXY_PORT = "proxy_port"
    private const val KEY_PROXY_USER = "proxy_user"
    private const val KEY_PROXY_PASSWORD = "proxy_password"
    private const val TEST_URL = "https://www.youtube.com/robots.txt"

    private lateinit var prefs: SharedPreferences

    private val _settings = MutableStateFlow(NetworkSettings())
    val settings: StateFlow<NetworkSettings> = _settings.asStateFlow()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(PreferenceDns())
            .proxySelector(SettingsProxySelector())
            .proxyAuthenticator(ProxyAuthenticator())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _settings.value = read()
        installSocksAuthenticator()
    }

    fun save(settings: NetworkSettings): Boolean {
        val next = settings.sanitized()
        if (next.mode == NetworkMode.Proxy && !next.proxyValid) return false
        _settings.value = next
        runCatching { client.connectionPool.evictAll() }
        if (::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_MODE, next.mode.name)
                .putString(KEY_PROXY_TYPE, next.proxyType.name)
                .putString(KEY_PROXY_HOST, next.proxyHost)
                .putInt(KEY_PROXY_PORT, next.proxyPort)
                .putString(KEY_PROXY_USER, next.proxyUser)
                .putString(KEY_PROXY_PASSWORD, next.proxyPassword)
                .apply()
        }
        return true
    }

    suspend fun ipv6Available(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                Dns.SYSTEM.lookup("www.youtube.com").any { address ->
                    address is Inet6Address &&
                        !address.isAnyLocalAddress &&
                        !address.isLinkLocalAddress &&
                        !address.isLoopbackAddress &&
                        !address.isMulticastAddress
                }
            }.getOrDefault(false)
        }

    suspend fun testConnection(): ConnectionTest =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            runCatching {
                client.newCall(
                    Request.Builder()
                        .url(TEST_URL)
                        .header("Cache-Control", "no-cache")
                        .build(),
                ).execute().use { response ->
                    ConnectionTest(
                        success = response.isSuccessful,
                        code = response.code,
                        elapsedMs = System.currentTimeMillis() - started,
                        message = response.message.takeIf { it.isNotBlank() },
                    )
                }
            }.getOrElse { error ->
                ConnectionTest(
                    success = false,
                    code = null,
                    elapsedMs = System.currentTimeMillis() - started,
                    message = error.message ?: error.javaClass.simpleName,
                )
            }
        }

    private fun read(): NetworkSettings {
        val mode =
            runCatching {
                NetworkMode.valueOf(
                    prefs.getString(KEY_MODE, NetworkMode.Direct.name) ?: NetworkMode.Direct.name,
                )
            }.getOrDefault(NetworkMode.Direct)
        val proxyType =
            runCatching {
                ProxyType.valueOf(
                    prefs.getString(KEY_PROXY_TYPE, ProxyType.Http.name) ?: ProxyType.Http.name,
                )
            }.getOrDefault(ProxyType.Http)
        return NetworkSettings(
            mode = mode,
            proxyType = proxyType,
            proxyHost = prefs.getString(KEY_PROXY_HOST, "").orEmpty(),
            proxyPort = prefs.getInt(KEY_PROXY_PORT, 0),
            proxyUser = prefs.getString(KEY_PROXY_USER, "").orEmpty(),
            proxyPassword = prefs.getString(KEY_PROXY_PASSWORD, "").orEmpty(),
        )
    }

    private fun installSocksAuthenticator() {
        runCatching {
            JavaAuthenticator.setDefault(
                object : JavaAuthenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication? {
                        val settings = _settings.value
                        if (!settings.proxyReady ||
                            settings.proxyType != ProxyType.Socks ||
                            settings.proxyUser.isBlank()
                        ) {
                            return null
                        }
                        return PasswordAuthentication(
                            settings.proxyUser,
                            settings.proxyPassword.toCharArray(),
                        )
                    }
                },
            )
        }
    }

    private class SettingsProxySelector : ProxySelector() {
        override fun select(uri: URI): List<Proxy> {
            val settings = _settings.value
            if (!settings.proxyReady) {
                return systemProxy(uri)
            }
            val host = uri.host.orEmpty()
            if (host.equals("localhost", ignoreCase = true) ||
                host == "127.0.0.1" ||
                host == "::1"
            ) {
                return listOf(Proxy.NO_PROXY)
            }
            val type =
                if (settings.proxyType == ProxyType.Socks) {
                    Proxy.Type.SOCKS
                } else {
                    Proxy.Type.HTTP
                }
            return listOf(
                Proxy(type, InetSocketAddress(settings.proxyHost, settings.proxyPort)),
            )
        }

        override fun connectFailed(
            uri: URI?,
            sa: SocketAddress?,
            ioe: IOException?,
        ) = Unit

        private fun systemProxy(uri: URI): List<Proxy> =
            runCatching { ProxySelector.getDefault()?.select(uri) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(Proxy.NO_PROXY)
    }

    private class PreferenceDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            return if (_settings.value.mode == NetworkMode.PreferIpv6) {
                preferIpv6(addresses)
            } else {
                addresses
            }
        }
    }

    private class ProxyAuthenticator : OkHttpAuthenticator {
        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            if (response.code != 407) return null
            val settings = _settings.value
            if (!settings.proxyReady || settings.proxyUser.isBlank()) return null
            if (response.request.header("Proxy-Authorization") != null) return null
            return response.request
                .newBuilder()
                .header(
                    "Proxy-Authorization",
                    Credentials.basic(settings.proxyUser, settings.proxyPassword),
                )
                .build()
        }
    }

    data class ConnectionTest(
        val success: Boolean,
        val code: Int?,
        val elapsedMs: Long,
        val message: String?,
    )
}

internal fun preferIpv6(addresses: List<InetAddress>): List<InetAddress> {
    val ipv6 =
        addresses.filterIsInstance<Inet6Address>().filterNot { address ->
            address.isAnyLocalAddress ||
                address.isLinkLocalAddress ||
                address.isLoopbackAddress ||
                address.isMulticastAddress
        }
    if (ipv6.isEmpty()) return addresses
    val ipv4 = addresses.filterIsInstance<Inet4Address>()
    val other = addresses.filterNot { it is Inet6Address || it is Inet4Address }
    return ipv6 + ipv4 + other
}
