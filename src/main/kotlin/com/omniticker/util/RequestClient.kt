package com.omniticker.util

import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.Charset

/**
 * Thin HTTP wrapper. Uses the platform [HttpRequests] so the IDE's proxy
 * settings and trust store apply automatically (critical for users behind
 * a corporate proxy in China).
 *
 * Never throws: network failures surface as null so callers can degrade.
 */
object RequestClient {
    private val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    /** Fetch a UTF-8 body, or null on any I/O failure. */
    suspend fun getText(url: String, referer: String? = null, timeoutMs: Int = 8000): String? =
        getBytes(url, referer, timeoutMs)?.let { String(it, Charsets.UTF_8) }

    /** Fetch a GBK body (Tencent's quote feed is GB2312/GBK bytes). */
    suspend fun getTextGbk(url: String, referer: String? = null, timeoutMs: Int = 8000): String? =
        getBytes(url, referer, timeoutMs)?.let { String(it, Charset.forName("GBK")) }

    /**
     * UTF-8 first, GBK fallback: some free quote endpoints (Sina documented
     * as GBK over the years) serve either charset depending on the CDN; the
     * U+FFFD replacement char signals a UTF-8 mis-decode and triggers the
     * GBK retry.
     */
    suspend fun getTextSmart(url: String, referer: String? = null, timeoutMs: Int = 8000): String? {
        val bytes = getBytes(url, referer, timeoutMs) ?: return null
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, Charset.forName("GBK")) else utf8
    }

    suspend fun getBytes(url: String, referer: String? = null, timeoutMs: Int = 8000): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                HttpRequests.request(url)
                    .connectTimeout(timeoutMs)
                    .readTimeout(timeoutMs)
                    .userAgent(BROWSER_UA)
                    .tuner { connection ->
                        connection.setRequestProperty("Referer", referer ?: "https://quote.eastmoney.com/")
                    }
                    .connect { req: HttpRequests.Request -> req.inputStream.readBytes() }
            } catch (_: IOException) {
                null
            } catch (_: RuntimeException) {
                null
            }
        }
}