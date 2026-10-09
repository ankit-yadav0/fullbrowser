package com.example.fullbrowser

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

// Har page/frame me document-start par chalti hai:
// (1) background play: page ko hamesha "visible" dikhao  (2) scriptlets (uBO)  (3) fingerprint protection
// (4) cosmetic filtering (ad boxes hide)  (5) YouTube ad auto-skip
private const val DOC_JS = """(function(){
if(window.__fb)return;window.__fb=1;
var H=location.hostname,ST=window.setTimeout,SI=window.setInterval;
try{if(H.indexOf('whatsapp')<0){
Object.defineProperty(document,'hidden',{get:function(){return false}});
Object.defineProperty(document,'visibilityState',{get:function(){return 'visible'}});
document.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation()},true);
window.addEventListener('blur',function(e){e.stopImmediatePropagation()},true);}}catch(e){}
function go(){
var SH=window.__shields,C;
try{C=JSON.parse(SH.cfg(H))}catch(e){return}
if(!C||C.off)return;
function G(o,p){var a=p.split('.');for(var i=0;i<a.length&&o!=null;i++)o=o[a[i]];return o}
function D(o,p){var a=p.split('.'),l=a.pop();for(var i=0;i<a.length&&o!=null;i++)o=o[a[i]];if(o!=null)try{delete o[l]}catch(e){}}
function V(v){return v==='undefined'?undefined:v==='false'?false:v==='true'?true:v==='null'?null:v==='noopFunc'?function(){}:v==='trueFunc'?function(){return true}:v==='falseFunc'?function(){return false}:v==='emptyObj'?{}:v==='emptyArr'?[]:isNaN(+v)?v:+v}
function T(o,c,val){var p=c[0];if(c.length==1){try{Object.defineProperty(o,p,{get:function(){return val},set:function(){},configurable:true})}catch(e){}return}
var cur=o[p];if(cur&&typeof cur=='object'){T(cur,c.slice(1),val);return}
var h=cur;try{Object.defineProperty(o,p,{configurable:true,get:function(){return h},set:function(n){h=n;if(n&&typeof n=='object')T(n,c.slice(1),val)}})}catch(e){}}
function R(s){try{var m=/^\/(.*)\/([gimsu]*)$/.exec(s);return m?new RegExp(m[1],m[2]):new RegExp(s.replace(/([.*+?^|()\[\]{}\\])/g,'\\$1'))}catch(e){return /^$/}}
function P(a){var c=a.split('.'),l=c.pop(),o=window;c.forEach(function(k){o=o&&o[k]});return o?[o,l]:null}
var SL={
'set-constant':function(a,v){if(a)T(window,a.split('.'),V(v))},
'json-prune':function(r,n){var rs=(r||'').split(' ').filter(Boolean),ns=(n||'').split(' ').filter(Boolean),op=JSON.parse;JSON.parse=function(){var x=op.apply(this,arguments);try{if(x&&typeof x=='object'&&ns.every(function(p){return G(x,p)!==undefined}))rs.forEach(function(p){D(x,p)})}catch(e){}return x}},
'abort-on-property-read':function(a){var p=P(a);if(p)try{Object.defineProperty(p[0],p[1],{get:function(){throw new ReferenceError(p[1])},set:function(){},configurable:true})}catch(e){}},
'abort-on-property-write':function(a){var p=P(a);if(p)try{Object.defineProperty(p[0],p[1],{set:function(){throw new ReferenceError(p[1])},get:function(){},configurable:true})}catch(e){}},
'abort-current-script':function(a,n){var p=P(a);if(!p)return;var re=n?R(n):null,cur=p[0][p[1]],me=document.currentScript;try{Object.defineProperty(p[0],p[1],{get:function(){var s=document.currentScript;if(s&&s!==me&&(!re||re.test(s.textContent||s.src)))throw new ReferenceError(p[1]);return cur},set:function(v){cur=v},configurable:true})}catch(e){}},
'no-setTimeout-if':function(n,d){var re=R(n||''),o=window.setTimeout;window.setTimeout=function(f,t){try{if(re.test(String(f))&&(!d||+d===t))return 0}catch(e){}return o.apply(this,arguments)}},
'no-setInterval-if':function(n,d){var re=R(n||''),o=window.setInterval;window.setInterval=function(f,t){try{if(re.test(String(f))&&(!d||+d===t))return 0}catch(e){}return o.apply(this,arguments)}},
'nowebrtc':function(){var f=function(){return{createDataChannel:function(){},close:function(){},addEventListener:function(){}}};window.RTCPeerConnection=f;window.webkitRTCPeerConnection=f}
};
var AL={'set':'set-constant','jsonp':'json-prune','aopr':'abort-on-property-read','aopw':'abort-on-property-write','acs':'abort-current-script','abort-current-inline-script':'abort-current-script','acis':'abort-current-script','nostif':'no-setTimeout-if','nosiif':'no-setInterval-if'};
(C.sl||[]).forEach(function(c){var n=String(c[0]).replace(/\.js$/,'');n=AL[n]||n;if(SL[n])try{SL[n].apply(null,c.slice(1))}catch(e){}});
try{var off=Math.floor(Math.random()*97),gid=CanvasRenderingContext2D.prototype.getImageData;
CanvasRenderingContext2D.prototype.getImageData=function(){var r=gid.apply(this,arguments);try{var d=r.data;for(var i=off*4;i<d.length;i+=388)d[i]^=1}catch(e){}return r};
var tdu=HTMLCanvasElement.prototype.toDataURL;
HTMLCanvasElement.prototype.toDataURL=function(){try{var w=this.width,h=this.height;if(w*h>0&&w*h<=90000){var c=document.createElement('canvas');c.width=w;c.height=h;var x=c.getContext('2d');x.drawImage(this,0,0);x.putImageData(x.getImageData(0,0,w,h),0,0);return tdu.apply(c,arguments)}}catch(e){}return tdu.apply(this,arguments)};
Object.defineProperty(Navigator.prototype,'hardwareConcurrency',{get:function(){return 4},configurable:true});
Object.defineProperty(Navigator.prototype,'deviceMemory',{get:function(){return 4},configurable:true})}catch(e){}
var st=document.createElement('style'),seen={},cls=[],ids=[],tm=0;
st.textContent=C.css||'';
function mount(){(document.head||document.documentElement).appendChild(st)}
if(document.documentElement)mount();else{var mo=new MutationObserver(function(){if(document.documentElement){mo.disconnect();mount()}});mo.observe(document,{childList:true})}
function add(n){if(n.id&&!seen['#'+n.id]){seen['#'+n.id]=1;ids.push(n.id)}var cl=n.classList;if(cl)for(var i=0;i<cl.length;i++){var c=cl[i];if(!seen['.'+c]){seen['.'+c]=1;cls.push(c)}}}
function sweep(r){if(r.nodeType!=1)return;add(r);var a=r.querySelectorAll('[class],[id]');for(var i=0;i<a.length;i++)add(a[i])}
function flush(){tm=0;if(!cls.length&&!ids.length)return;var r='';try{r=SH.gen(H,cls.join(' '),ids.join(' '))}catch(e){}cls=[];ids=[];if(r)st.appendChild(document.createTextNode(r))}
function sched(){if(!tm)tm=ST(flush,200)}
function begin(){sweep(document.documentElement);sched();new MutationObserver(function(ms){for(var i=0;i<ms.length;i++){var m=ms[i];if(m.type=='attributes')add(m.target);else for(var j=0;j<m.addedNodes.length;j++)sweep(m.addedNodes[j])}sched()}).observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['class','id']})}
if(document.readyState=='loading')document.addEventListener('DOMContentLoaded',begin);else begin();
if(H.indexOf('youtube.com')>-1)SI(function(){var p=document.querySelector('.html5-video-player'),v=document.querySelector('video');if(p&&v&&p.classList.contains('ad-showing')&&isFinite(v.duration))v.currentTime=v.duration;var b=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button');if(b)b.click()},500);
}
if(typeof window.__shields!=='undefined')go();else document.addEventListener('DOMContentLoaded',go);
})();"""

