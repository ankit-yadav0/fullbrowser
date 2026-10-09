package com.example.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads inside the app process (never DownloadManager) so the process-level VPN binding applies.
 * [isNetworkReady] is consulted before EVERY connection (including each redirect hop) and between
 * every 16 KiB chunk while writing; a partial file is deleted if the gate closes or anything fails.
 */
object PrivateDownloader {
    private const val CHUNK = 16 * 1024

    suspend fun download(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        referer: String? = null,
        isNetworkReady: () -> Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            DownloadPolicy.validateHop(url)
            var target = url
            var redirects = 0
            while (true) {
                require(isNetworkReady()) { "VPN disconnected" }
                DownloadPolicy.validateHop(target)
                val credentialsOk = DownloadPolicy.mayAttachCredentials(url, target)
                val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    requestMethod = "GET"
                    userAgent?.takeIf { it.isNotBlank() }?.let { setRequestProperty("User-Agent", it) }
                    if (credentialsOk) {
                        CookieManager.getInstance().getCookie(target)?.takeIf { it.isNotBlank() }
                            ?.let { setRequestProperty("Cookie", it) }
                        DownloadPolicy.refererFor(referer, target)?.let { setRequestProperty("Referer", it) }
                    }
                    setRequestProperty("Accept", "*/*")
                }
                var keepOpen = false
                try {
                    connection.connect()
                    require(isNetworkReady()) { "VPN disconnected" }
                    when (val code = connection.responseCode) {
                        in 200..299 -> {
                            keepOpen = true // writeResponse owns disconnect
                            return@runCatching writeResponse(context, connection, target, contentDisposition, mimeType, isNetworkReady)
                        }
                        301, 302, 303, 307, 308 -> {
                            require(redirects < DownloadPolicy.MAX_REDIRECTS) { "Too many redirects" }
                            redirects++
                            target = DownloadPolicy.nextHop(target, connection.getHeaderField("Location"))
                        }
                        else -> error("Download failed: HTTP $code")
                    }
                } finally {
                    if (!keepOpen) connection.disconnect()
                }
            }
            error("unreachable")
        }
    }

    private suspend fun writeResponse(
        context: Context,
        connection: HttpURLConnection,
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        isNetworkReady: () -> Boolean
    ): String {
        val lastSegment = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        val fileName = DownloadPolicy.safeFileName(
            DownloadPolicy.parseContentDisposition(contentDisposition)
                ?: DownloadPolicy.parseContentDisposition(connection.getHeaderField("Content-Disposition"))
                ?: lastSegment.ifBlank { "download" }
        )
        val type = DownloadPolicy.cleanMimeType(mimeType, connection.contentType)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, type)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val item = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create download")
                try {
                    val out = resolver.openOutputStream(item) ?: error("Unable to open download")
                    out.use { copyGuarded(connection.inputStream, it, isNetworkReady) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(item, values, null, null)
                    return item.toString()
                } catch (t: Throwable) {
                    runCatching { resolver.delete(item, null, null) }
                    throw t
                }
            }
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("Download directory unavailable")
            val file = uniqueFile(File(dir, fileName))
            try {
                FileOutputStream(file).use { out -> copyGuarded(connection.inputStream, out, isNetworkReady) }
                return file.absolutePath
            } catch (t: Throwable) {
                file.delete()
                throw t
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun copyGuarded(input: InputStream, out: OutputStream, isNetworkReady: () -> Boolean) {
        val ctx = currentCoroutineContext()
        input.use { src ->
            val buf = ByteArray(CHUNK)
            while (true) {
                ctx.ensureActive()
                require(isNetworkReady()) { "VPN disconnected" }
                val n = src.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        }
    }

    private fun uniqueFile(file: File): File {
        if (!file.exists()) return file
        val base = file.nameWithoutExtension
        val ext = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        for (i in 1..999) {
            val candidate = File(file.parentFile, "$base ($i)$ext")
            if (!candidate.exists()) return candidate
        }
        error("Unable to allocate filename")
    }
}
