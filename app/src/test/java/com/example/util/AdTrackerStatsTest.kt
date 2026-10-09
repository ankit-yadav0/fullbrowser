package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AdTrackerStatsTest {
    @Test fun countsByListCategoryNotBySubstring() {
        AdTrackerStats.reset()
        AdTrackerStats.recordBlocked("ads.doubleclick.net")        // AD
        AdTrackerStats.recordBlocked("www.google-analytics.com")   // TRACKER
        AdTrackerStats.recordBlocked("static.hotjar.com")          // TRACKER
        val s = AdTrackerStats.snapshot.value
        assertEquals(1, s.adsBlocked)
        assertEquals(2, s.trackersBlocked)
        assertEquals(3, s.totalBlocked)
        AdTrackerStats.reset()
        assertEquals(0, AdTrackerStats.snapshot.value.totalBlocked)
    }
}
