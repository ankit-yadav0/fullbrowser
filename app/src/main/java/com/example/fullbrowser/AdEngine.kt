package com.example.fullbrowser

import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Brave-Shields jaisa engine. EasyList / EasyPrivacy / uBlock lists (ABP + uBO syntax) padhta hai:
 *  - network filtering: token index, ||domain^, wildcards, $third-party, $domain=, $script/$image/..., @@ exceptions, $important
 *  - cosmetic filtering: generic (class/id lookup) + site specific, #@# exceptions
 *  - scriptlets (##+js(...)): config JS ko milti hai (JS side MainActivity.DOC_JS me)
 * Regex rules / procedural cosmetic filters / $popup / $csp / $removeparam abhi skip hote hain.
 */
class AdEngine {
    companion object {
        const val SUBDOC = 2; const val SCRIPT = 4; const val IMAGE = 8; const val CSS = 16
        const val XHR = 32; const val MEDIA = 64; const val FONT = 128; const val OTHER = 256
        private const val DOC = 1
        private const val SUB = SUBDOC or SCRIPT or IMAGE or CSS or XHR or MEDIA or FONT or OTHER
        private const val DANCH = 1; private const val SANCH = 2; private const val EANCH = 4
        private const val EXC = 8; private const val IMP = 16; private const val THIRD = 32; private const val FIRST = 64
        private val LISTS = listOf(
            "easylist" to "https://ublockorigin.github.io/uAssets/thirdparties/easylist.txt",
            "easyprivacy" to "https://ublockorigin.github.io/uAssets/thirdparties/easyprivacy.txt",
            "ubo-filters" to "https://ublockorigin.github.io/uAssets/filters/filters.min.txt",
            "ubo-privacy" to "https://ublockorigin.github.io/uAssets/filters/privacy.min.txt"
        )
        private val SLD = setOf("co", "com", "org", "net", "gov", "edu", "ac")
        private val PROC = listOf(":has-text", ":xpath", ":matches", ":upward", ":remove", ":style", "-abp-",
            ":contains", ":min-text", ":watch", ":others", ":nth-ancestor")
    }

    private class Rule(val pat: String, val fl: Int, val mask: Int, val doms: Array<String>?) {
        val segs: Array<String>? = if (pat.indexOf('*') >= 0) pat.split('*').toTypedArray() else null
    }

    private class State {
        val block = HashMap<String, Any>(); val allow = HashMap<String, Any>()
        val noTokB = ArrayList<Rule>(); val noTokA = ArrayList<Rule>()
        val gen = HashMap<String, ArrayList<String>>(); val genOther = ArrayList<String>()
        val site = HashMap<String, ArrayList<String>>(); val siteExc = HashMap<String, HashSet<String>>()
        val sl = HashMap<String, ArrayList<String>>()
        var nRules = 0; var nCos = 0
    }

    @Volatile private var st = State()
    @Volatile var ready = false
    @Volatile var off: Set<String> = emptySet()          // jin sites par Shields band hai (registrable domain)
    @Volatile var fallback: Set<String> = emptySet()     // lists load hone se pehle ki chhoti domain list
    @Volatile private var pageHost: String? = null
    @Volatile private var pageReg: String? = null
    val blocked = AtomicInteger()
    private val excCache = ConcurrentHashMap<String, Set<String>>()

    private fun Int.has(f: Int) = (this and f) != 0
    fun summary() = "${st.nRules} network + ${st.nCos} cosmetic rules"

    // ---------- helpers ----------
    private fun tok(c: Char) = (c in 'a'..'z') || (c in '0'..'9')
    private fun isSep(c: Char) = !((c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '_' || c == '-' || c == '.' || c == '%')
    private fun suffix(h: String, d: String) = h == d || h.endsWith(".$d")

    private fun chain(h: String): List<String> {
        val out = ArrayList<String>(); var d = h
        while (true) {
            out.add(d)
            val i = d.indexOf('.')
            if (i < 0) break
            d = d.substring(i + 1)
        }
        return out
    }

    private fun inSuffix(set: Set<String>, h: String): Boolean {
        for (d in chain(h)) if (d in set) return true
        return false
    }

    fun reg(h: String): String {
        val p = h.split('.'); val n = p.size
        if (n <= 2) return h
        return if (p[n - 1].length == 2 && p[n - 2] in SLD) p.subList(n - 3, n).joinToString(".")
        else p.subList(n - 2, n).joinToString(".")
    }

    fun isOff(h: String) = reg(h) in off
    fun page(h: String) { pageHost = h; pageReg = reg(h); blocked.set(0) }

    /** WebView request type nahi batata, isliye Accept header + extension se andaza lagate hain. */
    fun typeOf(url: String, accept: String?): Int {
        val a = accept ?: ""
        val p = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            a.contains("text/html") -> SUBDOC
            a.contains("image/") -> IMAGE
            a.contains("text/css") -> CSS
            p.endsWith(".js") || p.endsWith(".mjs") -> SCRIPT
            p.endsWith(".css") -> CSS
            p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".gif") ||
                p.endsWith(".webp") || p.endsWith(".svg") || p.endsWith(".ico") || p.endsWith(".avif") -> IMAGE
            p.endsWith(".woff") || p.endsWith(".woff2") || p.endsWith(".ttf") || p.endsWith(".otf") -> FONT
            p.endsWith(".mp4") || p.endsWith(".webm") || p.endsWith(".m3u8") || p.endsWith(".mp3") ||
                p.endsWith(".m4a") || p.endsWith(".ts") || p.endsWith(".mpd") -> MEDIA
            p.endsWith(".html") || p.endsWith(".htm") || p.endsWith("/") -> SUBDOC or XHR
            else -> SCRIPT or XHR or OTHER
        }
    }

    // ---------- network matching ----------
    fun blocks(url: String, host: String, type: Int): Boolean {
        val pr = pageReg
        if (pr != null && pr in off) return false
        if (!ready) return inSuffix(fallback, host)
        val s = st
        val u = url.lowercase()
        val third = pr != null && reg(host) != pr
        val b = find(s.block, s.noTokB, u, third, type) ?: return false
        if (!b.fl.has(IMP) && find(s.allow, s.noTokA, u, third, type) != null) return false
        blocked.incrementAndGet()
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun find(map: HashMap<String, Any>, noTok: ArrayList<Rule>, u: String, third: Boolean, type: Int): Rule? {
        val n = u.length
        var i = 0
        while (i < n) {
            if (!tok(u[i])) { i++; continue }
            var j = i
            while (j < n && tok(u[j])) j++
            if (j - i >= 3) {
                val b = map[u.substring(i, j)]
                if (b is Rule) { if (ok(b, u, third, type)) return b }
                else if (b != null) for (r in b as ArrayList<Rule>) if (ok(r, u, third, type)) return r
            }
            i = j
        }
        for (r in noTok) if (ok(r, u, third, type)) return r
        return null
    }

    private fun ok(r: Rule, u: String, third: Boolean, type: Int): Boolean {
        if (!r.mask.has(type)) return false
        if (r.fl.has(THIRD) && !third) return false
        if (r.fl.has(FIRST) && third) return false
        if (!domOk(r.doms)) return false
        return match(u, r)
    }

    private fun domOk(d: Array<String>?): Boolean {
        if (d == null) return true
        val p = pageHost
        var inc = false; var anyInc = false
        for (x in d) {
            if (x.startsWith("~")) { if (p != null && suffix(p, x.substring(1))) return false }
            else { anyInc = true; if (p != null && suffix(p, x)) inc = true }
        }
        return !anyInc || inc
    }

    // '^' = separator ya URL ka end
    private fun matchEnd(u: String, at: Int, s: String): Int {
        var k = at
        for (c in s) {
            if (c == '^') {
                if (k == u.length) continue
                if (!isSep(u[k])) return -1
                k++
            } else {
                if (k >= u.length || u[k] != c) return -1
                k++
            }
        }
        return k
    }

    private fun match(u: String, r: Rule): Boolean {
        val segs = r.segs ?: arrayOf(r.pat)
        var pos = -1
        if (r.fl.has(DANCH)) {
            val hs = u.indexOf("://").let { if (it < 0) 0 else it + 3 }
            var he = hs
            while (he < u.length && u[he] != '/' && u[he] != '?' && u[he] != '#' && u[he] != ':') he++
            var p0 = hs
            while (p0 <= he) {
                val e = matchEnd(u, p0, segs[0])
                if (e >= 0) { pos = e; break }
                val d = u.indexOf('.', p0)
                if (d < 0 || d >= he) break
                p0 = d + 1
            }
        } else if (r.fl.has(SANCH)) {
            pos = matchEnd(u, 0, segs[0])
        } else {
            var i = 0
            while (i <= u.length) {
                val e = matchEnd(u, i, segs[0])
                if (e >= 0) { pos = e; break }
                i++
            }
        }
        if (pos < 0) return false
        for (k in 1 until segs.size) {
            var f = -1; var i = pos
            while (i <= u.length) {
                val e = matchEnd(u, i, segs[k])
                if (e >= 0) { f = e; break }
                i++
            }
            if (f < 0) return false
            pos = f
        }
        return !r.fl.has(EANCH) || pos == u.length
    }

    // ---------- parsing ----------
    private fun bit(o: String) = when (o) {
        "script" -> SCRIPT; "image" -> IMAGE; "stylesheet", "css" -> CSS; "xmlhttprequest", "xhr" -> XHR
        "subdocument", "frame" -> SUBDOC; "document", "doc" -> DOC; "media" -> MEDIA; "font" -> FONT
        "other", "ping", "websocket", "object", "beacon" -> OTHER; "all" -> SUB or DOC
        else -> 0
    }

    private fun add(raw: String, s: State) {
        val l = raw.trim()
        if (l.isEmpty() || l[0] == '!' || l[0] == '[') return
        val h = l.indexOf('#')
        if (h >= 0) {
            val k = when {
                l.startsWith("##", h) -> 0
                l.startsWith("#@#", h) -> 1
                l.startsWith("#?#", h) || l.startsWith("#$#", h) || l.startsWith("#%#", h) -> 2
                else -> -1
            }
            if (k >= 0) {
                if (k < 2) cosmetic(l.substring(0, h), l.substring(h + if (k == 0) 2 else 3), k == 1, s)
                return
            }
        }
        network(l, s)
    }

    private fun key(sel: String): String? {
        var i = 0
        while (i < sel.length && sel[i].isLetterOrDigit()) i++
        if (i >= sel.length || (sel[i] != '.' && sel[i] != '#')) return null
        var j = i + 1
        while (j < sel.length && (sel[j].isLetterOrDigit() || sel[j] == '-' || sel[j] == '_')) j++
        return if (j > i + 1) sel.substring(i + 1, j) else null
    }

    private fun cosmetic(doms: String, sel: String, exc: Boolean, s: State) {
        val ds = if (doms.isEmpty()) listOf("") else doms.split(',').filter { !it.startsWith("~") && !it.contains('*') }
        if (ds.isEmpty()) return
        if (sel.startsWith("+js(")) {
            if (exc || !sel.endsWith(")")) return
            val call = sel.substring(4, sel.length - 1).split(',').joinToString("\u0001") { it.trim() }
            for (d in ds) s.sl.getOrPut(d) { ArrayList() }.add(call)
            return
        }
        if (sel.isEmpty() || sel.startsWith("^") || PROC.any { sel.contains(it) }) return
        if (exc) {
            for (d in ds) s.siteExc.getOrPut(d) { HashSet() }.add(sel)
            return
        }
        s.nCos++
        if (doms.isEmpty()) {
            val k = key(sel)
            if (k != null) s.gen.getOrPut(k) { ArrayList() }.add(sel)
            else if (s.genOther.size < 1500) s.genOther.add(sel)
        } else {
            for (d in ds) s.site.getOrPut(d) { ArrayList() }.add(sel)
        }
    }

    private fun tokenOf(p: String, fl: Int): String? {
        var best: String? = null
        var i = 0
        while (i < p.length) {
            if (!tok(p[i])) { i++; continue }
            var j = i
            while (j < p.length && tok(p[j])) j++
            val leftOk = if (i == 0) fl.has(DANCH) || fl.has(SANCH) else p[i - 1] != '*'
            val rightOk = if (j == p.length) fl.has(EANCH) else p[j] != '*'
            if (leftOk && rightOk && j - i >= 3 && (best == null || j - i > best.length)) best = p.substring(i, j)
            i = j
        }
        return best
    }

    @Suppress("UNCHECKED_CAST")
    private fun put(m: HashMap<String, Any>, t: String, r: Rule) {
        when (val c = m[t]) {
            null -> m[t] = r
            is Rule -> m[t] = arrayListOf(c, r)
            else -> (c as ArrayList<Rule>).add(r)
        }
    }

    private fun network(line: String, s: State) {
        var p = line
        var fl = 0
        if (p.startsWith("@@")) { fl = EXC; p = p.substring(2) }
        if (p.length > 2 && p[0] == '/' && p.endsWith("/")) return        // regex rules skip
        var opts = ""
        val d = p.lastIndexOf('$')
        if (d >= 0) { opts = p.substring(d + 1); p = p.substring(0, d) }
        var types = 0; var neg = 0; var doms: Array<String>? = null
        if (opts.isNotEmpty()) for (o in opts.split(',')) {
            when {
                o == "third-party" || o == "3p" -> fl = fl or THIRD
                o == "~third-party" || o == "1p" || o == "first-party" || o == "~3p" -> fl = fl or FIRST
                o == "important" -> fl = fl or IMP
                o == "match-case" || o == "empty" || o == "mp4" || o.startsWith("redirect=") -> {}
                o.startsWith("domain=") -> doms = o.substring(7).split('|').toTypedArray()
                o.startsWith("~") && bit(o.substring(1)) != 0 -> neg = neg or bit(o.substring(1))
                bit(o) != 0 -> types = types or bit(o)
                else -> return                                              // popup, csp, removeparam, ... skip
            }
        }
        val mask = (if (types != 0) types else SUB) and neg.inv()
        if (!mask.has(SUB)) return
        if (p.startsWith("||")) { fl = fl or DANCH; p = p.substring(2) }
        else if (p.startsWith("|")) { fl = fl or SANCH; p = p.substring(1) }
        if (p.endsWith("|")) { fl = fl or EANCH; p = p.substring(0, p.length - 1) }
        p = p.trim('*').lowercase()
        if (p.isEmpty()) return
        val r = Rule(p, fl, mask, doms)
        val exc = fl.has(EXC)
        val t = tokenOf(p, fl)
        if (t == null) (if (exc) s.noTokA else s.noTokB).add(r) else put(if (exc) s.allow else s.block, t, r)
        s.nRules++
    }

    // ---------- download + load (background thread) ----------
    fun update(dir: File, done: () -> Unit) {
        Thread {
            val d = File(dir, "lists")
            d.mkdirs()
            for ((name, url) in LISTS) {
                val f = File(d, "$name.txt")
                if (f.exists() && System.currentTimeMillis() - f.lastModified() < 3 * 86400000L) continue
                try {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 10000; c.readTimeout = 30000
                    val tmp = File(d, "$name.tmp")
                    c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    if (tmp.length() > 20000) tmp.renameTo(f) else tmp.delete()
                } catch (e: Exception) { }
            }
            try {
                val s = State()
                for ((name, _) in LISTS) {
                    val f = File(d, "$name.txt")
                    if (f.exists()) f.bufferedReader().use { r -> r.forEachLine { add(it, s) } }
                }
                if (s.nRules > 0) { st = s; excCache.clear(); ready = true }
            } catch (e: Throwable) { }
            done()
        }.start()
    }

    // ---------- JS side ko config ----------
    private fun excFor(h: String): Set<String> = excCache.getOrPut(h) {
        val s = st; val e = HashSet<String>()
        s.siteExc[""]?.let { e.addAll(it) }
        for (d in chain(h)) s.siteExc[d]?.let { e.addAll(it) }
        e
    }

    fun cfg(h: String): String {
        val o = JSONObject()
        if (isOff(h)) return o.put("off", true).toString()
        val s = st; val exc = excFor(h)
        val css = StringBuilder(); val sl = JSONArray()
        for (d in chain(h)) {
            s.site[d]?.forEach { if (it !in exc) css.append(it).append("{display:none!important}") }
            s.sl[d]?.forEach { sl.put(JSONArray(it.split('\u0001'))) }
        }
        s.sl[""]?.forEach { sl.put(JSONArray(it.split('\u0001'))) }
        s.genOther.forEach { if (it !in exc) css.append(it).append("{display:none!important}") }
        return o.put("css", css.toString()).put("sl", sl).toString()
    }

    fun gen(h: String, cls: String, ids: String): String {
        if (isOff(h)) return ""
        val s = st; val exc = excFor(h); val sb = StringBuilder()
        for (k in cls.split(' ') + ids.split(' ')) s.gen[k]?.forEach { if (it !in exc) sb.append(it).append("{display:none!important}") }
        return sb.toString()
    }
}

/** JS ko sirf do read-only calls milte hain (page me `window.__shields`). */
class Shields(private val e: AdEngine) {
    @JavascriptInterface fun cfg(h: String): String = e.cfg(h)
    @JavascriptInterface fun gen(h: String, c: String, i: String): String = e.gen(h, c, i)
}
