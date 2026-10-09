package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Static regression guards over the shipped manifest / backup configuration (plain JVM, no Android). */
class ManifestAndBackupConfigTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun load(relative: String): Document {
        val f = File(relative).takeIf { it.exists() } ?: File("app/$relative")
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(f)
    }

    private fun attr(e: Element, name: String): String? =
        if (e.hasAttributeNS(androidNs, name)) e.getAttributeNS(androidNs, name) else null

    private fun elements(doc: Document, tag: String): List<Element> {
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test fun applicationDisablesBackupAndCleartextAndIsNotDebuggable() {
        val app = elements(load("src/main/AndroidManifest.xml"), "application").single()
        assertEquals("false", attr(app, "allowBackup"))
        assertEquals("false", attr(app, "usesCleartextTraffic"))
        assertNull(attr(app, "debuggable"))
    }

    @Test fun onlyTheLauncherActivityIsExportedAndNoReceiversOrProviders() {
        val doc = load("src/main/AndroidManifest.xml")
        val activities = elements(doc, "activity")
        assertEquals(1, activities.size)
        assertEquals(".MainActivity", attr(activities[0], "name"))
        assertEquals("true", attr(activities[0], "exported"))
        for (s in elements(doc, "service")) assertEquals("false", attr(s, "exported"))
        assertTrue(elements(doc, "receiver").isEmpty())
        assertTrue(elements(doc, "provider").isEmpty())
    }

    @Test fun permissionSetIsExactlyTheExpectedMinimum() {
        val declared = elements(load("src/main/AndroidManifest.xml"), "uses-permission")
            .mapNotNull { attr(it, "name") }.toSet()
        val expected = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO"
        )
        assertEquals(expected, declared)
        assertFalse(declared.any { it.contains("LOCATION") || it.contains("STORAGE") })
    }

    private val requiredDomains = setOf("root", "file", "database", "sharedpref", "external", "cache")

    private fun assertAllExcluded(container: Element) {
        val excludes = (0 until container.childNodes.length).map { container.childNodes.item(it) }
            .filterIsInstance<Element>()
        assertTrue("no <include> allowed", excludes.none { it.tagName == "include" })
        val excluded = excludes.filter { it.tagName == "exclude" }
        for (d in requiredDomains) {
            assertTrue("domain $d must be excluded", excluded.any { it.getAttribute("domain") == d && it.getAttribute("path") == "." })
        }
    }

    @Test fun legacyFullBackupRulesExcludeEverything() {
        val root = load("src/main/res/xml/backup_rules.xml").documentElement
        assertEquals("full-backup-content", root.tagName)
        assertAllExcluded(root)
    }

    @Test fun dataExtractionRulesExcludeEverythingForCloudAndTransfer() {
        val doc = load("src/main/res/xml/data_extraction_rules.xml")
        assertAllExcluded(elements(doc, "cloud-backup").single())
        assertAllExcluded(elements(doc, "device-transfer").single())
    }
}
