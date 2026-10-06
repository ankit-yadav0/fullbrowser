package com.example.fullbrowser

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import java.io.ByteArrayInputStream

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    // Ye domains (aur unke subdomains) load hi nahi honge. Apne domain yahan add kar sakte ho.
    private val blockList = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagmanager.com",
        "googletagservices.com", "google-analytics.com", "adservice.google.com", "2mdn.net",
        "facebook.net", "scorecardresearch.com", "quantserve.com", "taboola.com", "outbrain.com",
        "adnxs.com", "criteo.com", "criteo.net", "hotjar.com", "mixpanel.com", "segment.io",
        "popads.net", "popcash.net", "propellerads.com", "adsterra.com", "exoclick.com",
        "juicyads.com", "mgid.com", "revcontent.com", "moatads.com", "pubmatic.com",
        "rubiconproject.com", "openx.net", "smartadserver.com", "adsrvr.org", "bluekai.com",
        "demdex.net", "clarity.ms", "mc.yandex.ru", "ads.twitter.com", "analytics.tiktok.com",
        "amazon-adsystem.com", "adcolony.com", "applovin.com", "inmobi.com"
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var home: LinearLayout
    private lateinit var browse: FrameLayout
    private lateinit var fab: LinearLayout
    private lateinit var menu: LinearLayout
    private lateinit var list: ListView
    private lateinit var input: EditText
    private lateinit var starBtn: TextView
    private lateinit var musicBtn: TextView
    private var web: WebView? = null
    private var player: MediaPlayer? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun bms() = (prefs.getString("bm", "") ?: "").split("\n").filter { it.isNotBlank() }
    private fun saveBms(l: List<String>) = prefs.edit().putString("bm", l.joinToString("\n")).apply()
    private fun blocked(h: String?) = h != null && blockList.any { h == it || h.endsWith(".$it") }

    private fun circle(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC222222.toInt()) }
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("fb", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.also {
            it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

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
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111111.toInt()); setPadding(dp(16), dp(24), dp(16), dp(16))
            addView(TextView(context).apply { text = "Full Screen Browser"; textSize = 22f; setTextColor(Color.WHITE) })
            addView(input)
            addView(Button(context).apply { text = "Open ▶"; setOnClickListener { openSite(input.text.toString()) } })
            addView(Button(context).apply { text = "♪ Background music chuno"; setOnClickListener { pickMusic() } })
            addView(TextView(context).apply {
                text = "Bookmarks (hatane ke liye dabake rakho)"
                setTextColor(Color.LTGRAY); setPadding(0, dp(16), 0, dp(4))
            })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }

        // ---------- Floating quick-access button ----------
        musicBtn = circle("♪") { setMusic(player?.isPlaying != true) }
        starBtn = circle("☆") { toggleBookmark() }
        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            addView(circle("◀") {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else toast("Peeche koi page nahi")
            })
            addView(starBtn); addView(musicBtn)
            addView(circle("✕") { closeSite() })
        }
        val main = circle("⚡") {}
        fab = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(main); addView(menu) }

        var dx = 0f; var dy = 0f; var moved = false
        main.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = fab.x - e.rawX; dy = fab.y - e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val nx = e.rawX + dx; val ny = e.rawY + dy
                    if (Math.abs(nx - fab.x) > dp(6) || Math.abs(ny - fab.y) > dp(6)) moved = true
                    if (moved) {
                        fab.x = nx.coerceIn(0f, (browse.width - fab.width).toFloat())
                        fab.y = ny.coerceIn(0f, (browse.height - fab.height).toFloat())
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) menu.visibility = if (menu.visibility == View.GONE) View.VISIBLE else View.GONE
                }
            }
            true
        }

        browse = FrameLayout(this).apply {
            visibility = View.GONE; setBackgroundColor(Color.BLACK)
            addView(fab, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(120); marginEnd = dp(8)
            })
        }
        setContentView(FrameLayout(this).apply { addView(home); addView(browse) })
        refreshList()
    }

    // ---------- Site open / close ----------
    private fun openSite(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            "." in t && " " !in t -> "https://$t"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(t)
        }
        val w = newWebView()
        web = w
        browse.addView(w, 0, FrameLayout.LayoutParams(-1, -1))
        home.visibility = View.GONE; browse.visibility = View.VISIBLE
        immersive(true)
        if (prefs.contains("music")) setMusic(true)
        w.loadUrl(url)
    }

    private fun closeSite() {
        hideCustom()
        web?.let { browse.removeView(it); it.destroy() }   // WebView destroy = RAM free
        web = null
        setMusic(false)
        browse.visibility = View.GONE; home.visibility = View.VISIBLE
        immersive(false); refreshList()
    }

    private fun newWebView() = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setSupportMultipleWindows(false)                 // popup windows band
        settings.javaScriptCanOpenWindowsAutomatically = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)   // tracker cookies band
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                if (blocked(r.url.host)) WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null

            // intent:// market:// jaise ad-redirects band, sirf http/https allowed
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean =
                !(r.url.scheme == "http" || r.url.scheme == "https")

            override fun onPageFinished(v: WebView, url: String) = updateStar()
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(v: View, cb: WebChromeClient.CustomViewCallback) {
                customView = v; customCb = cb
                browse.addView(v, FrameLayout.LayoutParams(-1, -1)); fab.bringToFront()
            }
            override fun onHideCustomView() = hideCustom()
        }
    }

    private fun hideCustom() {
        val v = customView; val cb = customCb
        customView = null; customCb = null
        if (v != null) browse.removeView(v)
        cb?.onCustomViewHidden()
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

    // ---------- Background music (apni audio file) ----------
    private fun setMusic(on: Boolean) {
        if (on) {
            val u = prefs.getString("music", null)?.let { Uri.parse(it) } ?: return pickMusic()
            try {
                if (player == null) player = MediaPlayer().apply {
                    setDataSource(this@MainActivity, u); isLooping = true; prepare()
                }
                player?.start()
            } catch (e: Exception) { player = null; toast("Music file nahi chal rahi") }
        } else player?.pause()
        musicBtn.text = if (player?.isPlaying == true) "♪" else "🔇"
    }

    private fun pickMusic() = startActivityForResult(
        Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE), 1
    )

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val u = data?.data
        if (req == 1 && res == RESULT_OK && u != null) {
            try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            prefs.edit().putString("music", u.toString()).apply()
            player?.release(); player = null
            toast("Music set ho gaya ♪")
            if (browse.visibility == View.VISIBLE) setMusic(true)
        }
    }

    // ---------- Full screen ----------
    private fun immersive(on: Boolean) {
        window.decorView.systemUiVisibility = if (on) (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        ) else 0
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && browse.visibility == View.VISIBLE) immersive(true)
    }

    override fun onBackPressed() {
        val w = web
        when {
            customView != null -> hideCustom()
            w != null && w.canGoBack() -> w.goBack()
            w != null -> closeSite()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        player?.release(); web?.destroy()
        super.onDestroy()
    }
}
