package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BookmarkRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookmarkRepositorySecurityTest {
    private fun repo() = BookmarkRepository(ApplicationProvider.getApplicationContext<Context>())

    @Test fun urlsDifferingOnlyByPathCaseAreDistinctBookmarks() {
        val r = repo()
        r.addBookmark("https://example.com/Foo", "A")
        r.addBookmark("https://example.com/foo", "B")
        assertEquals(2, r.bookmarks.value.size)
        r.addBookmark("https://example.com/foo", "B again") // exact duplicate moves to top
        assertEquals(2, r.bookmarks.value.size)
        assertEquals("https://example.com/foo", r.bookmarks.value[0].url)
    }

    @Test fun nonWebSchemesAreRejected() {
        val r = repo()
        for (u in listOf("javascript:alert(1)", "file:///x", "intent://a#Intent;end", "data:text/html,x", "about:blank")) {
            try {
                r.addBookmark(u, "x")
                fail("expected rejection of $u")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertEquals(0, r.bookmarks.value.size)
    }

    @Test fun titleIsLengthCappedAndControlCharsStripped() {
        val r = repo()
        val b = r.addBookmark("https://example.com", "x".repeat(1000) + "\u0007")
        assertEquals(200, b.title.length)
        val c = r.addBookmark("https://example.org", "a\u0000b\nc")
        assertEquals("abc", c.title)
    }
}
