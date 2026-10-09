package com.example.util

import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.util.Log
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

object DrmDiagnostics {
    private const val TAG = "DrmDiagnostics"

    // Well-known DRM scheme UUIDs
    val WIDEVINE_UUID: UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
    val PLAYREADY_UUID: UUID = UUID.fromString("9a04f079-9840-4286-ab92-e65be0885f95")
    val CLEARKEY_UUID: UUID = UUID.fromString("e2719d58-a985-b3c9-781a-b030af78d30e")

    /**
     * Executes comprehensive diagnostic logging for Android OS, WebView version,
     * User-Agent, MediaDrm capabilities, Widevine availability, and security level.
     */
    fun runDiagnostics(context: Context, userAgent: String? = null) {
        Log.i(TAG, "==================== DRM & WEBVIEW DIAGNOSTICS ====================")

        // 1. Android OS & Device Info
        Log.i(TAG, "[OS] Android API Level: ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
        Log.i(TAG, "[OS] Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) - Hardware: ${Build.HARDWARE}")

        // 2. WebView Package & Version
        try {
            val webViewPackage = WebViewCompat.getCurrentWebViewPackage(context)
            if (webViewPackage != null) {
                Log.i(TAG, "[WebView] Package: ${webViewPackage.packageName}, Version: ${webViewPackage.versionName}")
            } else {
                Log.w(TAG, "[WebView] WebViewCompat.getCurrentWebViewPackage returned null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[WebView] Error querying WebView package info: ${e.message}")
        }

        // 3. User-Agent
        if (!userAgent.isNullOrBlank()) {
            Log.i(TAG, "[WebView] Active User-Agent: $userAgent")
        }

        // 4. WebViewFeature Support
        logWebViewFeatures()

        // 5. MediaDrm Supported UUIDs & Widevine Analysis
        logMediaDrmCapabilities()

        Log.i(TAG, "===================================================================")
    }

    private fun logWebViewFeatures() {
        val features = listOf(
            "USER_AGENT_METADATA" to WebViewFeature.USER_AGENT_METADATA,
            "WEB_MESSAGE_LISTENER" to WebViewFeature.WEB_MESSAGE_LISTENER,
            "GET_WEB_VIEW_RENDERER" to WebViewFeature.GET_WEB_VIEW_RENDERER,
            "SAFE_BROWSING_ENABLE" to WebViewFeature.SAFE_BROWSING_ENABLE
        )
        for ((name, feature) in features) {
            val supported = try {
                WebViewFeature.isFeatureSupported(feature)
            } catch (e: Exception) {
                false
            }
            Log.i(TAG, "[WebViewFeature] $name: $supported")
        }
    }

    private fun logMediaDrmCapabilities() {
        val schemes = listOf(
            "Widevine" to WIDEVINE_UUID,
            "PlayReady" to PLAYREADY_UUID,
            "ClearKey" to CLEARKEY_UUID
        )

        for ((name, uuid) in schemes) {
            val isSupported = try {
                MediaDrm.isCryptoSchemeSupported(uuid)
            } catch (e: Exception) {
                false
            }
            Log.i(TAG, "[MediaDrm] Scheme $name ($uuid): isSupported = $isSupported")

            if (name == "Widevine" && isSupported) {
                queryWidevineDetails(uuid)
            }
        }
    }

    private fun queryWidevineDetails(uuid: UUID) {
        var mediaDrm: MediaDrm? = null
        try {
            mediaDrm = MediaDrm(uuid)
            val vendor = try { mediaDrm.getPropertyString(MediaDrm.PROPERTY_VENDOR) } catch (_: Exception) { "Unknown" }
            val version = try { mediaDrm.getPropertyString(MediaDrm.PROPERTY_VERSION) } catch (_: Exception) { "Unknown" }
            val description = try { mediaDrm.getPropertyString(MediaDrm.PROPERTY_DESCRIPTION) } catch (_: Exception) { "Unknown" }
            val securityLevel = try { mediaDrm.getPropertyString("securityLevel") } catch (_: Exception) { "Unknown" }
            val systemId = try { mediaDrm.getPropertyString("systemId") } catch (_: Exception) { "Unknown" }
            val hdcpLevel = try { mediaDrm.getPropertyString("hdcpLevel") } catch (_: Exception) { "Unknown" }

            Log.i(TAG, "[Widevine Details] Vendor: $vendor")
            Log.i(TAG, "[Widevine Details] Version: $version")
            Log.i(TAG, "[Widevine Details] Description: $description")
            Log.i(TAG, "[Widevine Details] Security Level: $securityLevel")
            Log.i(TAG, "[Widevine Details] System ID: $systemId, HDCP Level: $hdcpLevel")
        } catch (e: Exception) {
            Log.w(TAG, "[Widevine Details] Unable to instantiate MediaDrm instance for Widevine: ${e.message}")
        } finally {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    mediaDrm?.close()
                } else {
                    @Suppress("DEPRECATION")
                    mediaDrm?.release()
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Redacts tokens, passwords, cookies, auth headers to prevent sensitive leakage.
     */
    fun sanitizeLog(message: String): String {
        return message
            .replace(Regex("""(?i)(bearer\s+)[A-Za-z0-9._~+/=-]+"""), "$1[REDACTED]")
            .replace(Regex("""(?i)((?:access_token|refresh_token|id_token|token|password|passwd|secret|session|api[_-]?key)=)[^&\s"'`]+"""), "$1[REDACTED]")
            .replace(Regex("""(?i)(cookie:?\s*)[^;\r\n]+"""), "$1[REDACTED]")
            .replace(Regex("""(?i)(authorization:?\s*)[^\r\n]+"""), "$1[REDACTED]")
            .replace(Regex("""(?i)(set-cookie:?\s*)[^\r\n]+"""), "$1[REDACTED]")
    }
}
