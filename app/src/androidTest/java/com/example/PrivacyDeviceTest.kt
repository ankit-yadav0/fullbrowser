package com.example

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.BrowsingDataWiper
import com.example.util.PrivacyNetworkController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Device/emulator tests. They cannot run on the plain JVM. The VPN test assumes the device has NO
 * active VPN; the wipe test covers cookies only (see BrowsingDataWiper for what else is cleared).
 */
@RunWith(AndroidJUnit4::class)
class PrivacyDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun gateStaysClosedWithoutVpn() {
        val controller = PrivacyNetworkController(instrumentation.targetContext)
        instrumentation.runOnMainSync { controller.start { } }
        Thread.sleep(1_000)
        assertFalse(controller.isReady())
        instrumentation.runOnMainSync { controller.stop() }
        assertFalse(controller.isReady())
    }

    @Test fun wipeRemovesCookies() {
        val cookies = CookieManager.getInstance()
        val set = CountDownLatch(1)
        instrumentation.runOnMainSync { cookies.setCookie("https://example.com", "a=b; Secure") { set.countDown() } }
        assertTrue(set.await(5, TimeUnit.SECONDS))
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync { BrowsingDataWiper.wipe(instrumentation.targetContext) { done.countDown() } }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertNull(cookies.getCookie("https://example.com"))
    }
}
