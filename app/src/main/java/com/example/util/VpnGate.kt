package com.example.util

/**
 * Thread-safe VPN readiness state machine (no Android types, so it is unit-testable).
 *
 * Invariant: [isReady] is true only while the network we bound to is still a usable VPN network AND
 * every setup step has completed. Any loss/downgrade flips it to false synchronously, on whichever
 * thread reports it, so request interceptors and the downloader observe the loss immediately.
 */
class VpnGate {
    private val usable = LinkedHashSet<Long>()
    private var bound: Long? = null
    private var ready = false

    /** Records the latest capabilities of a VPN-transport network. */
    @Synchronized
    fun update(id: Long, usableVpn: Boolean) {
        if (usableVpn) usable.add(id) else usable.remove(id)
        if (bound == id && !usableVpn) ready = false
    }

    @Synchronized
    fun lost(id: Long) {
        usable.remove(id)
        if (bound == id) ready = false
    }

    /** Prefers the network we are already bound to, else the oldest usable VPN network. */
    @Synchronized
    fun pickCandidate(): Long? {
        val b = bound
        if (b != null && b in usable) return b
        return usable.firstOrNull()
    }

    /** True when [id] is the bound network, still usable, and fully set up (no work needed). */
    @Synchronized
    fun isReadyFor(id: Long): Boolean = ready && bound == id && id in usable

    /** Starts setup of [id]; the gate stays closed until [markReady]. */
    @Synchronized
    fun beginBind(id: Long) {
        bound = id
        ready = false
    }

    /** Opens the gate iff [id] is still the bound, usable network. */
    @Synchronized
    fun markReady(id: Long): Boolean {
        if (bound == id && id in usable) {
            ready = true
            return true
        }
        return false
    }

    @Synchronized
    fun forceClosed() {
        ready = false
    }

    @Synchronized
    fun isReady(): Boolean = ready

    /** Forgets everything (used when the browser screen is torn down). Gate ends closed. */
    @Synchronized
    fun shutdown() {
        usable.clear()
        bound = null
        ready = false
    }
}
