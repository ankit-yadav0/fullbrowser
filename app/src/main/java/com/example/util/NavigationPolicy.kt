package com.example.util

import java.net.URLDecoder
import java.util.Locale

/** Result of evaluating a navigation. Pure data so it can be unit-tested without Android. */
sealed interface NavDecision {
    /** Let the WebView load it. */
    data object Allow : NavDecision

    /** Refuse the navigation. [reason] is a short, non-sensitive explanation. */
    data class Block(val reason: String) : NavDecision

    /** Hand a safe, allow-listed scheme (mailto/tel/sms) to the OS. Only returned with a user gesture. */
    data class External(val url: String) : NavDecision

    /** An `intent://` URL carried an https fallback; load THAT in the WebView instead of launching apps. */
    data class LoadInWebView(val url: String) : NavDecision
}

object NavigationPolicy {
    /** Schemes that may be passed to another app, and only on a user gesture. */
    val EXTERNAL_SCHEMES: Set<String> = setOf("mailto", "tel", "sms", "smsto")

    fun isWebUrl(url: String?): Boolean {
        val p = UrlSafety.parse(url) ?: return false
        return p.scheme == "https" || p.scheme == "http"
    }

    /**
     * URL that may be loaded by the app itself (initial load, bookmark, restored state):
     * https stays https, http is upgraded to https, everything else (and any URL with embedded
     * credentials) is rejected.
     */
    fun toLoadableUrl(url: String?): String? {
        val trimmed = url?.trim() ?: return null
        val p = UrlSafety.parse(trimmed) ?: return null
        if (p.hasUserInfo) return null
        return when (p.scheme) {
            "https" -> trimmed
            "http" -> "https" + trimmed.substring(4) // "http" -> "https", keeps "://..." untouched
            else -> null
        }
    }

    /** Decision for a MAIN-FRAME navigation reported by shouldOverrideUrlLoading (incl. server redirects). */
    fun decideMainFrame(url: String?, hasGesture: Boolean): NavDecision {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return NavDecision.Block("empty url")
        val lower = trimmed.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("https://") -> {
                val p = UrlSafety.parse(trimmed)
                when {
                    p == null -> NavDecision.Block("malformed url")
                    p.hasUserInfo -> NavDecision.Block("credentials in url")
                    else -> NavDecision.Allow
                }
            }
            lower.startsWith("http://") -> NavDecision.Block("insecure http")
            lower.startsWith("blob:https://") -> NavDecision.Allow
            lower == "about:blank" -> NavDecision.Allow
            lower.startsWith("intent:") -> decideIntent(trimmed, hasGesture)
            else -> {
                val scheme = lower.substringBefore(':', "")
                when {
                    scheme !in EXTERNAL_SCHEMES -> NavDecision.Block("scheme not allowed")
                    scheme == "mailto" && mailtoHasAttachmentParam(trimmed) -> NavDecision.Block("mailto attachment parameter")
                    !hasGesture -> NavDecision.Block("external scheme without user gesture")
                    else -> NavDecision.External(trimmed)
                }
            }
        }
    }

    /** Some mail clients honour `attach=`/`attachment=` and would attach a local file to the draft. */
    private fun mailtoHasAttachmentParam(url: String): Boolean {
        val query = url.substringAfter('?', "").substringBefore('#')
        return query.split('&').any {
            val key = it.substringBefore('=').trim().lowercase(Locale.ROOT)
            key == "attach" || key == "attachment" || key == "attachments"
        }
    }

    private fun decideIntent(url: String, hasGesture: Boolean): NavDecision {
        if (!hasGesture) return NavDecision.Block("intent without user gesture")
        val fallback = extractIntentFallback(url) ?: return NavDecision.Block("intent without https fallback")
        val loadable = toLoadableUrl(fallback)
        return if (loadable != null && loadable.startsWith("https://", ignoreCase = true)) {
            NavDecision.LoadInWebView(loadable)
        } else {
            NavDecision.Block("intent fallback not https")
        }
    }

    /** Reads S.browser_fallback_url from an intent: URI WITHOUT constructing an Intent. */
    fun extractIntentFallback(url: String): String? {
        val marker = "#Intent;"
        val idx = url.indexOf(marker, ignoreCase = true)
        if (idx < 0) return null
        val parts = url.substring(idx + marker.length).split(';')
        val key = "S.browser_fallback_url="
        val entry = parts.firstOrNull { it.startsWith(key) } ?: return null
        val raw = entry.substring(key.length)
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * Sub-resource / sub-frame request to a loopback or private address from a page that is NOT itself
     * private. Blocks LAN/localhost probing (including the "localhost tracking" technique).
     */
    fun isPrivateNetworkProbe(requestHost: String?, pageHost: String?): Boolean {
        if (!UrlSafety.isPrivateOrLocalHost(requestHost)) return false
        return !UrlSafety.isPrivateOrLocalHost(pageHost)
    }
}
