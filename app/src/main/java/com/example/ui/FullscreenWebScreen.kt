package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.BuildConfig
import com.example.model.Bookmark
import com.example.service.BackgroundAudioService
import com.example.ui.components.BookmarksBottomSheet
import com.example.ui.components.QuickControlFab
import com.example.util.AdBlockList
import com.example.util.AdTrackerStats
import com.example.util.BlockedResponses
import com.example.util.CrashLoopGuard
import com.example.util.DownloadPolicy
import com.example.util.DrmDiagnostics
import com.example.util.NavDecision
import com.example.util.NavigationPolicy
import com.example.util.PermissionPolicy
import com.example.util.PopupPolicy
import com.example.util.PrivacyNetworkController
import com.example.util.PrivateDownloader
import com.example.util.UrlSafety
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Document-start hardening. PARTIAL by design (see README): it pins two entropy-bearing numbers to
 * common stable values (instead of `undefined`, which is itself a rare fingerprint) and removes
 * RTCPeerConnection. Geolocation is NOT patched here: it is denied natively (geolocationEnabled=false,
 * onGeolocationPermissionsShowPrompt denies, and no location permission is declared).
 */
private const val PRIVACY_DOCUMENT_START_JS = """
(function(){
  try { if ('hardwareConcurrency' in Navigator.prototype) Object.defineProperty(Navigator.prototype,'hardwareConcurrency',{get:function(){return 4;},configurable:true,enumerable:true}); } catch(e) {}
  try { if ('deviceMemory' in Navigator.prototype) Object.defineProperty(Navigator.prototype,'deviceMemory',{get:function(){return 4;},configurable:true,enumerable:true}); } catch(e) {}
  try { Object.defineProperty(window,'RTCPeerConnection',{value:undefined,configurable:true,writable:false}); } catch(e) {}
  try { Object.defineProperty(window,'webkitRTCPeerConnection',{value:undefined,configurable:true,writable:false}); } catch(e) {}
})();
"""

private fun isMediaStreamingSite(url: String?): Boolean {
    val host = UrlSafety.parse(url)?.host ?: return false
    return host == "youtube.com" || host.endsWith(".youtube.com") ||
        host == "youtu.be" || host == "spotify.com" || host.endsWith(".spotify.com") ||
        host == "soundcloud.com" || host.endsWith(".soundcloud.com")
}

/** WebViews already destroyed (main thread only) so double-destroy is avoided. */
private val destroyedViews: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

/**
 * Detaches callbacks and destroys a WebView. Deliberately does NOT call pauseTimers(): that is a
 * process-global switch and would freeze JavaScript timers of every other WebView (e.g. the main
 * page after a popup is closed or after a renderer restart).
 */
private fun destroyWebViewSafely(web: WebView) {
    if (!destroyedViews.add(web)) return
    runCatching {
        web.stopLoading()
        web.onPause()
        (web.webChromeClient as? BrowserChromeClient)?.onHideCustomView()
        web.webViewClient = WebViewClient()
        web.webChromeClient = null
        (web.parent as? ViewGroup)?.removeView(web)
        web.removeAllViews()
        web.destroy()
    }
}

private data class PendingDownload(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val referer: String?
) {
    val fileName: String
        get() = DownloadPolicy.safeFileName(
            DownloadPolicy.parseContentDisposition(contentDisposition)
                ?: url.substringBefore('#').substringBefore('?').substringAfterLast('/').ifBlank { "download" }
        )
    val host: String get() = UrlSafety.parse(url)?.host.orEmpty()
}

