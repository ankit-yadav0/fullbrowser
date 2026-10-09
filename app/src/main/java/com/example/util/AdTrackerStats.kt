package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Thread-safe session counters for requests blocked by the WebView filter. */
object AdTrackerStats {
    data class Snapshot(
        val adsBlocked: Int = 0,
        val trackersBlocked: Int = 0
    ) {
        val totalBlocked: Int get() = adsBlocked + trackersBlocked
    }

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    /** Counts by the list category of [host] (exact/subdomain match), never by substring. */
    fun recordBlocked(host: String?) {
        val tracker = AdBlockList.categoryOf(host) == AdBlockList.Category.TRACKER
        _snapshot.update { current ->
            if (tracker) current.copy(trackersBlocked = current.trackersBlocked + 1)
            else current.copy(adsBlocked = current.adsBlocked + 1)
        }
    }

    fun reset() {
        _snapshot.value = Snapshot()
    }
}
