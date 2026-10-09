package com.example

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device tests of the INSTALLED (merged) manifest and of window flags across Activity recreation. */
@RunWith(AndroidJUnit4::class)
class WindowAndManifestDeviceTest {
    private fun isSecure(flags: Int) = (flags and WindowManager.LayoutParams.FLAG_SECURE) != 0

    @Test fun mainWindowIsSecureAndStaysSecureAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(isSecure(it.window.attributes.flags)) }
            scenario.recreate()
            scenario.onActivity { assertTrue(isSecure(it.window.attributes.flags)) }
        }
    }

    @Test fun installedPackageExportsOnlyTheLauncherActivityAndDisablesBackup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, flags)
        // Only our own components: debug-only library activities (e.g. compose ui-test-manifest) are ignored.
        val ownActivities = info.activities.orEmpty().filter { it.name.startsWith("com.example") }
        assertEquals(listOf("com.example.MainActivity"), ownActivities.filter { it.exported }.map { it.name })
        assertTrue(info.services.orEmpty().filter { it.name.startsWith("com.example") }.none { it.exported })
        assertTrue(info.receivers.orEmpty().none { it.exported && it.name.startsWith("com.example") })
        assertTrue(info.providers.orEmpty().none { it.exported && it.name.startsWith("com.example") })
        assertEquals(0, ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
