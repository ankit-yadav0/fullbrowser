package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.Bookmark
import com.example.util.NavigationPolicy
import com.example.util.UrlHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class BookmarkRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    init {
        loadBookmarks()
    }

    @Synchronized
    private fun loadBookmarks() {
        val jsonString = prefs.getString(KEY_BOOKMARKS, null)
        if (jsonString.isNullOrEmpty()) {
            _bookmarks.value = emptyList()
            return
        }

        try {
            val jsonArray = JSONArray(jsonString)
            val list = ArrayList<Bookmark>(jsonArray.length())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                val url = obj.optString("url", "")
                val title = obj.optString("title", UrlHelper.extractDisplayTitle(url)).take(MAX_TITLE)
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                if (NavigationPolicy.isWebUrl(url)) {
                    list.add(Bookmark(id = id, url = url, title = title, timestamp = timestamp))
                }
            }
            _bookmarks.value = list
        } catch (e: Exception) {
            android.util.Log.w("BookmarkRepository", "Bookmark storage operation failed")
            _bookmarks.value = emptyList()
        }
    }

    @Synchronized
    private fun saveBookmarks(list: List<Bookmark>) {
        _bookmarks.value = list
        try {
            val jsonArray = JSONArray()
            for (bm in list) {
                val obj = JSONObject()
                obj.put("id", bm.id)
                obj.put("url", bm.url)
                obj.put("title", bm.title)
                obj.put("timestamp", bm.timestamp)
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_BOOKMARKS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.w("BookmarkRepository", "Bookmark storage operation failed")
        }
    }

    @Synchronized
    fun addBookmark(url: String, title: String? = null): Bookmark {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isEmpty()) throw IllegalArgumentException("Bookmark URL cannot be empty")
        require(NavigationPolicy.isWebUrl(normalizedUrl)) { "Only http(s) bookmarks are allowed" }
        val displayTitle = UrlHelper.extractDisplayTitle(normalizedUrl, title)
            .filter { it.code >= 0x20 && it.code != 0x7f }
            .take(MAX_TITLE)
        val currentList = _bookmarks.value.toMutableList()

        // Remove existing bookmark with same URL if present so it moves to top
        // URL paths are case-sensitive: only an exact match is a duplicate.
        currentList.removeAll { it.url == normalizedUrl }

        val newBookmark = Bookmark(
            id = java.util.UUID.randomUUID().toString(),
            url = normalizedUrl,
            title = displayTitle,
            timestamp = System.currentTimeMillis()
        )
        currentList.add(0, newBookmark)
        saveBookmarks(currentList)
        return newBookmark
    }

    @Synchronized
    fun deleteBookmark(id: String) {
        val currentList = _bookmarks.value.toMutableList()
        currentList.removeAll { it.id == id }
        saveBookmarks(currentList)
    }

    companion object {
        private const val PREFS_NAME = "fullscreen_web_prefs"
        private const val KEY_BOOKMARKS = "saved_bookmarks"
        private const val MAX_TITLE = 200
    }
}
