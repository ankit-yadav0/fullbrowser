package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlHelperTest {
    private fun assertSearch(input: String) {
        val out = UrlHelper.normalizeUrl(input)
        assertTrue("'$input' must become a search, was '$out'", out.startsWith("https://www.google.com/search?q="))
    }

    @Test fun preservesPortsAndLocalHosts() {
        assertEquals("https://example.com:8080", UrlHelper.normalizeUrl("example.com:8080"))
        assertEquals("https://localhost:3000", UrlHelper.normalizeUrl("localhost:3000"))
        assertEquals("https://192.168.1.1", UrlHelper.normalizeUrl("192.168.1.1"))
        assertEquals("https://1.2.3.4:8080", UrlHelper.normalizeUrl("1.2.3.4:8080"))
    }

    @Test fun preservesFragmentsAndExplicitSchemes() {
        assertEquals("https://example.com#top", UrlHelper.normalizeUrl("example.com#top"))
        assertEquals("HTTP://EXAMPLE.COM", UrlHelper.normalizeUrl("HTTP://EXAMPLE.COM"))
    }

    @Test fun ipv6AndIdnAreNavigatedNotSearched() {
        assertEquals("https://[::1]:8080", UrlHelper.normalizeUrl("[::1]:8080"))
        assertEquals("https://m\u00FCnchen.de", UrlHelper.normalizeUrl("m\u00FCnchen.de"))
    }

    @Test fun nonWebSchemesAndMalformedInputBecomeSearches() {
        assertSearch("file:///sdcard/secret.txt")
        assertSearch("javascript:alert(1)")
        assertSearch("intent://a.b#Intent;scheme=x;end")
        assertSearch("ftp://example.com/x")
        assertSearch("data:text/html,<script>1</script>")
        assertSearch("user@example.com")
        assertSearch("example.com:99999")
        assertSearch("myserver")
        assertSearch("a b.com")
    }
}
