package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GreetingUiTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun greeting_isDisplayed() {
        composeTestRule.setContent { MyApplicationTheme { Greeting("Robolectric") } }
        composeTestRule.onNodeWithText("Hello Robolectric!").assertIsDisplayed()
    }
}
