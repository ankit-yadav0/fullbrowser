package com.example.util

/** Pure decision logic for WebView camera/microphone/DRM permission requests. */
object PermissionPolicy {
    // Values of android.webkit.PermissionRequest.RESOURCE_* (kept literal so the logic is JVM-testable).
    const val VIDEO_CAPTURE = "android.webkit.resource.VIDEO_CAPTURE"
    const val AUDIO_CAPTURE = "android.webkit.resource.AUDIO_CAPTURE"
    const val PROTECTED_MEDIA_ID = "android.webkit.resource.PROTECTED_MEDIA_ID"

    private val SUPPORTED = setOf(VIDEO_CAPTURE, AUDIO_CAPTURE, PROTECTED_MEDIA_ID)

    enum class Verdict { PROMPT_USER, DENY_INSECURE_ORIGIN, DENY_UNSUPPORTED_RESOURCE, DENY_BUSY }

    fun evaluate(originUrl: String?, resources: List<String>, requestAlreadyPending: Boolean): Verdict {
        val origin = UrlSafety.parse(originUrl)
        if (origin == null || origin.scheme != "https" || origin.hasUserInfo) return Verdict.DENY_INSECURE_ORIGIN
        if (resources.isEmpty() || !resources.all { it in SUPPORTED }) return Verdict.DENY_UNSUPPORTED_RESOURCE
        if (requestAlreadyPending) return Verdict.DENY_BUSY
        return Verdict.PROMPT_USER
    }

    /**
     * True when the requesting origin's host differs from the top-level page's host, i.e. the request
     * comes from an embedded frame of another site. Exact host comparison only (no public-suffix
     * list), so `www.a.com` embedding `a.com` is conservatively reported as embedded.
     */
    fun isEmbeddedFrameRequest(pageUrl: String?, originUrl: String?): Boolean {
        val page = UrlSafety.parse(pageUrl) ?: return false
        val origin = UrlSafety.parse(originUrl) ?: return false
        return page.host != origin.host
    }

    /** Android runtime permissions that must be held before the request can be granted. */
    fun androidPermissionsFor(resources: List<String>): List<String> = buildList {
        if (VIDEO_CAPTURE in resources) add("android.permission.CAMERA")
        if (AUDIO_CAPTURE in resources) add("android.permission.RECORD_AUDIO")
    }
}

/** Popup (window.open / target=_blank) admission rules. */
object PopupPolicy {
    fun allow(userGesture: Boolean, networkReady: Boolean, popupAlreadyOpen: Boolean): Boolean =
        userGesture && networkReady && !popupAlreadyOpen
}

/** Stops renderer-crash -> reload -> crash loops. */
class CrashLoopGuard(
    private val maxCrashes: Int = 3,
    private val windowMs: Long = 60_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val times = ArrayDeque<Long>()

    /** Records a crash. Returns true if an automatic reload is still acceptable. */
    @Synchronized
    fun recordCrash(): Boolean {
        val now = clock()
        times.addLast(now)
        while (times.isNotEmpty() && now - times.first() > windowMs) times.removeFirst()
        return times.size < maxCrashes
    }

    @Synchronized
    fun reset() = times.clear()
}
