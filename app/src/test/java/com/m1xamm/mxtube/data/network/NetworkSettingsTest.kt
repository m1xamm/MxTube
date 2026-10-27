package com.m1xamm.mxtube.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

class NetworkSettingsTest {
    @Test
    fun proxyValidationRejectsBrokenAddresses() {
        assertFalse(NetworkSettings().proxyValid)
        assertFalse(NetworkSettings(proxyHost = "example.com", proxyPort = 0).proxyValid)
        assertFalse(NetworkSettings(proxyHost = "http://example.com", proxyPort = 8080).proxyValid)
        assertFalse(NetworkSettings(proxyHost = "bad host", proxyPort = 8080).proxyValid)
        assertFalse(NetworkSettings(proxyHost = "example.com", proxyPort = 65536).proxyValid)
        assertTrue(NetworkSettings(proxyHost = "example.com", proxyPort = 8080).proxyValid)
    }

    @Test
    fun proxyReadyRequiresProxyMode() {
        val settings = NetworkSettings(proxyHost = "example.com", proxyPort = 8080)
        assertFalse(settings.proxyReady)
        assertTrue(settings.copy(mode = NetworkMode.Proxy).proxyReady)
    }

    @Test
    fun sanitizeTrimsHostAndUser() {
        val settings =
            NetworkSettings(
                proxyHost = "  example.com ",
                proxyPort = 8080,
                proxyUser = " user ",
            ).sanitized()
        assertEquals("example.com", settings.proxyHost)
        assertEquals("user", settings.proxyUser)
    }

    @Test
    fun preferIpv6ReordersGlobalIpv6AndDropsLinkLocal() {
        val ipv4 = InetAddress.getByName("192.0.2.10")
        val globalIpv6 = InetAddress.getByName("2001:db8::10")
        val linkLocal = InetAddress.getByName("fe80::10")
        assertTrue(ipv4 is Inet4Address)
        assertTrue(globalIpv6 is Inet6Address)

        val ordered = preferIpv6(listOf(ipv4, linkLocal, globalIpv6))

        assertEquals(listOf(globalIpv6, ipv4), ordered)
    }

    @Test
    fun preferIpv6FallsBackWhenOnlyLinkLocalExists() {
        val ipv4 = InetAddress.getByName("192.0.2.10")
        val linkLocal = InetAddress.getByName("fe80::10")
        val original = listOf(ipv4, linkLocal)

        assertEquals(original, preferIpv6(original))
    }
}
