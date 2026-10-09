package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
    private fun block(url: String, gesture: Boolean = true) =
        NavigationPolicy.decideMainFrame(url, gesture) is NavDecision.Block

    @Test fun httpsIsAllowedHttpIsBlocked() {
        assertEquals(NavDecision.Allow, NavigationPolicy.decideMainFrame("https://example.com/a", false))
        assertTrue(block("http://example.com/"))
        assertTrue(block("HTTP://EXAMPLE.COM/"))
    }

    @Test fun credentialsAndMalformedHttpsAreBlocked() {
        assertTrue(block("https://google.com@evil.com/"))
        assertTrue(block("https://"))
        assertTrue(block("https://a.com:99999/"))
    }

    @Test fun dangerousSchemesAreBlockedEvenWithGesture() {
        for (u in listOf("javascript:alert(1)", "file:///sdcard/a", "content://x/y", "data:text/html,hi",
            "ftp://a.com/", "market://details?id=x", "whatsapp://send", "tg://resolve", "vbscript:x",
            "view-source:https://a.com", "about:config", "chrome://settings", "android-app://com.x")) {
            assertTrue("$u must be blocked", block(u, true))
        }
    }

    @Test fun blobHttpsAndAboutBlankAreAllowedButBlobHttpIsNot() {
        assertEquals(NavDecision.Allow, NavigationPolicy.decideMainFrame("blob:https://a.com/uuid", false))
        assertEquals(NavDecision.Allow, NavigationPolicy.decideMainFrame("about:blank", false))
        assertTrue(block("blob:http://a.com/uuid"))
        assertTrue(block("blob:null/uuid"))
    }

    @Test fun externalSchemesNeedAGesture() {
        assertTrue(NavigationPolicy.decideMainFrame("mailto:a@b.com", true) is NavDecision.External)
        assertTrue(NavigationPolicy.decideMainFrame("tel:+123", true) is NavDecision.External)
        assertTrue(NavigationPolicy.decideMainFrame("sms:+123", true) is NavDecision.External)
        assertTrue(block("mailto:a@b.com", false))
        assertTrue(block("tel:+123", false))
    }

    @Test fun mailtoAttachmentParametersAreBlockedButOrdinaryMailtoIsNot() {
        assertTrue(block("mailto:a@b.com?attach=file:///sdcard/secret.txt"))
        assertTrue(block("MAILTO:a@b.com?subject=x&Attachment=content://x/y"))
        assertTrue(block("mailto:a@b.com?attachments=file:///x"))
        assertTrue(NavigationPolicy.decideMainFrame("mailto:a@b.com?subject=Hi&body=please%20attach%20the%20file", true) is NavDecision.External)
        assertTrue(NavigationPolicy.decideMainFrame("mailto:a@b.com", true) is NavDecision.External)
    }

    @Test fun intentSchemeNeverLaunchesAnAppOnlyHttpsFallback() {
        val withFallback = "intent://x.com/p#Intent;scheme=https;package=com.evil;S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fok;end"
        val d = NavigationPolicy.decideMainFrame(withFallback, true)
        assertEquals(NavDecision.LoadInWebView("https://example.com/ok"), d)
        assertTrue(block(withFallback, false))
        assertTrue(block("intent://x.com/p#Intent;scheme=https;package=com.evil;end"))
        // an http fallback is upgraded to https (never loaded over http)
        assertEquals(NavDecision.LoadInWebView("https://example.com"),
            NavigationPolicy.decideMainFrame("intent://x#Intent;S.browser_fallback_url=http%3A%2F%2Fexample.com;end", true))
        assertTrue(block("intent://x#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"))
        assertTrue(block("intent://x#Intent;S.browser_fallback_url=https%3A%2F%2Fa%40evil.com;end"))
    }

    @Test fun toLoadableUrlUpgradesHttpAndRejectsOthers() {
        assertEquals("https://example.com/a", NavigationPolicy.toLoadableUrl("http://example.com/a"))
        assertEquals("https://EXAMPLE.com/a", NavigationPolicy.toLoadableUrl("HTTP://EXAMPLE.com/a"))
        assertEquals("https://example.com", NavigationPolicy.toLoadableUrl(" https://example.com "))
        assertNull(NavigationPolicy.toLoadableUrl("javascript:alert(1)"))
        assertNull(NavigationPolicy.toLoadableUrl("file:///x"))
        assertNull(NavigationPolicy.toLoadableUrl("https://a@evil.com"))
        assertNull(NavigationPolicy.toLoadableUrl(null))
        assertNull(NavigationPolicy.toLoadableUrl(""))
    }

    @Test fun isWebUrl() {
        assertTrue(NavigationPolicy.isWebUrl("https://a.com"))
        assertTrue(NavigationPolicy.isWebUrl("http://a.com"))
        assertFalse(NavigationPolicy.isWebUrl("about:blank"))
        assertFalse(NavigationPolicy.isWebUrl("data:text/html,x"))
        assertFalse(NavigationPolicy.isWebUrl("chrome-error://chromewebdata/"))
    }

    @Test fun privateNetworkProbeRules() {
        assertTrue(NavigationPolicy.isPrivateNetworkProbe("127.0.0.1", "example.com"))
        assertTrue(NavigationPolicy.isPrivateNetworkProbe("192.168.0.1", "example.com"))
        assertTrue(NavigationPolicy.isPrivateNetworkProbe("localhost", "example.com"))
        assertTrue(NavigationPolicy.isPrivateNetworkProbe("[::1]", "example.com"))
        assertTrue(NavigationPolicy.isPrivateNetworkProbe("2130706433", null))
        assertFalse(NavigationPolicy.isPrivateNetworkProbe("example.com", "example.com"))
        // A private page may talk to private targets (local dev / intranet)
        assertFalse(NavigationPolicy.isPrivateNetworkProbe("192.168.0.2", "192.168.0.1"))
    }
}
