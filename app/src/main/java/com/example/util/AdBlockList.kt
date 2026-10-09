package com.example.util

/**
 * Small host-based ad/tracker list. Matching is exact-host or true-subdomain only (no substring tests).
 *
 * It can only see requests that WebView routes through shouldInterceptRequest: it cannot see
 * WebSocket traffic, cannot resolve DNS (so CNAME-cloaked first-party trackers are NOT detected) and
 * performs no cosmetic filtering. It is a lightweight shield, not an EasyList/uBlock equivalent.
 */
object AdBlockList {

    private val adHosts: Set<String> = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "googletagservices.com", "adservice.google.com", "adservice.google.co.in",
        "adnxs.com", "adsrvr.org", "adroll.com", "amazon-adsystem.com",
        "criteo.com", "criteo.net", "outbrain.com", "taboola.com",
        "taboolasyndication.com", "pubmatic.com", "rubiconproject.com",
        "openx.net", "casalemedia.com", "bidswitch.net", "yieldmo.com",
        "media.net", "moatads.com", "smartadserver.com", "33across.com",
        "contextweb.com", "sharethrough.com", "smaato.net", "inmobi.com",
        "unityads.unity3d.com", "vungle.com", "applovin.com", "ironsource.com",
        "adsafeprotected.com", "ads-twitter.com"
    )

    private val trackerHosts: Set<String> = setOf(
        "google-analytics.com", "googletagmanager.com", "mixpanel.com",
        "segment.io", "segment.com", "amplitude.com", "newrelic.com",
        "nr-data.net", "clarity.ms", "bat.bing.com", "analytics.tiktok.com",
        "stats.wp.com", "mc.yandex.ru", "hotjar.com", "fullstory.com",
        "mouseflow.com", "clicktale.net", "heap.io", "heapanalytics.com",
        "quantserve.com", "scorecardresearch.com",
        "connect.facebook.net", "facebook.net", "analytics.twitter.com",
        "tr.snapchat.com", "sc-static.net",
        "fingerprint.com", "fpjs.io", "datadome.co", "perimeterx.net"
    )

    enum class Category { AD, TRACKER }

    private fun matches(set: Set<String>, host: String): Boolean {
        if (host in set) return true
        var idx = host.indexOf('.')
        while (idx >= 0 && idx < host.length - 1) {
            if (host.substring(idx + 1) in set) return true
            idx = host.indexOf('.', idx + 1)
        }
        return false
    }

    /** Returns the list category for an exact host / real subdomain match, else null. */
    fun categoryOf(host: String?): Category? {
        val h = UrlSafety.normalizeObservedHost(host) ?: return null
        if (h.contains(':')) return null // IPv6 literal: never in the list
        return when {
            matches(adHosts, h) -> Category.AD
            matches(trackerHosts, h) -> Category.TRACKER
            else -> null
        }
    }

    fun isBlockedHost(host: String?): Boolean = categoryOf(host) != null
}
