package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSafetyTest {
    @Test fun parsesBasicHttps() {
        val p = UrlSafety.parse("https://Example.COM/path?q=1#f")!!
        assertEquals("https", p.scheme)
        assertEquals("example.com", p.host)
        assertEquals(443, p.port)
        assertFalse(p.hasUserInfo)
        assertEquals("https://example.com", p.origin)
    }

    @Test fun defaultPortIsElidedFromOriginButCustomPortIsNot() {
        assertEquals("https://a.com", UrlSafety.parse("https://a.com:443/")!!.origin)
        assertEquals("https://a.com:8443", UrlSafety.parse("https://a.com:8443/")!!.origin)
        assertEquals("http://a.com", UrlSafety.parse("http://a.com:80")!!.origin)
    }

    @Test fun rejectsBadPorts() {
        assertNull(UrlSafety.parse("https://a.com:0/"))
        assertNull(UrlSafety.parse("https://a.com:65536/"))
        assertNull(UrlSafety.parse("https://a.com:99999999/"))
        assertNull(UrlSafety.parse("https://a.com:80a/"))
        assertNull(UrlSafety.parse("https://a.com:/"))
    }

    @Test fun detectsUserInfoAndBackslashTrick() {
        assertTrue(UrlSafety.parse("https://google.com@evil.com/")!!.hasUserInfo)
        assertEquals("evil.com", UrlSafety.parse("https://google.com@evil.com/")!!.host)
        // Backslash ends the authority (WHATWG): host is good.com, NOT evil.com
        assertEquals("good.com", UrlSafety.parse("https://good.com\\@evil.com/")!!.host)
    }

    @Test fun rejectsMalformedInput() {
        assertNull(UrlSafety.parse(null))
        assertNull(UrlSafety.parse(""))
        assertNull(UrlSafety.parse("example.com"))
        assertNull(UrlSafety.parse("https://"))
        assertNull(UrlSafety.parse("https:// example.com"))
        assertNull(UrlSafety.parse("https://exa\u0000mple.com"))
        assertNull(UrlSafety.parse("https://exa%6Dple.com"))
        assertNull(UrlSafety.parse("https://a..com"))
        assertNull(UrlSafety.parse("1http://a.com"))
    }

    @Test fun idnHostsBecomePunycode() {
        assertEquals("xn--mnchen-3ya.de", UrlSafety.parse("https://münchen.de/")!!.host)
    }

    @Test fun ipv6Literals() {
        val p = UrlSafety.parse("https://[2001:DB8::1]:8443/x")!!
        assertTrue(p.isIpv6Literal)
        assertEquals("2001:db8::1", p.host)
        assertEquals(8443, p.port)
        assertEquals("https://[2001:db8::1]:8443", p.origin)
        assertNull(UrlSafety.parse("https://[::1/"))
        assertNull(UrlSafety.parse("https://[zz::1]/"))
        assertNull(UrlSafety.parse("https://[::1]x/"))
    }

    @Test fun trailingDotIsStripped() {
        assertEquals("example.com", UrlSafety.parse("https://example.com./")!!.host)
    }

    @Test fun localhostAndPrivateIpv4() {
        for (h in listOf("localhost", "foo.localhost", "printer.local", "x.internal", "127.0.0.1", "127.1.2.3",
            "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254", "0.0.0.0",
            "100.64.0.1", "224.0.0.1", "255.255.255.255")) {
            assertTrue("$h must be private/local", UrlSafety.isPrivateOrLocalHost(h))
        }
    }

    @Test fun publicIpv4IsNotPrivate() {
        for (h in listOf("8.8.8.8", "1.1.1.1", "172.15.0.1", "172.32.0.1", "100.63.0.1", "100.128.0.1", "example.com", "1e100.net")) {
            assertFalse("$h must be public", UrlSafety.isPrivateOrLocalHost(h))
        }
    }

    @Test fun ambiguousNumericHostsAreTreatedAsLocal() {
        for (h in listOf("2130706433", "0x7f.1", "127.1", "0177.0.0.1", "999.1.1.1", "1.2.3")) {
            assertTrue("$h is ambiguous and must be blocked", UrlSafety.isPrivateOrLocalHost(h))
        }
    }

    @Test fun ipv6PrivateAndPublic() {
        for (h in listOf("::1", "[::1]", "fe80::1", "fd00::1", "fc00::1", "::", "ff02::1", "::ffff:127.0.0.1",
            "::ffff:10.0.0.1", "64:ff9b::7f00:1", "0:0:0:0:0:0:0:1")) {
            assertTrue("$h must be private/local", UrlSafety.isPrivateOrLocalHost(h))
        }
        assertFalse(UrlSafety.isPrivateOrLocalHost("2001:4860:4860::8888"))
        assertFalse(UrlSafety.isPrivateOrLocalHost("::ffff:8.8.8.8"))
    }

    @Test fun nullOrBlankHostIsNotPrivate() {
        assertFalse(UrlSafety.isPrivateOrLocalHost(null))
        assertFalse(UrlSafety.isPrivateOrLocalHost(""))
    }

    @Test fun normalizeObservedHost() {
        assertEquals("example.com", UrlSafety.normalizeObservedHost("EXAMPLE.com."))
        assertEquals("::1", UrlSafety.normalizeObservedHost("[::1]"))
        assertNull(UrlSafety.normalizeObservedHost(null))
        assertNotNull(UrlSafety.normalizeObservedHost("münchen.de"))
    }
}
