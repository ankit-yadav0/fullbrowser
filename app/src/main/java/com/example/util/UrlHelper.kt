package com.example.util

import java.net.URI
import java.net.URLEncoder

object UrlHelper {

    /**
     * Normalizes a user-entered URL or search term.
     * - Explicit http:// and https:// URLs are returned unchanged (policy is applied later by NavigationPolicy).
     * - Any OTHER explicit scheme (file:, intent:, ftp:, custom schemes ...) is never navigated: it becomes a search.
     * - Domain-like input (example.com, localhost:3000, 192.168.1.1, [::1]:8080, münchen.de) gets https://.
     * - Input with spaces, embedded credentials, an invalid port/host, or no domain structure is a search query.
     * Parsing uses the single strict parser in [UrlSafety] (IDN-aware).
     */
    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            return trimmed
        }
        if (trimmed.contains("://")) return searchUrl(trimmed)

        val candidate = "https://$trimmed"
        val parsed = UrlSafety.parse(candidate)
        val looksLikeHost = parsed != null && !parsed.hasUserInfo && (
            parsed.host == "localhost" || parsed.host.contains('.') || parsed.isIpv6Literal
            )
        return if (!trimmed.contains(' ') && looksLikeHost) candidate else searchUrl(trimmed)
    }

    private fun searchUrl(query: String): String =
        "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")

    /**
     * Extracts a display title from a URL or web page title.
     * Defaults to host or domain name if title is empty.
     */
    fun extractDisplayTitle(url: String, pageTitle: String? = null): String {
        if (!pageTitle.isNullOrBlank() && pageTitle != "about:blank") {
            return pageTitle.trim()
        }
        return try {
            val uri = URI(url)
            val host = uri.host
            if (!host.isNullOrBlank()) {
                host.removePrefix("www.")
            } else {
                url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
            }
        } catch (_: Exception) {
            url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        }
    }
}

