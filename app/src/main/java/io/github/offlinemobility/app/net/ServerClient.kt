package io.github.offlinemobility.app.net

import io.github.offlinemobility.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal HTTP client for the rescue server.
 *
 * Release builds accept only https:// base URLs (cleartext is also blocked by the network security
 * config). Debug builds additionally accept http://10.0.2.2, the emulator's alias for the
 * developer machine, so the local server can be used. Redirects are never followed and responses
 * are size-capped.
 */
class ServerClient(baseUrl: String) {
    private val base = baseUrl.trim().trimEnd('/')

    val enabled: Boolean = base.startsWith("https://") ||
        (BuildConfig.DEBUG && Regex("^http://10\\.0\\.2\\.2(:\\d+)?$").matches(base))

    /** Blocking GET; returns the body on 200, otherwise null. */
    fun get(path: String, params: Map<String, String> = emptyMap()): String? {
        if (!enabled) return null
        val query = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        return runCatching {
            val connection = open(if (query.isEmpty()) path else "$path?$query")
            try {
                connection.requestMethod = "GET"
                if (connection.responseCode != 200) return@runCatching null
                connection.inputStream.use { stream ->
                    val bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1)
                    if (bytes.size > MAX_RESPONSE_BYTES) null else bytes.toString(Charsets.UTF_8)
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** Blocking POST; true on 2xx or 409 (already stored). */
    fun postJson(path: String, body: String): Boolean {
        if (!enabled) return false
        return runCatching {
            val connection = open(path)
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                connection.responseCode in 200..299 || connection.responseCode == HttpURLConnection.HTTP_CONFLICT
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    private fun open(pathAndQuery: String): HttpURLConnection =
        (URL(base + pathAndQuery).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            useCaches = false
        }

    private companion object {
        const val MAX_RESPONSE_BYTES = 512 * 1024
    }
}
