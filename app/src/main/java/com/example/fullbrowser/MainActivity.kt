package com.example.fullbrowser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"

// Har page me inject hoti hai: (1) background me video/audio na ruke, (2) generic ad boxes hide,
// (3) YouTube ads auto-skip
private const val PAGE_JS = """(function(){if(window.__fb)return;window.__fb=1;var h=location.hostname;
try{if(h.indexOf('whatsapp')<0){
Object.defineProperty(document,'hidden',{get:function(){return false}});
Object.defineProperty(document,'visibilityState',{get:function(){return 'visible'}});
document.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation()},true);
window.addEventListener('blur',function(e){e.stopImmediatePropagation()},true);}}catch(e){}
var s=document.createElement('style');
s.textContent='ins.adsbygoogle,.adsbygoogle,[id^="google_ads"],[id^="div-gpt-ad"],iframe[src*="doubleclick"],iframe[src*="googlesyndication"],[aria-label="Advertisement"]{display:none!important}';
(document.head||document.documentElement).appendChild(s);
if(h.indexOf('youtube.com')>-1){setInterval(function(){
var p=document.querySelector('.html5-video-player'),v=document.querySelector('video');
if(p&&v&&p.classList.contains('ad-showing')&&isFinite(v.duration))v.currentTime=v.duration;
var b=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button');if(b)b.click();
},500);}})();"""

