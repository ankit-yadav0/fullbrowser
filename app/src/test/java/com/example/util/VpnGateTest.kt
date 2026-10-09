package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnGateTest {
    @Test fun startsClosed() {
        val g = VpnGate()
        assertFalse(g.isReady())
        assertNull(g.pickCandidate())
    }

    @Test fun opensOnlyAfterBindAndMarkReady() {
        val g = VpnGate()
        g.update(1L, true)
        assertFalse(g.isReady())
        assertEquals(1L, g.pickCandidate())
        g.beginBind(1L)
        assertFalse(g.isReady())
        assertTrue(g.markReady(1L))
        assertTrue(g.isReady())
        assertTrue(g.isReadyFor(1L))
    }

    @Test fun lossClosesGateSynchronously() {
        val g = VpnGate()
        g.update(1L, true); g.beginBind(1L); g.markReady(1L)
        g.lost(1L)
        assertFalse(g.isReady())
        assertNull(g.pickCandidate())
    }

    @Test fun capabilityDowngradeClosesGate() {
        val g = VpnGate()
        g.update(1L, true); g.beginBind(1L); g.markReady(1L)
        g.update(1L, false) // e.g. lost INTERNET capability
        assertFalse(g.isReady())
    }

    @Test fun markReadyFailsIfNetworkVanishedDuringSetup() {
        val g = VpnGate()
        g.update(1L, true); g.beginBind(1L)
        g.lost(1L) // VPN dropped while the proxy override was being cleared
        assertFalse(g.markReady(1L))
        assertFalse(g.isReady())
    }

    @Test fun lossOfAnotherNetworkDoesNotCloseGate() {
        val g = VpnGate()
        g.update(1L, true); g.update(2L, true); g.beginBind(1L); g.markReady(1L)
        g.lost(2L)
        assertTrue(g.isReady())
    }

    @Test fun failsOverToSecondVpnOnlyAfterRebind() {
        val g = VpnGate()
        g.update(1L, true); g.update(2L, true); g.beginBind(1L); g.markReady(1L)
        g.lost(1L)
        assertFalse(g.isReady())
        assertEquals(2L, g.pickCandidate())
        g.beginBind(2L)
        assertFalse(g.isReady())
        assertTrue(g.markReady(2L))
        assertTrue(g.isReady())
    }

    @Test fun preferBoundNetworkWhenStillUsable() {
        val g = VpnGate()
        g.update(1L, true); g.update(2L, true); g.beginBind(2L)
        assertEquals(2L, g.pickCandidate())
    }

    @Test fun staleReadyForOldNetworkDoesNotOpenGate() {
        val g = VpnGate()
        g.update(1L, true); g.update(2L, true)
        g.beginBind(1L)
        g.beginBind(2L) // superseded
        assertFalse(g.markReady(1L))
        assertFalse(g.isReady())
    }

    @Test fun shutdownClosesAndForgets() {
        val g = VpnGate()
        g.update(1L, true); g.beginBind(1L); g.markReady(1L)
        g.shutdown()
        assertFalse(g.isReady())
        assertNull(g.pickCandidate())
    }
}
