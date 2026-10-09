package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAndPopupPolicyTest {
    private val cam = listOf(PermissionPolicy.VIDEO_CAPTURE)
    private val camMic = listOf(PermissionPolicy.VIDEO_CAPTURE, PermissionPolicy.AUDIO_CAPTURE)

    @Test fun httpsOriginPromptsUser() {
        assertEquals(PermissionPolicy.Verdict.PROMPT_USER, PermissionPolicy.evaluate("https://meet.example.com/", camMic, false))
        assertEquals(PermissionPolicy.Verdict.PROMPT_USER,
            PermissionPolicy.evaluate("https://a.com", listOf(PermissionPolicy.PROTECTED_MEDIA_ID), false))
    }

    @Test fun nonHttpsOriginsAreDenied() {
        for (o in listOf("http://a.com", "http://localhost", "http://127.0.0.1", "file:///x", "about:blank", "", null, "https://u@evil.com")) {
            assertEquals("$o", PermissionPolicy.Verdict.DENY_INSECURE_ORIGIN, PermissionPolicy.evaluate(o, cam, false))
        }
    }

    @Test fun unsupportedOrEmptyResourcesAreDenied() {
        assertEquals(PermissionPolicy.Verdict.DENY_UNSUPPORTED_RESOURCE, PermissionPolicy.evaluate("https://a.com", emptyList(), false))
        assertEquals(PermissionPolicy.Verdict.DENY_UNSUPPORTED_RESOURCE,
            PermissionPolicy.evaluate("https://a.com", cam + "android.webkit.resource.MIDI_SYSEX", false))
    }

    @Test fun secondConcurrentRequestIsDenied() {
        assertEquals(PermissionPolicy.Verdict.DENY_BUSY, PermissionPolicy.evaluate("https://a.com", cam, true))
        assertEquals(PermissionPolicy.Verdict.DENY_BUSY, PermissionPolicy.evaluate("https://b.com", camMic, true))
    }

    @Test fun androidPermissionMapping() {
        assertEquals(listOf("android.permission.CAMERA"), PermissionPolicy.androidPermissionsFor(cam))
        assertEquals(listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"), PermissionPolicy.androidPermissionsFor(camMic))
        assertTrue(PermissionPolicy.androidPermissionsFor(listOf(PermissionPolicy.PROTECTED_MEDIA_ID)).isEmpty())
    }

    @Test fun embeddedFrameDetectionUsesExactHostComparison() {
        assertTrue(PermissionPolicy.isEmbeddedFrameRequest("https://a.com/page", "https://ads.example.net"))
        assertFalse(PermissionPolicy.isEmbeddedFrameRequest("https://a.com/page", "https://a.com"))
        assertFalse(PermissionPolicy.isEmbeddedFrameRequest("https://A.com/page", "https://a.com"))
        // No public-suffix list: a sibling subdomain is conservatively reported as embedded.
        assertTrue(PermissionPolicy.isEmbeddedFrameRequest("https://www.a.com/", "https://a.com"))
        assertFalse(PermissionPolicy.isEmbeddedFrameRequest(null, "https://a.com"))
        assertFalse(PermissionPolicy.isEmbeddedFrameRequest("https://a.com", "garbage"))
    }

    @Test fun popupRequiresGestureVpnAndNoExistingPopup() {
        assertTrue(PopupPolicy.allow(userGesture = true, networkReady = true, popupAlreadyOpen = false))
        assertFalse(PopupPolicy.allow(userGesture = false, networkReady = true, popupAlreadyOpen = false))
        assertFalse(PopupPolicy.allow(userGesture = true, networkReady = false, popupAlreadyOpen = false))
        assertFalse(PopupPolicy.allow(userGesture = true, networkReady = true, popupAlreadyOpen = true))
    }

    @Test fun crashLoopGuardStopsReloadsAfterThreeCrashesInWindow() {
        var now = 0L
        val g = CrashLoopGuard(maxCrashes = 3, windowMs = 60_000L, clock = { now })
        assertTrue(g.recordCrash()); now += 1_000
        assertTrue(g.recordCrash()); now += 1_000
        assertFalse(g.recordCrash())
    }

    @Test fun crashLoopGuardForgetsOldCrashes() {
        var now = 0L
        val g = CrashLoopGuard(maxCrashes = 3, windowMs = 60_000L, clock = { now })
        g.recordCrash(); now += 1_000
        g.recordCrash(); now += 120_000
        assertTrue(g.recordCrash())
        g.reset()
        assertTrue(g.recordCrash())
    }
}
