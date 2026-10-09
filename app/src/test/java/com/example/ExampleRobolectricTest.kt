package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BookmarkRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Fullscreen Web", appName)
  }

  @Test
  fun `bookmark repository saves and restores bookmarks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo1 = BookmarkRepository(context)

    val added = repo1.addBookmark("https://example.com", "Example Domain")
    assertEquals("https://example.com", added.url)
    assertEquals("Example Domain", added.title)
    assertEquals(1, repo1.bookmarks.value.size)

    // Verify persistence by initializing a fresh repository instance
    val repo2 = BookmarkRepository(context)
    val restored = repo2.bookmarks.value
    assertEquals(1, restored.size)
    assertEquals("https://example.com", restored[0].url)
    assertEquals("Example Domain", restored[0].title)

    // Delete bookmark
    repo2.deleteBookmark(added.id)
    assertTrue(repo2.bookmarks.value.isEmpty())

    // Verify deletion persisted
    val repo3 = BookmarkRepository(context)
    assertTrue(repo3.bookmarks.value.isEmpty())
  }

  @Test
  fun `background audio service constants and actions are configured`() {
    assertEquals("com.example.action.START_AUDIO", com.example.service.BackgroundAudioService.ACTION_START)
    assertEquals("com.example.action.STOP_AUDIO", com.example.service.BackgroundAudioService.ACTION_STOP)
    assertEquals("com.example.action.UPDATE_TITLE", com.example.service.BackgroundAudioService.ACTION_UPDATE_TITLE)
    assertEquals("web_audio_playback_channel", com.example.service.BackgroundAudioService.CHANNEL_ID)
  }
}
