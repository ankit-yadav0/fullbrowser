package com.example

import com.example.util.BlockedResponses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlockedResponsesTest {
    @Test fun adTrackerResponseIs204EmptyWithoutIdentifyingHeaders() {
        val r = BlockedResponses.adTracker()
        assertEquals(204, r.statusCode)
        assertEquals(-1, r.data.read())
        assertEquals("no-store", r.responseHeaders["Cache-Control"])
        assertFalse(r.responseHeaders.keys.any { it.startsWith("X-", ignoreCase = true) })
    }

    @Test fun vpnClosedOrPrivateProbeResponseIs503Empty() {
        val r = BlockedResponses.refused()
        assertEquals(503, r.statusCode)
        assertEquals(-1, r.data.read())
        assertEquals("no-store", r.responseHeaders["Cache-Control"])
    }
}
