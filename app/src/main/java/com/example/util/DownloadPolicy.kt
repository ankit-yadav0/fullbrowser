package com.example.util

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Pure validation / sanitisation rules for in-app downloads (JVM-testable). */
object DownloadPolicy {
    const val MAX_REDIRECTS = 5

    /**
     * Validates one hop of a download. https only, no embedded credentials, and never a loopback /
     * private / link-local target (blocks page-triggered requests to the user's LAN, including via redirects).
     * @throws IllegalArgumentException with a short reason.
     */
    fun validateHop(url: String): ParsedUrl {
        val p = UrlSafety.parse(url) ?: throw IllegalArgumentException("Malformed download URL")
        require(p.scheme == "https") { "Only HTTPS downloads are allowed" }
        require(!p.hasUserInfo) { "Credentials in download URL are not allowed" }
        require(!UrlSafety.isPrivateOrLocalHost(p.host)) { "Private network target blocked" }
        return p
    }

    /** Resolves a Location header against [current] and validates the result. Never downgrades to http. */
    fun nextHop(current: String, location: String?): String {
        require(!location.isNullOrBlank()) { "Redirect without Location" }
        val resolved = try {
            URI(current).resolve(URI(location.trim())).toString()
        } catch (_: Exception) {
            throw IllegalArgumentException("Malformed redirect")
        }
        validateHop(resolved)
        return resolved
    }

    /** Cookies and Referer may only accompany requests to the SAME origin as the original download URL. */
    fun mayAttachCredentials(originalUrl: String, hopUrl: String): Boolean {
        val a = UrlSafety.parse(originalUrl) ?: return false
        val b = UrlSafety.parse(hopUrl) ?: return false
        return a.origin == b.origin
    }

    /** Referer is only sent when it is an https URL of the same origin as the hop. */
    fun refererFor(referer: String?, hopUrl: String): String? {
        val r = UrlSafety.parse(referer) ?: return null
        val h = UrlSafety.parse(hopUrl) ?: return null
        if (r.scheme != "https" || r.hasUserInfo || r.origin != h.origin) return null
        return referer?.trim()
    }

    private val unsafeNameChars = Regex("[\\u0000-\\u001F\\u007F\\\\/:*?\"<>|]")
    private val bidiControls = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")

    fun safeFileName(value: String?): String {
        var clean = (value ?: "").replace(unsafeNameChars, "_").replace(bidiControls, "_").trim()
        clean = clean.trim('.', ' ')
        if (clean.isBlank() || clean.all { it == '_' }) return "download"
        return if (clean.length <= 180) clean else clean.take(180)
    }

    fun parseContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(header)
            ?.groupValues?.getOrNull(1)?.trim()?.let { enc ->
                runCatching { URLDecoder.decode(enc.replace("+", "%2B"), "UTF-8") }.getOrNull()?.let { return it }
            }
        return Regex("filename\\s*=\\s*\"?([^;\"]+)", RegexOption.IGNORE_CASE).find(header)
            ?.groupValues?.getOrNull(1)?.trim()
    }

    /** Strips parameters (";charset=...") and validates the type/subtype shape. Falls back to octet-stream. */
    fun cleanMimeType(vararg candidates: String?): String {
        for (c in candidates) {
            val base = c?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: continue
            if (Regex("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+").matches(base)) return base
        }
        return "application/octet-stream"
    }
}
