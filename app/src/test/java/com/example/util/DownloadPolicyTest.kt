package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DownloadPolicyTest {
    private fun rejects(url: String) {
        try {
            DownloadPolicy.validateHop(url)
            fail("expected rejection of $url")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun acceptsPlainHttps() {
        assertNotNull(DownloadPolicy.validateHop("https://example.com/f.zip"))
        assertNotNull(DownloadPolicy.validateHop("https://cdn.example.com:8443/f.zip?x=1"))
    }

    @Test fun rejectsHttpAndOtherSchemes() {
        rejects("http://example.com/f.zip")
        rejects("ftp://example.com/f.zip")
        rejects("file:///sdcard/f")
        rejects("data:text/plain,hi")
        rejects("blob:https://a.com/uuid")
        rejects("https://")
        rejects("")
    }

    @Test fun rejectsCredentialsAndPrivateTargets() {
        rejects("https://user:pw@example.com/f")
        rejects("https://192.168.1.1/router")
        rejects("https://10.0.0.5/x")
        rejects("https://localhost/x")
        rejects("https://127.0.0.1:8080/x")
        rejects("https://[::1]/x")
        rejects("https://2130706433/x")
        rejects("https://169.254.169.254/latest/meta-data")
    }

    @Test fun redirectResolutionAndDowngradeBlocking() {
        assertEquals("https://a.com/z", DownloadPolicy.nextHop("https://a.com/x/y", "../z"))
        assertEquals("https://cdn.example.net/f", DownloadPolicy.nextHop("https://a.com/x", "//cdn.example.net/f"))
        assertEquals("https://b.com/f", DownloadPolicy.nextHop("https://a.com/x", "https://b.com/f"))
        for (loc in listOf("http://b.com/", "https://127.0.0.1/", "https://192.168.0.1/", "ftp://b.com/", "https://u@evil.com/", "", "  ", "https://a.com/%zz")) {
            try {
                DownloadPolicy.nextHop("https://a.com/x", loc)
                fail("expected rejection of redirect to '$loc'")
            } catch (_: IllegalArgumentException) {
            }
        }
        try {
            DownloadPolicy.nextHop("https://a.com/x", null)
            fail("null location must be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun credentialsOnlyForSameOrigin() {
        assertTrue(DownloadPolicy.mayAttachCredentials("https://a.com/x", "https://a.com/y"))
        assertTrue(DownloadPolicy.mayAttachCredentials("https://a.com:443/x", "https://a.com/y"))
        assertFalse(DownloadPolicy.mayAttachCredentials("https://a.com/x", "https://cdn.a.com/y"))
        assertFalse(DownloadPolicy.mayAttachCredentials("https://a.com/x", "https://b.com/y"))
        assertFalse(DownloadPolicy.mayAttachCredentials("https://a.com/x", "https://a.com:8443/y"))
        assertFalse(DownloadPolicy.mayAttachCredentials("https://a.com/x", "garbage"))
    }

    @Test fun refererOnlySameOriginHttps() {
        assertEquals("https://a.com/page", DownloadPolicy.refererFor("https://a.com/page", "https://a.com/file"))
        assertNull(DownloadPolicy.refererFor("https://a.com/page", "https://b.com/file"))
        assertNull(DownloadPolicy.refererFor("http://a.com/page", "http://a.com/file"))
        assertNull(DownloadPolicy.refererFor(null, "https://a.com/file"))
        assertNull(DownloadPolicy.refererFor("https://u@a.com/p", "https://a.com/file"))
    }

    @Test fun fileNameSanitisation() {
        assertEquals("download", DownloadPolicy.safeFileName(null))
        assertEquals("download", DownloadPolicy.safeFileName(""))
        assertEquals("download", DownloadPolicy.safeFileName("   "))
        assertEquals("download", DownloadPolicy.safeFileName(".."))
        assertEquals("download", DownloadPolicy.safeFileName("___"))
        assertEquals("a_b.txt", DownloadPolicy.safeFileName("a\u0000b.txt"))
        assertEquals("a_b", DownloadPolicy.safeFileName("a\\b"))
        assertEquals("con_aux_.txt", DownloadPolicy.safeFileName("con:aux?.txt"))
        assertEquals("_gpj.exe", DownloadPolicy.safeFileName("\u202Egpj.exe"))
        assertEquals(180, DownloadPolicy.safeFileName("a".repeat(300)).length)
        val traversal = DownloadPolicy.safeFileName("../../etc/passwd")
        assertFalse(traversal.contains("/"))
        assertFalse(traversal.contains("\\"))
        assertFalse(traversal.startsWith("."))
    }

    @Test fun contentDispositionParsing() {
        assertEquals("report.pdf", DownloadPolicy.parseContentDisposition("attachment; filename=\"report.pdf\""))
        assertEquals("report.pdf", DownloadPolicy.parseContentDisposition("attachment; filename=report.pdf"))
        assertEquals("na\u00EFve file.txt", DownloadPolicy.parseContentDisposition("attachment; filename*=UTF-8''na%C3%AFve%20file.txt"))
        assertEquals("b.txt", DownloadPolicy.parseContentDisposition("attachment; filename=\"a.txt\"; filename*=UTF-8''b.txt"))
        assertNull(DownloadPolicy.parseContentDisposition("inline"))
        assertNull(DownloadPolicy.parseContentDisposition(null))
        assertNull(DownloadPolicy.parseContentDisposition("  "))
    }

    @Test fun mimeCleaning() {
        assertEquals("text/html", DownloadPolicy.cleanMimeType("text/html; charset=utf-8"))
        assertEquals("application/pdf", DownloadPolicy.cleanMimeType(null, "application/pdf"))
        assertEquals("image/png", DownloadPolicy.cleanMimeType("IMAGE/PNG"))
        assertEquals("application/octet-stream", DownloadPolicy.cleanMimeType("garbage", null))
        assertEquals("application/octet-stream", DownloadPolicy.cleanMimeType("", null))
        assertEquals("application/octet-stream", DownloadPolicy.cleanMimeType())
    }
}