/** Activity background me jaye tab bhi WebView ko "visible" rakho, warna audio/video ruk jata hai. */
class BgWebView(c: Context) : WebView(c) {
    override fun onWindowVisibilityChanged(v: Int) {
        if (v != View.GONE) super.onWindowVisibilityChanged(View.VISIBLE)
    }
}

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {

    private val eng = AdEngine()
    private val ws = Regex("\\s+")

    // Engine ready hone se pehle (ya lists download na ho paye tab) ki chhoti fallback list
    private val builtin: Set<String> = """
        doubleclick.net googlesyndication.com googleadservices.com googletagmanager.com googletagservices.com
        google-analytics.com adservice.google.com 2mdn.net facebook.net scorecardresearch.com quantserve.com
        taboola.com outbrain.com adnxs.com criteo.com criteo.net hotjar.com mixpanel.com segment.io popads.net
        popcash.net propellerads.com adsterra.com exoclick.com juicyads.com mgid.com revcontent.com moatads.com
        pubmatic.com rubiconproject.com openx.net smartadserver.com adsrvr.org bluekai.com demdex.net clarity.ms
        mc.yandex.ru ads.twitter.com analytics.tiktok.com amazon-adsystem.com adcolony.com applovin.com inmobi.com
    """.trim().split(ws).toHashSet()

    // Ye sites apne aap desktop mode me khulti hain (WhatsApp Web / Spotify web player ke liye zaroori)
    private val desktopHosts = listOf("web.whatsapp.com", "open.spotify.com", "web.telegram.org")

    // Search engines (home screen ke button se badalte hain)
    private val engines = listOf(
        "Google" to "https://www.google.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Brave" to "https://search.brave.com/search?q=",
        "Bing" to "https://www.bing.com/search?q="
    )

    // Links se hataye jane wale tracking params (Brave jaisa) + https upgrade state
    private val trk = ("fbclid gclid dclid gbraid wbraid msclkid mc_eid mc_cid igshid yclid twclid ttclid _hsenc _hsmi " +
        "mkt_tok oly_anon_id oly_enc_id vero_id rb_clickid s_cid ref_src ref_url").split(" ").toHashSet()
    private val noUp = HashSet<String>()
    private var httpFallback: String? = null
    private var docStart = false

    private lateinit var prefs: SharedPreferences
    private lateinit var home: LinearLayout
    private lateinit var browse: FrameLayout
    private lateinit var fab: LinearLayout
    private lateinit var menu: LinearLayout
    private lateinit var list: ListView
    private lateinit var input: EditText
    private lateinit var statTv: TextView
    private lateinit var starBtn: TextView
    private lateinit var shBtn: TextView
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
    private fun engine() = prefs.getInt("eng", 0).coerceIn(0, engines.size - 1)
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
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
        prefs = getSharedPreferences("fb", MODE_PRIVATE)
        eng.fallback = builtin
        eng.off = prefs.getStringSet("off", emptySet()) ?: emptySet()
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
            text = "Shields engine load ho raha hai..."; setTextColor(Color.GRAY); textSize = 12f
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
        shBtn = circle("🛡") { toggleShields() }.apply { textSize = 12f }

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
            addView(shBtn)
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
                        updateShield()
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
        loadEngine()
        handleIntent(intent)
    }

    // ---------- Shields engine (lists download + parse, background thread) ----------
    private fun loadEngine() = eng.update(filesDir) {
        runOnUiThread {
            statTv.text = if (eng.ready) "Shields engine: ${eng.summary()}"
            else "Shields: basic mode (lists download nahi hui, internet check karo)"
        }
    }

    private fun updateShield() {
        val h = Uri.parse(web?.url ?: "").host
        shBtn.text = if (h != null && eng.isOff(h)) "🛡\nOFF" else "🛡\n${eng.blocked.get()}"
    }

    // Is site par Shields ON/OFF (agar koi site Shields ki wajah se kharab chale)
    private fun toggleShields() {
        val h = Uri.parse(web?.url ?: return).host ?: return
        val r = eng.reg(h)
        val off = (prefs.getStringSet("off", emptySet()) ?: emptySet()).toMutableSet()
        val nowOff = if (off.remove(r)) false else { off.add(r); true }
        prefs.edit().putStringSet("off", off).apply()
        eng.off = off
        toast(if (nowOff) "Shields OFF: $r" else "Shields ON: $r")
        web?.reload()
    }

    // ---------- URL cleanup: tracking params hatao, http -> https ----------
    private fun clean(url: String): String = try {
        val u = Uri.parse(url)
        val names = u.queryParameterNames
        fun bad(n: String) = n in trk || n.startsWith("utm_")
        if (names.none { bad(it) }) url else {
            val b = u.buildUpon().clearQuery()
            for (n in names) if (!bad(n)) for (v in u.getQueryParameters(n)) b.appendQueryParameter(n, v)
            b.build().toString()
        }
    } catch (e: Exception) { url }

    private fun upgrade(url: String): String {
        if (!url.startsWith("http://")) return url
        val h = Uri.parse(url).host ?: return url
        if (h in noUp || !h.contains('.') || h.startsWith("[") || h.endsWith(".local") ||
            h.all { it.isDigit() || it == '.' }) return url
        httpFallback = url
        return "https://" + url.substring(7)
    }

    private fun prepare(url: String) = upgrade(clean(url))

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
        val url = prepare(when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            "." in t && " " !in t -> "https://$t"
            else -> engines[engine()].second + Uri.encode(t)
        })
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

    private fun emptyFor(t: Int) = WebResourceResponse(
        when {
            (t and AdEngine.SCRIPT) != 0 -> "application/javascript"
            (t and AdEngine.CSS) != 0 -> "text/css"
            (t and AdEngine.IMAGE) != 0 -> "image/gif"
            else -> "text/plain"
        }, "utf-8", ByteArrayInputStream(ByteArray(0))
    )

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
        addJavascriptInterface(Shields(eng), "__shields")
        docStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (docStart) WebViewCompat.addDocumentStartJavaScript(this, DOC_JS, setOf("*"))

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val u = r.url
                val h = u.host ?: return null
                if (r.isForMainFrame) { eng.page(h); return null }       // top-level page kabhi block nahi hota
                val s = u.toString()
                val t = eng.typeOf(s, r.requestHeaders?.get("Accept"))
                return if (eng.blocks(s, h, t)) emptyFor(t) else null
            }

            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = nav(v, r)

            override fun onPageStarted(v: WebView, url: String, f: Bitmap?) {
                if (!docStart) v.evaluateJavascript(DOC_JS, null)
            }

            override fun onPageFinished(v: WebView, url: String) {
                if (!docStart) v.evaluateJavascript(DOC_JS, null)
                httpFallback = null
                updateStar(); updateShield()
            }

            // https upgrade fail ho to us site ke liye http par wapas
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                val fb = httpFallback
                if (r.isForMainFrame && fb != null && r.url.toString().startsWith("https://" + fb.substring(7).substringBefore('/'))) {
                    httpFallback = null
                    Uri.parse(fb).host?.let { noUp.add(it) }
                    v.loadUrl(fb)
                }
            }
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
            "http", "https" -> {
                val s = u.toString()
                val up = if (r.isForMainFrame) prepare(s) else s
                when {
                    up != s -> { v.loadUrl(up); true }                       // tracking params hate / https upgrade
                    r.isForMainFrame && !desktop && wantDesktop(h) -> {      // WhatsApp / Spotify web => desktop mode
                        setMode(v, true); v.loadUrl(s); true
                    }
                    else -> false                                            // baaki sab isi WebView me
                }
            }
            "intent" -> {
                val fb = try {
                    Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
                } catch (e: Exception) { null }
                if (fb != null && fb.startsWith("http")) v.loadUrl(fb)
                true
            }
            "about", "blob", "data", "javascript" -> false
            else -> true                                                     // market:// whatsapp:// tg:// app-open band
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

    private fun handleBack() {
        val w = web
        when {
            customView != null -> hideCustom()
            bar.visibility == View.VISIBLE -> hideBar()
            w != null && w.canGoBack() -> w.goBack()
            w != null -> closeSite()
            else -> finish()
        }
    }

    override fun onDestroy() {
        web?.destroy()
        stopService(Intent(this, PlaybackService::class.java))
        super.onDestroy()
    }
}
