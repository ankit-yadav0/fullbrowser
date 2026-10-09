package com.example

import com.example.util.AdBlockList
import com.example.util.DrmDiagnostics
import com.example.util.UrlHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAndBrowserLogicTest {
    @Test
    fun adBlock_onlyMatchesExactHostBoundaries() {
        assertTrue(AdBlockList.isBlockedHost("doubleclick.net"))
        assertTrue(AdBlockList.isBlockedHost("ads.doubleclick.net"))
        assertFalse(AdBlockList.isBlockedHost("doubleclick.net.evil.example"))
    }

    @Test
    fun sanitizer_redactsCommonCredentialForms() {
        val input = "Bearer abc123 access_token=secret password=hunter2 api_key=xyz cookie: session=abc"
        val output = DrmDiagnostics.sanitizeLog(input)
        assertFalse(output.contains("abc123"))
        assertFalse(output.contains("secret"))
        assertFalse(output.contains("hunter2"))
        assertFalse(output.contains("xyz"))
        assertFalse(output.contains("session=abc"))
    }

    @Test
    fun urlHelper_doesNotRewriteExistingWebUrls() {
        assertTrue(UrlHelper.normalizeUrl("https://example.com/path?q=1").startsWith("https://example.com"))
        assertTrue(UrlHelper.normalizeUrl("http://example.com").startsWith("http://example.com"))
    }
}
