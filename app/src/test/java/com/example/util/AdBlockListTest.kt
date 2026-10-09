package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockListTest {
    @Test
    fun blocksExactAndSubdomainHosts() {
        assertTrue(AdBlockList.isBlockedHost("doubleclick.net"))
        assertTrue(AdBlockList.isBlockedHost("stats.google-analytics.com"))
        assertTrue(AdBlockList.isBlockedHost("connect.facebook.net"))
        assertTrue(AdBlockList.isBlockedHost("a.b.c.googlesyndication.com"))
    }

    @Test
    fun doesNotUseDangerousSubstringMatching() {
        assertFalse(AdBlockList.isBlockedHost("not-google-analytics.com.example"))
        assertFalse(AdBlockList.isBlockedHost("notdoubleclick.net"))
        assertFalse(AdBlockList.isBlockedHost("doubleclick.net.evil.example"))
        assertFalse(AdBlockList.isBlockedHost("example.com"))
        assertFalse(AdBlockList.isBlockedHost("facebook.com"))
        assertFalse(AdBlockList.isBlockedHost("google.com"))
        assertFalse(AdBlockList.isBlockedHost("analytics.example.com"))
    }

    @Test
    fun normalizesTrailingDotAndCase() {
        assertTrue(AdBlockList.isBlockedHost("DOUBLECLICK.NET."))
        assertTrue(AdBlockList.isBlockedHost("A.GOOGLESYNDICATION.COM"))
    }

    @Test
    fun ipLiteralsPortsAndPunycodeAreNeverMatched() {
        assertFalse(AdBlockList.isBlockedHost("1.2.3.4"))
        assertFalse(AdBlockList.isBlockedHost("[::1]"))
        assertFalse(AdBlockList.isBlockedHost("::1"))
        assertFalse(AdBlockList.isBlockedHost("xn--mnchen-3ya.de"))
        assertFalse(AdBlockList.isBlockedHost(null))
        assertFalse(AdBlockList.isBlockedHost(""))
        assertFalse(AdBlockList.isBlockedHost("."))
    }

    @Test
    fun categories() {
        assertEquals(AdBlockList.Category.AD, AdBlockList.categoryOf("ads.doubleclick.net"))
        assertEquals(AdBlockList.Category.TRACKER, AdBlockList.categoryOf("www.google-analytics.com"))
        assertEquals(AdBlockList.Category.TRACKER, AdBlockList.categoryOf("static.hotjar.com"))
        assertNull(AdBlockList.categoryOf("hotjar-clone.example"))
    }
}
