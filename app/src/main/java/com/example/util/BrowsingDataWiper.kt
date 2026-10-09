package com.example.util

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

/**
 * Explicit, user-triggered privacy wipe of WEBVIEW data only. Bookmarks (SharedPreferences) and files
 * already saved to Downloads are intentionally NOT touched.
 *
 * Cleared: cookies, WebStorage (localStorage/IndexedDB/etc. as far as WebStorage.deleteAllData covers
 * them), HTTP cache and in-memory history. Whether Service Worker registrations / Cache Storage are
 * fully removed by deleteAllData() depends on the installed WebView version (device verification).
 * Must be called on the main thread while no browser WebView is alive (the Home screen).
 */
object BrowsingDataWiper {
    fun wipe(context: Context, onCookiesCleared: () -> Unit = {}) {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies { _ ->
            runCatching { cookies.flush() }
            onCookiesCleared()
        }
        runCatching { WebStorage.getInstance().deleteAllData() }
        runCatching {
            val temp = WebView(context.applicationContext)
            temp.clearCache(true)
            temp.clearHistory()
            temp.destroy()
        }
    }
}
