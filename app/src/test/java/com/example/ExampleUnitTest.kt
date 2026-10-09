package com.example

import com.example.util.UrlHelper
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun urlHelper_prependsHttps_whenSchemeMissing() {
    assertEquals("https://example.com", UrlHelper.normalizeUrl("example.com"))
    assertEquals("https://google.com/search?q=test", UrlHelper.normalizeUrl("google.com/search?q=test"))
  }

  @Test
  fun urlHelper_handlesSearchQueries() {
    assertEquals("https://www.google.com/search?q=kotlin+android", UrlHelper.normalizeUrl("kotlin android"))
    assertEquals("https://www.google.com/search?q=hello", UrlHelper.normalizeUrl("hello"))
  }

  @Test
  fun urlHelper_preservesExistingScheme() {
    assertEquals("http://example.com", UrlHelper.normalizeUrl("http://example.com"))
    assertEquals("https://example.com", UrlHelper.normalizeUrl("https://example.com"))
  }

  @Test
  fun urlHelper_extractsDisplayTitle() {
    assertEquals("example.com", UrlHelper.extractDisplayTitle("https://example.com"))
    assertEquals("My Custom Page", UrlHelper.extractDisplayTitle("https://example.com", "My Custom Page"))
  }

  @Test
  fun drmDiagnostics_wellKnownUuidsAreCorrect() {
    assertEquals("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed", com.example.util.DrmDiagnostics.WIDEVINE_UUID.toString())
    assertEquals("9a04f079-9840-4286-ab92-e65be0885f95", com.example.util.DrmDiagnostics.PLAYREADY_UUID.toString())
    assertEquals("e2719d58-a985-b3c9-781a-b030af78d30e", com.example.util.DrmDiagnostics.CLEARKEY_UUID.toString())
  }

  @Test
  fun drmDiagnostics_sanitizesSensitiveInformation() {
    val sensitive = "User login with Bearer eyJhbGciOi... and password=secret123 and cookie: session=abc"
    val sanitized = com.example.util.DrmDiagnostics.sanitizeLog(sensitive)
    assertFalse(sanitized.contains("secret123"))
    assertFalse(sanitized.contains("eyJhbGciOi"))
    assertTrue(sanitized.contains("[REDACTED]"))
  }
}