private class BrowserChromeClient(
    private val activity: Activity?,
    private val onProgress: (Float, Boolean) -> Unit,
    private val onTitle: (String) -> Unit,
    private val onCreateWindow: (Boolean, Message?) -> Boolean,
    private val onWindowClosed: (WebView?) -> Unit,
    private val onFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean,
    private val onPermission: (PermissionRequest) -> Unit,
    private val onPermissionCanceled: (PermissionRequest?) -> Unit,
    private val onGeoPermission: (String, GeolocationPermissions.Callback) -> Unit
) : WebChromeClient() {
    private var customView: View? = null
    private var customCallback: CustomViewCallback? = null

    val isCustomViewShowing: Boolean get() = customView != null

    override fun onProgressChanged(view: WebView?, newProgress: Int) = onProgress(newProgress / 100f, newProgress >= 100)

    override fun onReceivedTitle(view: WebView?, title: String?) {
        if (!title.isNullOrBlank()) onTitle(title)
    }

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean =
        onCreateWindow(isUserGesture, resultMsg)

    override fun onCloseWindow(window: WebView?) = onWindowClosed(window)

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view == null || customView != null) {
            callback?.onCustomViewHidden()
            return
        }
        customView = view
        customCallback = callback
        (activity?.window?.decorView as? FrameLayout)?.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        (activity?.window?.decorView as? FrameLayout)?.removeView(view)
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean =
        onFileChooser(callback, params)

    override fun onPermissionRequest(request: PermissionRequest?) { request?.let(onPermission) }

    override fun onPermissionRequestCanceled(request: PermissionRequest?) { onPermissionCanceled(request) }

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
        if (origin != null && callback != null) onGeoPermission(origin, callback)
        else callback?.invoke(origin.orEmpty(), false, false)
    }

    override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
        // Never persist or print page-controlled console data in release builds.
        if (!BuildConfig.DEBUG) return true
        if (message != null) {
            val safe = DrmDiagnostics.sanitizeLog(message.message() ?: "")
            android.util.Log.d("WebViewConsole", "${message.messageLevel()}: $safe")
        }
        return true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullscreenWebScreen(
    initialUrl: String,
    bookmarks: List<Bookmark>,
    onExitToHome: () -> Unit,
    onAddBookmark: (url: String, title: String?) -> Unit,
    onDeleteBookmark: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val privacyNetwork = remember(context) { PrivacyNetworkController(context) }
    val crashGuard = remember { CrashLoopGuard() }
    val hardeningWarned = remember { BooleanArray(1) }

    val startUrl = remember(initialUrl) { NavigationPolicy.toLoadableUrl(initialUrl) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var popupUrl by remember { mutableStateOf("") }
    var chromeRef by remember { mutableStateOf<BrowserChromeClient?>(null) }
    var currentUrl by rememberSaveable { mutableStateOf(startUrl.orEmpty()) }
    var currentTitle by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var fabExpanded by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }
    var shieldEnabled by rememberSaveable { mutableStateOf(true) }
    val shieldState = rememberUpdatedState(shieldEnabled)
    val stats by AdTrackerStats.snapshot.collectAsStateWithLifecycle()
    var vpnActive by remember { mutableStateOf(false) }
    var networkReady by remember { mutableStateOf(false) }
    var rendererGeneration by rememberSaveable { mutableStateOf(0) }

    var uploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingPermission by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingOrigin by remember { mutableStateOf("") }
    var pendingPageUrl by remember { mutableStateOf("") }
    var pendingResources by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<PendingDownload?>(null) }

    var backgroundAudio by remember { mutableStateOf(false) }
    var serviceFailed by remember { mutableStateOf(false) }
    var webMediaPlaying by remember { mutableStateOf(false) }
    val serviceActive by BackgroundAudioService.isServiceActive.collectAsStateWithLifecycle()
    val audioEnabled = backgroundAudio && !serviceFailed
    val shouldRunAudio = backgroundAudio && webMediaPlaying && isMediaStreamingSite(currentUrl)
    val keepWebViewRunningInBackground = rememberUpdatedState(shouldRunAudio)

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) backgroundAudio = false
    }

    /** Resolves the single pending website permission request exactly once. */
    fun finishPermission(grant: Boolean) {
        val request = pendingPermission
        val resources = pendingResources
        pendingPermission = null
        pendingOrigin = ""
        pendingPageUrl = ""
        pendingResources = emptyList()
        showPermissionDialog = false
        if (request != null) {
            runCatching { if (grant) request.grant(resources.toTypedArray()) else request.deny() }
        }
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        uploadCallback?.onReceiveValue(uris)
        uploadCallback = null
    }

    val webPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (pendingPermission != null) {
            val needed = PermissionPolicy.androidPermissionsFor(pendingResources)
            val ok = needed.all {
                grants[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            finishPermission(ok)
        }
    }

    fun handleFileChooser(callback: ValueCallback<Array<Uri>>?, params: WebChromeClient.FileChooserParams?): Boolean {
        uploadCallback?.onReceiveValue(null)
        uploadCallback = callback
        return runCatching {
            fileChooserLauncher.launch(
                params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            )
            true
        }.getOrElse {
            uploadCallback?.onReceiveValue(null)
            uploadCallback = null
            false
        }
    }

    fun handlePermissionRequest(request: PermissionRequest, fromPopup: Boolean) {
        val verdict = PermissionPolicy.evaluate(
            originUrl = request.origin?.toString(),
            resources = request.resources?.toList().orEmpty(),
            requestAlreadyPending = pendingPermission != null
        )
        if (verdict != PermissionPolicy.Verdict.PROMPT_USER) {
            runCatching { request.deny() }
            return
        }
        // 1) Ask the user about THIS origin and THIS request first. The Android runtime prompt
        //    (if needed) only follows the user's consent, and grant() targets exactly this request.
        pendingPermission = request
        pendingOrigin = request.origin.toString()
        pendingPageUrl = if (fromPopup) popupUrl else currentUrl
        pendingResources = request.resources.toList()
        showPermissionDialog = true
    }

    fun onDownloadRequested(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (!privacyNetwork.isReady()) {
            Toast.makeText(context, "VPN required before downloading", Toast.LENGTH_SHORT).show()
            return
        }
        if (!url.startsWith("https://", ignoreCase = true)) {
            Toast.makeText(context, "Only HTTPS downloads are allowed", Toast.LENGTH_SHORT).show()
            return
        }
        if (pendingDownload != null) return // one confirmation at a time; extra triggers are dropped
        pendingDownload = PendingDownload(url, userAgent, contentDisposition, mimeType, currentUrl)
    }

    fun createDownloadListener(): DownloadListener =
        DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            onDownloadRequested(url, userAgent, contentDisposition, mimeType)
        }

    fun launchExternal(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addCategory(Intent.CATEGORY_BROWSABLE) })
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to open this link", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun closePopup() {
        val popup = popupWebView ?: return
        popupWebView = null
        popupUrl = ""
        destroyWebViewSafely(popup)
    }

    /** Immediately destroys every WebView and drops every outstanding callback. */
    fun tearDownBrowser() {
        finishPermission(false)
        pendingDownload = null
        uploadCallback?.onReceiveValue(null)
        uploadCallback = null
        chromeRef?.onHideCustomView()
        closePopup()
        webViewRef?.let { destroyWebViewSafely(it) }
        webViewRef = null
        chromeRef = null
        loading = false
        progress = 0f
    }

    fun configureWebView(webView: WebView, popup: Boolean = false) {
        val pageHost = AtomicReference<String?>(null)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Native layer also refuses script-opened windows without a user gesture
            // (onCreateWindow re-checks the gesture via PopupPolicy).
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(this, true)
            }
            if (BuildConfig.DEBUG) DrmDiagnostics.runDiagnostics(context, userAgentString)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, PRIVACY_DOCUMENT_START_JS, setOf("*"))
        } else if (!hardeningWarned[0]) {
            hardeningWarned[0] = true
            Toast.makeText(
                context,
                "This WebView version cannot inject the privacy script: WebRTC hardening is inactive",
                Toast.LENGTH_LONG
            ).show()
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageHost.set(UrlSafety.parse(url)?.host)
                if (popup) {
                    if (NavigationPolicy.isWebUrl(url)) popupUrl = url.orEmpty()
                } else {
                    loading = true
                    if (NavigationPolicy.isWebUrl(url)) currentUrl = url.orEmpty()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (popup) return
                loading = false
                if (NavigationPolicy.isWebUrl(url)) currentUrl = url.orEmpty()
                view?.title?.let { currentTitle = it }
                runCatching { CookieManager.getInstance().flush() }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                pageHost.set(UrlSafety.parse(url)?.host ?: pageHost.get())
                if (popup) {
                    if (NavigationPolicy.isWebUrl(url)) popupUrl = url.orEmpty()
                } else if (NavigationPolicy.isWebUrl(url)) {
                    currentUrl = url.orEmpty() // follows history.pushState/replaceState
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                if (popup) {
                    closePopup()
                    Toast.makeText(context, "Popup renderer closed", Toast.LENGTH_SHORT).show()
                } else {
                    finishPermission(false)
                    uploadCallback?.onReceiveValue(null)
                    uploadCallback = null
                    if (crashGuard.recordCrash()) {
                        rendererGeneration++
                        Toast.makeText(context, "Web page renderer restarted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "This page keeps crashing and was closed", Toast.LENGTH_LONG).show()
                        onExitToHome()
                    }
                }
                return true
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (!popup && request?.isForMainFrame == true) loading = false
            }

            // Never override: an invalid certificate must always end the load.
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request == null) return true
                if (!request.isForMainFrame) return false
                if (!privacyNetwork.isReady()) return true
                return when (val decision = NavigationPolicy.decideMainFrame(request.url.toString(), request.hasGesture())) {
                    // Script- or redirect-initiated top-level navigation from a public page to a loopback/private
                    // target is refused (LAN/localhost probing). Typed addresses and user-gesture navigations
                    // are allowed; DNS-rebinding names are not detectable here.
                    is NavDecision.Allow ->
                        !request.hasGesture() && NavigationPolicy.isPrivateNetworkProbe(request.url.host, pageHost.get())
                    is NavDecision.Block -> {
                        if (decision.reason == "insecure http") {
                            Toast.makeText(context, "Insecure HTTP navigation blocked", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    is NavDecision.External -> {
                        launchExternal(decision.url)
                        true
                    }
                    is NavDecision.LoadInWebView -> {
                        view?.loadUrl(decision.url)
                        true
                    }
                }
            }

            @Suppress("DEPRECATION")
            @Deprecated("Legacy callback (not used on API 24+); applies the gesture-less policy.")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                NavigationPolicy.decideMainFrame(url, hasGesture = false) !is NavDecision.Allow

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request == null) return null
                // Closed VPN gate: nothing may reach the network stack (runs on a WebView IO thread).
                if (!privacyNetwork.isReady()) return BlockedResponses.refused()
                if (!request.isForMainFrame) {
                    val host = request.url.host
                    if (shieldState.value && AdBlockList.isBlockedHost(host)) {
                        AdTrackerStats.recordBlocked(host)
                        return BlockedResponses.adTracker()
                    }
                    if (NavigationPolicy.isPrivateNetworkProbe(host, pageHost.get())) {
                        return BlockedResponses.refused()
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.webChromeClient = BrowserChromeClient(
            activity = activity,
            onProgress = { p, done -> if (!popup) { progress = p; if (done) loading = false } },
            onTitle = { if (!popup) currentTitle = it },
            onCreateWindow = { userGesture, resultMsg ->
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                if (popup || resultMsg == null || transport == null ||
                    !PopupPolicy.allow(userGesture, privacyNetwork.isReady(), popupWebView != null)
                ) {
                    false
                } else {
                    val child = WebView(context)
                    configureWebView(child, popup = true)
                    child.setDownloadListener(createDownloadListener())
                    popupUrl = ""
                    popupWebView = child
                    transport.webView = child
                    resultMsg.sendToTarget()
                    true
                }
            },
            onWindowClosed = { closePopup() },
            onFileChooser = { callback, params -> handleFileChooser(callback, params) },
            onPermission = { request -> handlePermissionRequest(request, popup) },
            onPermissionCanceled = { request -> if (request != null && pendingPermission === request) finishPermission(false) },
            onGeoPermission = { origin, callback -> callback.invoke(origin, false, false) }
        )
        webView.setDownloadListener(createDownloadListener())
    }

    DisposableEffect(context, privacyNetwork) {
        privacyNetwork.start { ready ->
            vpnActive = ready
            networkReady = ready
            if (!ready) tearDownBrowser() // synchronous teardown on the main thread, no recomposition lag
        }
        onDispose { privacyNetwork.stop() }
    }

    DisposableEffect(Unit) {
        AdTrackerStats.reset()
        val controller = ServiceWorkerController.getInstance()
        controller.serviceWorkerWebSettings.apply {
            allowContentAccess = false
            allowFileAccess = false
        }
        controller.setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                if (!privacyNetwork.isReady()) return BlockedResponses.refused()
                val host = request.url.host
                if (shieldState.value && AdBlockList.isBlockedHost(host)) {
                    AdTrackerStats.recordBlocked(host)
                    return BlockedResponses.adTracker()
                }
                // A Service Worker has no page host to compare with: private targets are always refused.
                if (UrlSafety.isPrivateOrLocalHost(host)) return BlockedResponses.refused()
                return null
            }
        })
        onDispose { ServiceWorkerController.getInstance().setServiceWorkerClient(null) }
    }

    LaunchedEffect(startUrl) {
        if (startUrl == null && currentUrl.isBlank()) {
            Toast.makeText(context, "Only http(s) addresses can be opened", Toast.LENGTH_SHORT).show()
            onExitToHome()
        }
    }

    // Pause the page when the app is not visible, unless the user opted into background media AND
    // media is actually playing. Uses onPause()/onResume() only (pauseTimers() is process-global).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (!keepWebViewRunningInBackground.value) {
                    webViewRef?.onPause()
                    popupWebView?.onPause()
                }
            } else if (event == Lifecycle.Event.ON_START) {
                webViewRef?.onResume()
                popupWebView?.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(backgroundAudio, currentUrl, webViewRef) {
        while (backgroundAudio && isMediaStreamingSite(currentUrl)) {
            webViewRef?.evaluateJavascript("(function(){try{return [].some.call(document.querySelectorAll('audio,video'),function(x){return !x.paused&&!x.ended&&x.readyState>2;});}catch(e){return false;}})();") { result ->
                webMediaPlaying = result == "true"
            }
            delay(2000)
        }
        webMediaPlaying = false
    }

    LaunchedEffect(shouldRunAudio, serviceActive) {
        if (shouldRunAudio) {
            if (!serviceActive) BackgroundAudioService.start(context, null, null)
        } else if (serviceActive) {
            BackgroundAudioService.stop(context)
        }
    }
    LaunchedEffect(shouldRunAudio, serviceActive) {
        serviceFailed = false
        if (shouldRunAudio && !serviceActive) {
            delay(2000)
            if (!serviceActive) serviceFailed = true
        }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        onDispose {
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { runCatching { it.clearHistory(); it.clearCache(true) } }
            tearDownBrowser()
            if (BackgroundAudioService.isServiceActive.value) BackgroundAudioService.stop(context)
        }
    }

    BackHandler {
        when {
            popupWebView != null -> {
                val popup = popupWebView
                if (popup?.canGoBack() == true) popup.goBack() else closePopup()
            }
            chromeRef?.isCustomViewShowing == true -> chromeRef?.onHideCustomView()
            bookmarksOpen -> bookmarksOpen = false
            fabExpanded -> fabExpanded = false
            webViewRef?.canGoBack() == true -> webViewRef?.goBack()
            else -> onExitToHome()
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black).testTag("fullscreen_webview_container")
    ) {
        if (networkReady) {
            androidx.compose.runtime.key(rendererGeneration) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            configureWebView(this)
                            webViewRef = this
                            chromeRef = webChromeClient as? BrowserChromeClient
                            resumeTimers()
                            val target = NavigationPolicy.toLoadableUrl(currentUrl.ifBlank { initialUrl })
                            loadUrl(target ?: "about:blank")
                        }
                    },
                    onRelease = { web ->
                        destroyWebViewSafely(web)
                        if (webViewRef === web) webViewRef = null
                    }
                )
            }
        }

        if (loading && progress < 1f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp), color = Color(0xFF3B82F6))
        }

        if (!networkReady) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black).clickable { },
                contentAlignment = Alignment.Center
            ) {
                Text("Secure VPN connection required", color = Color.White)
            }
        }

        QuickControlFab(
            isExpanded = fabExpanded,
            onToggleExpand = { fabExpanded = !fabExpanded },
            onCollapse = { fabExpanded = false },
            onExitClick = { fabExpanded = false; onExitToHome() },
            onBookmarksClick = { fabExpanded = false; bookmarksOpen = true },
            adTrackerBlockingEnabled = shieldEnabled,
            privacyNetworkReady = networkReady,
            vpnActive = vpnActive,
            blockedRequestCount = stats.totalBlocked,
            onToggleAdTrackerBlocking = { shieldEnabled = !shieldEnabled },
            isBackgroundAudioEnabled = audioEnabled,
            onToggleBackgroundAudio = {
                if (backgroundAudio) backgroundAudio = false
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else backgroundAudio = true
            }
        )

        if (bookmarksOpen) {
            BookmarksBottomSheet(
                bookmarks = bookmarks,
                currentUrl = currentUrl,
                onAddCurrentUrl = {
                    if (NavigationPolicy.isWebUrl(currentUrl)) runCatching { onAddBookmark(currentUrl, currentTitle) }
                },
                onSelectBookmark = { url ->
                    bookmarksOpen = false
                    val target = NavigationPolicy.toLoadableUrl(url)
                    if (target == null) {
                        Toast.makeText(context, "Bookmark address is not allowed", Toast.LENGTH_SHORT).show()
                    } else {
                        currentUrl = target
                        if (privacyNetwork.isReady()) webViewRef?.loadUrl(target)
                    }
                },
                onDeleteBookmark = onDeleteBookmark,
                onDismiss = { bookmarksOpen = false }
            )
        }

        popupWebView?.let { popup ->
            Dialog(
                onDismissRequest = { closePopup() },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    securePolicy = SecureFlagPolicy.SecureOn
                )
            ) {
                Column(modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The popup has no address bar, so always show which host is actually loaded.
                        Text(
                            text = "Popup: " + (UrlSafety.parse(popupUrl)?.host ?: "loading…"),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { closePopup() }) { Text("Close") }
                    }
                    AndroidView(
                        factory = { (popup.parent as? ViewGroup)?.removeView(popup); popup },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }

        if (showPermissionDialog) {
            val kinds = buildList {
                if (PermissionPolicy.VIDEO_CAPTURE in pendingResources) add("camera")
                if (PermissionPolicy.AUDIO_CAPTURE in pendingResources) add("microphone")
                if (PermissionPolicy.PROTECTED_MEDIA_ID in pendingResources) add("protected media")
            }.joinToString(", ")
            AlertDialog(
                onDismissRequest = { finishPermission(false) },
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
                title = { Text("Website permission request") },
                text = {
                    val pageHost = UrlSafety.parse(pendingPageUrl)?.host
                    val embedded = PermissionPolicy.isEmbeddedFrameRequest(pendingPageUrl, pendingOrigin)
                    Text(
                        buildString {
                            append("$pendingOrigin wants access to $kinds.")
                            if (pageHost != null) append("\nYou are viewing: $pageHost")
                            if (embedded) append("\nThis request comes from an embedded frame of a DIFFERENT site.")
                            append("\nAllow only if you trust it.")
                        }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showPermissionDialog = false
                        val missing = PermissionPolicy.androidPermissionsFor(pendingResources).filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) finishPermission(true)
                        else webPermissionLauncher.launch(missing.toTypedArray())
                    }) { Text("Allow") }
                },
                dismissButton = { Button(onClick = { finishPermission(false) }) { Text("Deny") } }
            )
        }

        pendingDownload?.let { download ->
            AlertDialog(
                onDismissRequest = { pendingDownload = null },
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
                title = { Text("Download file?") },
                text = { Text("${download.fileName}\nfrom ${download.host}") },
                confirmButton = {
                    Button(onClick = {
                        pendingDownload = null
                        scope.launch {
                            val result = PrivateDownloader.download(
                                context, download.url, download.userAgent, download.contentDisposition,
                                download.mimeType, download.referer, privacyNetwork::isReady
                            )
                            result.onSuccess { Toast.makeText(context, "Download completed", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show() }
                        }
                    }) { Text("Download") }
                },
                dismissButton = { Button(onClick = { pendingDownload = null }) { Text("Cancel") } }
            )
        }
    }
}
