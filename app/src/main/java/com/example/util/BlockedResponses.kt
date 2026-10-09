package com.example.util

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/** Synthetic WebView responses used to stop a request before it reaches the network stack. */
object BlockedResponses {
    private val headers = mapOf("Cache-Control" to "no-store")

    private fun empty(status: Int, reason: String) = WebResourceResponse(
        "text/plain", "utf-8", status, reason, headers, ByteArrayInputStream(ByteArray(0))
    )

    /** Ad/tracker shield hit. */
    fun adTracker(): WebResourceResponse = empty(204, "No Content")

    /** Request refused because the VPN gate is closed or the target is a private-network probe. */
    fun refused(): WebResourceResponse = empty(503, "Blocked")
}