/** Activity background me jaye tab bhi WebView ko "visible" rakho, warna audio/video ruk jata hai. */
class BgWebView(c: Context) : WebView(c) {
    override fun onWindowVisibilityChanged(v: Int) {
        if (v != View.GONE) super.onWindowVisibilityChanged(View.VISIBLE)
    }
}

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private val ws = Regex("\\s+")

    // Chhoti built-in list (internet na ho tab bhi kaam kare). Poori list loadBlocklist() download karta hai.
    private val builtin: Set<String> = """
        doubleclick.net googlesyndication.com googleadservices.com googletagmanager.com googletagservices.com
        google-analytics.com adservice.google.com 2mdn.net facebook.net scorecardresearch.com quantserve.com
        taboola.com outbrain.com adnxs.com criteo.com criteo.net hotjar.com mixpanel.com segment.io popads.net
        popcash.net propellerads.com adsterra.com exoclick.com juicyads.com mgid.com revcontent.com moatads.com
        pubmatic.com rubiconproject.com openx.net smartadserver.com adsrvr.org bluekai.com demdex.net clarity.ms
        mc.yandex.ru ads.twitter.com analytics.tiktok.com amazon-adsystem.com adcolony.com applovin.com inmobi.com
    """.trim().split(ws).toHashSet()

    // In domains par block-list lagu nahi hoti (video / login / captcha na tute)
    private val allow: Set<String> = ("googlevideo.com youtube.com ytimg.com ggpht.com spotify.com scdn.co " +
        "whatsapp.com whatsapp.net recaptcha.net hcaptcha.com cloudflare.com gstatic.com").split(" ").toHashSet()

    // Ye sites apne aap desktop mode me khulti hain (WhatsApp Web / Spotify web player ke liye zaroori)
    private val desktopHosts = listOf("web.whatsapp.com", "open.spotify.com", "web.telegram.org")

    // Search engines (home screen ke button se badalte hain)
    private val engines = listOf(
        "Google" to "https://www.google.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Brave" to "https://search.brave.com/search?q=",
        "Bing" to "https://www.bing.com/search?q="
    )

    @Volatile private var hosts: Set<String> = builtin

    private lateinit var prefs: SharedPreferences
    private lateinit var home: LinearLayout
    private lateinit var browse: FrameLayout
    private lateinit var fab: LinearLayout
    private lateinit var menu: LinearLayout
    private lateinit var list: ListView
    private lateinit var input: EditText
    private lateinit var statTv: TextView
    private lateinit var starBtn: TextView
    private lateinit var modeBtn: TextView
    private lateinit var bar: LinearLayout
    private lateinit var urlBar: EditText
    private var web: WebView? = null
    private var desktop = false
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun bms() = (prefs.getString("bm", "") ?: "").split("\n").filter { it.isNotBlank() }
    private fun saveBms(l: List<String>) = prefs.edit().putString("bm", l.joinToString("\n")).apply()

    // "a.b.c.com" ke liye "a.b.c.com", "b.c.com", "c.com" check karta hai (fast HashSet lookup)
    private fun inSet(s: Set<String>, h: String): Boolean {
        var d = h
        while (true) {
            if (d in s) return true
            val i = d.indexOf('.')
            if (i < 0) return false
            d = d.substring(i + 1)
        }
    }

    private fun engine() = prefs.getInt("eng", 0).coerceIn(0, engines.size - 1)
    private fun blocked(h: String?) = h != null && !inSet(allow, h) && inSet(hosts, h)
    private fun wantDesktop(h: String?) = h != null && desktopHosts.any { h == it || h.endsWith(".$it") }

    private fun ua(desk: Boolean): String {
        val d = WebSettings.getDefaultUserAgent(this)
        val v = Regex("Chrome/([\\d.]+)").find(d)?.groupValues?.get(1) ?: "120.0.0.0"
        return if (desk) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$v Safari/537.36"
        else d.replace("; wv", "").replace("Version/4.0 ", "")
    }

    private fun circle(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC222222.toInt()) }
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
        setOnClickListener { onClick() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("fb", MODE_PRIVATE)
        window.setDecorFitsSystemWindows(false)
        window.attributes = window.attributes.also {
            it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 2)

        // ---------- Home screen ----------
        input = EditText(this).apply {
            hint = "Website ya search likho"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setOnEditorActionListener { _, _, _ -> openSite(text.toString()); true }
        }
        list = ListView(this).apply {
            setOnItemClickListener { _, _, i, _ -> openSite(bms()[i]) }
            setOnItemLongClickListener { _, _, i, _ ->
                saveBms(bms().toMutableList().also { it.removeAt(i) })
                refreshList(); toast("Bookmark hata diya"); true
            }
        }
        statTv = TextView(this).apply {
            text = "Ad/tracker list load ho rahi hai..."; setTextColor(Color.GRAY); textSize = 12f
        }
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true            // focus yahan rahe, keyboard apne aap na khule
            setPadding(dp(16), dp(24), dp(16), dp(16))
            addView(TextView(context).apply { text = "Full Screen Browser"; textSize = 22f; setTextColor(Color.WHITE) })
            addView(statTv)
            addView(input)
            addView(Button(context).apply { text = "Open ▶"; setOnClickListener { openSite(input.text.toString()) } })
            addView(Button(context).apply {
                text = "Search engine: ${engines[engine()].first} ▾"
                setOnClickListener {
                    prefs.edit().putInt("eng", (engine() + 1) % engines.size).apply()
                    text = "Search engine: ${engines[engine()].first} ▾"
                }
            })
            addView(TextView(context).apply {
                text = "Bookmarks (hatane ke liye dabake rakho)"
                setTextColor(Color.LTGRAY); setPadding(0, dp(16), 0, dp(4))
            })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }

        // ---------- Floating quick-access button ----------
        starBtn = circle("☆") { toggleBookmark() }

        // ---------- Address / search bar (full screen ke upar khulta hai) ----------
        urlBar = EditText(this).apply {
            hint = "Search ya website likho"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setOnEditorActionListener { _, _, _ -> go(); true }
        }
        fun tool(t: String, f: () -> Unit) = Button(this).apply {
            text = t; textSize = 12f; setOnClickListener { f() }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        modeBtn = tool("Desktop: OFF") { toggleDesktop(); hideBar() }
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            setBackgroundColor(0xF2111111.toInt()); setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(LinearLayout(context).apply {
                addView(urlBar, LinearLayout.LayoutParams(0, -2, 1f))
                addView(Button(context).apply { text = "Go"; setOnClickListener { go() } })
            })
            addView(LinearLayout(context).apply {
                addView(tool("⟳ Reload") { web?.reload(); hideBar() })
                addView(tool("Forward ▶") { web?.let { if (it.canGoForward()) it.goForward() }; hideBar() })
                addView(modeBtn)
                addView(tool("✕ Close") { hideBar() })
            })
        }

        menu = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; visibility = View.GONE
            addView(circle("◀") {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else toast("Peeche koi page nahi")
            })
            addView(starBtn)
            addView(circle("🔍") { showBar() })
            addView(circle("✕") { closeSite() })
        }
        val main = circle("⚡") {}
        fab = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(menu); addView(main) }

        var dx = 0f; var dy = 0f; var moved = false
        main.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = fab.x - e.rawX; dy = fab.y - e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val nx = e.rawX + dx; val ny = e.rawY + dy
                    if (Math.abs(nx - fab.x) > dp(6) || Math.abs(ny - fab.y) > dp(6)) moved = true
                    if (moved) { fab.x = nx; fab.y = ny; clampFab() }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        menu.visibility = if (menu.visibility == View.GONE) View.VISIBLE else View.GONE
                        fab.post { clampFab() }
                    }
                }
            }
            true
        }

        browse = FrameLayout(this).apply {
            visibility = View.GONE; setBackgroundColor(Color.BLACK)
            addView(fab, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(120); marginEnd = dp(8)
            })
            addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        }

        // Status/nav bar aur keyboard ki jagah automatically chhodta hai (full screen me bars = 0)
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF111111.toInt()); addView(home); addView(browse) }
        root.setOnApplyWindowInsetsListener { v, ins ->
            val s = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            val c = ins.getInsets(WindowInsets.Type.displayCutout())
            v.setPadding(maxOf(s.left, c.left), s.top, maxOf(s.right, c.right), s.bottom)
            WindowInsets.CONSUMED
        }
        setContentView(root)
        home.requestFocus()
        refreshList()
        loadBlocklist()
        handleIntent(intent)
    }

    // ---------- Browser: kisi aur app ka link aaye to yahin full screen me khule ----------
    private fun handleIntent(i: Intent?) {
        val u = i?.dataString
        if (i?.action == Intent.ACTION_VIEW && u != null) openSite(u)
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        handleIntent(i)
    }

    private fun showBar() {
        urlBar.setText(web?.url ?: "")
        urlBar.selectAll()
        bar.visibility = View.VISIBLE
        urlBar.requestFocus()
        urlBar.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(urlBar, 0) }
    }

    private fun hideBar() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(urlBar.windowToken, 0)
        bar.visibility = View.GONE
        web?.requestFocus()
    }

    private fun go() {
        val t = urlBar.text.toString()
        hideBar()
        openSite(t)
    }

    // ---------- Site open / close ----------
    private fun openSite(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            "." in t && " " !in t -> "https://$t"
            else -> engines[engine()].second + Uri.encode(t)
        }
        hideKb()
        val old = web
        if (old != null) {                       // browser pehle se khula hai: usi me nayi site/search load karo
            if (!desktop && wantDesktop(Uri.parse(url).host)) setMode(old, true)
            old.loadUrl(url); old.requestFocus(); return
        }
        val w = newWebView()
        web = w
        setMode(w, wantDesktop(Uri.parse(url).host))
        browse.addView(w, 0, FrameLayout.LayoutParams(-1, -1))
        home.visibility = View.GONE; browse.visibility = View.VISIBLE
        immersive(true)
        try { startForegroundService(Intent(this, PlaybackService::class.java)) } catch (e: Exception) { }
        w.requestFocus()
        w.loadUrl(url)
    }

    private fun closeSite() {
        hideCustom()
        bar.visibility = View.GONE
        web?.let { browse.removeView(it); it.destroy() }   // WebView destroy = RAM free
        web = null
        CookieManager.getInstance().flush()
        stopService(Intent(this, PlaybackService::class.java))
        browse.visibility = View.GONE; home.visibility = View.VISIBLE
        immersive(false); refreshList(); home.requestFocus()
    }

    private fun hideKb() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
    }

    private fun newWebView() = BgWebView(this).apply {
        settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false           // agla video/song apne aap chale
            useWideViewPort = true; loadWithOverviewMode = true
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
        }
        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)   // background me bhi high priority
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true); cm.setAcceptThirdPartyCookies(this, true)

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                if (blocked(r.url.host)) WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null

            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = nav(v, r)

            override fun onPageStarted(v: WebView, url: String, f: Bitmap?) { v.evaluateJavascript(PAGE_JS, null) }

            override fun onPageFinished(v: WebView, url: String) { v.evaluateJavascript(PAGE_JS, null); updateStar() }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(v: View, cb: WebChromeClient.CustomViewCallback) {
                customView = v; customCb = cb
                browse.addView(v, FrameLayout.LayoutParams(-1, -1)); fab.bringToFront()
            }
            override fun onHideCustomView() = hideCustom()

            // Spotify / Netflix jaise DRM (Widevine) players ke liye "protected content" allow
            override fun onPermissionRequest(r: PermissionRequest) {
                val ok = r.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }.toTypedArray()
                if (ok.isNotEmpty()) r.grant(ok) else r.deny()
            }
        }
    }

    // Links / redirects hamesha isi WebView me khulte hain, bahar ke browser ya app me nahi
    private fun nav(v: WebView, r: WebResourceRequest): Boolean {
        val u = r.url
        val h = u.host
        return when (u.scheme) {
            "http", "https" -> when {
                blocked(h) -> true                                    // ad redirect band
                r.isForMainFrame && !desktop && wantDesktop(h) -> {   // WhatsApp / Spotify web => desktop mode
                    setMode(v, true); v.loadUrl(u.toString()); true
                }
                else -> false                                         // baaki sab isi WebView me
            }
            "intent" -> {
                val fb = try {
                    Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
                } catch (e: Exception) { null }
                if (fb != null && fb.startsWith("http")) v.loadUrl(fb)
                true
            }
            "about", "blob", "data", "javascript" -> false
            else -> true                                              // market:// whatsapp:// tg:// app-open band
        }
    }

    private fun setMode(w: WebView, d: Boolean) {
        desktop = d
        w.settings.userAgentString = ua(d)
        modeBtn.text = if (d) "Desktop: ON" else "Desktop: OFF"
    }

    private fun toggleDesktop() {
        val w = web ?: return
        setMode(w, !desktop)
        w.reload()
    }

    private fun hideCustom() {
        val v = customView; val cb = customCb
        customView = null; customCb = null
        if (v != null) browse.removeView(v)
        cb?.onCustomViewHidden()
    }

    private fun clampFab() {
        fab.x = fab.x.coerceIn(0f, maxOf(0f, (browse.width - fab.width).toFloat()))
        fab.y = fab.y.coerceIn(0f, maxOf(0f, (browse.height - fab.height).toFloat()))
    }

    // ---------- Bookmarks ----------
    private fun refreshList() {
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, bms())
    }

    private fun toggleBookmark() {
        val u = web?.url ?: return
        val l = bms().toMutableList()
        if (l.remove(u)) toast("Bookmark hata diya") else { l.add(0, u); toast("Bookmark ho gaya ★") }
        saveBms(l); updateStar()
    }

    private fun updateStar() {
        val u = web?.url
        starBtn.text = if (u != null && bms().contains(u)) "★" else "☆"
    }

    // ---------- Ad/tracker list (StevenBlack hosts), hafte me ek baar update ----------
    private fun loadBlocklist() {
        Thread {
            val f = File(filesDir, "hosts.txt")
            try {
                if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 7 * 86400000L) {
                    val tmp = File(filesDir, "hosts.tmp")
                    val c = URL(HOSTS_URL).openConnection() as HttpURLConnection
                    c.connectTimeout = 10000; c.readTimeout = 30000
                    tmp.bufferedWriter().use { w ->
                        c.inputStream.bufferedReader().forEachLine { l ->
                            val t = l.substringBefore('#').trim().split(ws)
                            if (t.size >= 2 && (t[0] == "0.0.0.0" || t[0] == "127.0.0.1") && '.' in t[1])
                                w.write(t[1].lowercase() + "\n")
                        }
                    }
                    if (tmp.length() > 50000) tmp.renameTo(f) else tmp.delete()
                }
            } catch (e: Exception) { }
            try {
                if (f.exists()) hosts = f.readLines().toHashSet().also { it.addAll(builtin) }
            } catch (e: Exception) { }
            runOnUiThread { statTv.text = "Ad/tracker block list: ${hosts.size} domains" }
        }.start()
    }

    // ---------- Full screen ----------
    private fun immersive(on: Boolean) {
        val c = window.insetsController ?: return
        if (on) {
            c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsets.Type.systemBars())
        } else c.show(WindowInsets.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && browse.visibility == View.VISIBLE) immersive(true)
    }

    // Rotate hone par full screen wapas lagao aur floating button ko screen ke andar rakho
    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        browse.postDelayed({
            if (browse.visibility == View.VISIBLE) immersive(true)
            clampFab()
        }, 300)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()      // login session save
        super.onPause()                          // NOTE: WebView ko pause nahi karte => background play chalta rahe
    }

    override fun onBackPressed() {
        val w = web
        when {
            customView != null -> hideCustom()
            bar.visibility == View.VISIBLE -> hideBar()
            w != null && w.canGoBack() -> w.goBack()
            w != null -> closeSite()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        web?.destroy()
        stopService(Intent(this, PlaybackService::class.java))
        super.onDestroy()
    }
}
