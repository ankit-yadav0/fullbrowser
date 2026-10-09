package com.example.util

import java.net.IDN
import java.net.InetAddress
import java.util.Locale

/**
 * Strictly parsed URL. Deliberately independent of android.net.Uri / java.net.URI so the exact same
 * (single) parser is used for every security decision and so it is unit-testable on the plain JVM.
 */
data class ParsedUrl(
    val scheme: String,
    /** Lower-case ASCII host. IPv6 literals are returned WITHOUT brackets. IDN hosts are punycode. */
    val host: String,
    /** Effective port (default port filled in for http/https, -1 for other schemes without a port). */
    val port: Int,
    val isIpv6Literal: Boolean,
    val hasUserInfo: Boolean
) {
    /** scheme://host[:port] with default ports elided. Safe to compare for same-origin checks. */
    val origin: String
        get() {
            val h = if (isIpv6Literal) "[$host]" else host
            val defaultPort = UrlSafety.defaultPort(scheme)
            return if (port == -1 || port == defaultPort) "$scheme://$h" else "$scheme://$h:$port"
        }
}

object UrlSafety {
    private const val MAX_URL_LENGTH = 8192

    fun defaultPort(scheme: String): Int = when (scheme) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }

    /**
     * Parses `scheme://[userinfo@]host[:port][/...]`. Returns null for anything ambiguous:
     * whitespace/control characters, empty or malformed hosts, bad ports, percent-encoded hosts,
     * hosts that cannot be converted to ASCII.
     * A backslash terminates the authority (WHATWG treats it as '/' for special schemes), so
     * `https://good.com\@evil.com/` is parsed with host `good.com`, matching what Chromium does.
     */
    fun parse(url: String?): ParsedUrl? {
        if (url == null) return null
        val s = url.trim()
        if (s.isEmpty() || s.length > MAX_URL_LENGTH) return null
        if (s.any { it.code <= 0x20 || it.code == 0x7f }) return null

        val schemeEnd = s.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = s.substring(0, schemeEnd).lowercase(Locale.ROOT)
        if (!scheme[0].isLetter() || !scheme.all { it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '-' || it == '.' }) {
            return null
        }

        val rest = s.substring(schemeEnd + 3)
        val authEnd = rest.indexOfAny(charArrayOf('/', '?', '#', '\\'))
        val authority = if (authEnd < 0) rest else rest.substring(0, authEnd)
        if (authority.isEmpty()) return null

        val at = authority.lastIndexOf('@')
        val hasUserInfo = at >= 0
        val hostPort = if (hasUserInfo) authority.substring(at + 1) else authority
        if (hostPort.isEmpty()) return null

        val rawHost: String
        val portText: String
        var ipv6 = false
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) return null
            rawHost = hostPort.substring(1, close)
            val after = hostPort.substring(close + 1)
            portText = when {
                after.isEmpty() -> ""
                after.startsWith(":") -> after.substring(1)
                else -> return null
            }
            ipv6 = true
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon >= 0) {
                rawHost = hostPort.substring(0, colon)
                portText = hostPort.substring(colon + 1)
            } else {
                rawHost = hostPort
                portText = ""
            }
        }

        val port: Int = if (portText.isEmpty()) {
            if (hostPort.endsWith(":") && !ipv6) return null
            defaultPort(scheme)
        } else {
            if (portText.length > 5 || !portText.all { it in '0'..'9' }) return null
            val p = portText.toInt()
            if (p !in 1..65535) return null
            p
        }

        val host: String = if (ipv6) {
            normalizeIpv6(rawHost) ?: return null
        } else {
            normalizeHostName(rawHost) ?: return null
        }
        return ParsedUrl(scheme, host, port, ipv6, hasUserInfo)
    }

    private fun normalizeIpv6(raw: String): String? {
        if (raw.count { it == ':' } < 2) return null
        if (!raw.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }) return null
        return raw.lowercase(Locale.ROOT)
    }

    /** Lower-cases, strips ONE trailing dot, converts IDN to punycode. Returns null if not a valid host. */
    fun normalizeHostName(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null
        var h = raw.lowercase(Locale.ROOT)
        if (h.endsWith(".")) h = h.dropLast(1)
        if (h.isEmpty() || h.length > 253) return null
        if (h.any { it.code > 127 }) {
            h = try {
                IDN.toASCII(h, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT)
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
        if (h.startsWith(".") || h.contains("..")) return null
        if (!h.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '.' || it == '_' }) return null
        return h
    }

    /** Lower-cases a host coming from the WebView (may carry brackets / trailing dot). Null when blank. */
    fun normalizeObservedHost(host: String?): String? {
        if (host.isNullOrBlank()) return null
        val h = host.trim().removePrefix("[").removeSuffix("]")
        return if (h.contains(':')) normalizeIpv6(h) else normalizeHostName(h)
    }

    private val numericLabel = Regex("0x[0-9a-f]*|[0-9]+")

    /**
     * True for loopback / private / link-local / CGNAT / multicast / reserved targets and for local-only
     * names. Numeric hosts that are NOT canonical dotted-quad (e.g. `2130706433`, `0x7f.1`, `127.1`)
     * are treated as local because different parsers resolve them differently.
     * Limitation: a public DNS name that RESOLVES to a private address (DNS rebinding) cannot be seen here.
     */
    fun isPrivateOrLocalHost(host: String?): Boolean {
        val h = normalizeObservedHost(host) ?: return false
        if (h.contains(':')) return isPrivateIpv6(h)
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") ||
            h.endsWith(".internal") || h.endsWith(".localdomain") || h.endsWith(".home.arpa")
        ) return true
        val labels = h.split('.')
        if (labels.all { numericLabel.matches(it) }) {
            val octets = parseDottedQuad(labels) ?: return true
            return isPrivateIpv4(octets)
        }
        return false
    }

    private fun parseDottedQuad(labels: List<String>): IntArray? {
        if (labels.size != 4) return null
        val out = IntArray(4)
        for (i in 0 until 4) {
            val l = labels[i]
            if (l.isEmpty() || l.length > 3 || !l.all { it in '0'..'9' }) return null
            if (l.length > 1 && l[0] == '0') return null // octal-looking
            val v = l.toInt()
            if (v > 255) return null
            out[i] = v
        }
        return out
    }

    private fun isPrivateIpv4(o: IntArray): Boolean {
        val a = o[0]
        val b = o[1]
        return a == 0 || a == 10 || a == 127 || a >= 224 ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 192 && b == 0 && o[2] == 0) ||
            (a == 100 && b in 64..127)
    }

    private fun isPrivateIpv6(h: String): Boolean {
        val addr = try {
            InetAddress.getByName(h) // IPv6 literal only: contains ':' so no DNS lookup is performed
        } catch (_: Exception) {
            return true // unparsable literal: treat as unsafe
        }
        val bytes = addr.address
        if (bytes.size == 4) {
            return isPrivateIpv4(IntArray(4) { bytes[it].toInt() and 0xff }) // IPv4-mapped
        }
        val first = bytes[0].toInt() and 0xff
        val nat64 = bytes[0].toInt() == 0x00 && bytes[1].toInt() == 0x64 &&
            (bytes[2].toInt() and 0xff) == 0xff && (bytes[3].toInt() and 0xff) == 0x9b
        return addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress || addr.isMulticastAddress ||
            (first and 0xfe) == 0xfc ||  // fc00::/7 unique-local
            nat64                        // 64:ff9b::/32 (embeds an IPv4 address)
    }
}
