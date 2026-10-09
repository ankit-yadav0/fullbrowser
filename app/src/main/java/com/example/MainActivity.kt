package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BookmarkRepository
import com.example.ui.FullscreenWebScreen
import com.example.ui.HomeScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var bookmarkRepository: BookmarkRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        bookmarkRepository = BookmarkRepository(applicationContext)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    FullscreenWebApp(bookmarkRepository = bookmarkRepository)
                }
            }
        }
    }
}

sealed interface Screen {
    data object Home : Screen
    data class FullscreenWeb(val url: String) : Screen
}

private val ScreenSaver = Saver<Screen, String>(
    save = { screen ->
        when (screen) {
            is Screen.Home -> "HOME"
            is Screen.FullscreenWeb -> "WEB:${screen.url}"
        }
    },
    restore = { value ->
        if (value.startsWith("WEB:")) {
            Screen.FullscreenWeb(value.removePrefix("WEB:"))
        } else {
            Screen.Home
        }
    }
)

@Composable
fun FullscreenWebApp(
    bookmarkRepository: BookmarkRepository,
    modifier: Modifier = Modifier
) {
    val bookmarks by bookmarkRepository.bookmarks.collectAsStateWithLifecycle()
    var currentScreen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition",
        modifier = modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            is Screen.Home -> {
                HomeScreen(
                    bookmarks = bookmarks,
                    onOpenUrl = { url ->
                        currentScreen = Screen.FullscreenWeb(url)
                    },
                    onDeleteBookmark = { id ->
                        bookmarkRepository.deleteBookmark(id)
                    }
                )
            }
            is Screen.FullscreenWeb -> {
                FullscreenWebScreen(
                    initialUrl = screen.url,
                    bookmarks = bookmarks,
                    onExitToHome = {
                        currentScreen = Screen.Home
                    },
                    onAddBookmark = { url, title ->
                        bookmarkRepository.addBookmark(url, title)
                    },
                    onDeleteBookmark = { id ->
                        bookmarkRepository.deleteBookmark(id)
                    }
                )
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
