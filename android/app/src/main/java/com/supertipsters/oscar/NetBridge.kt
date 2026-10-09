package com.supertipsters.oscar

import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Exposed to the page as `window.AndroidNet`.
 *
 * The updater in `index.html` owns the whole refresh policy (which endpoints,
 * which tokens, how to merge the payload); this bridge only performs the HTTP
 * GET that a WebView page cannot do itself, because football-data.org restricts
 * CORS to `http://localhost` and the page runs on
 * `https://appassets.androidplatform.net`.
 *
 * [get] never blocks the JavaScript thread: it returns immediately, runs the
 * request on a small pool and, when it finishes, calls the page back through
 * `window.__odNetResult(requestId, {status, body, error})`. The id is emitted as
 * a JSON string literal so it cannot break out of the callback.
 *
 * Only `https` requests to an allow-listed host are accepted; the token travels
 * as a single header the caller chooses (football-data.org uses `X-Auth-Token`).
 */
class NetBridge(private val webView: WebView) {

    private val pool = Executors.newFixedThreadPool(3)

    @JavascriptInterface
    fun get(requestId: String?, url: String?, headerName: String?, headerValue: String?) {
        val id = requestId ?: return
        val target = url ?: return
        pool.execute {
            val result = try {
                val parsed = URL(target)
                if (parsed.protocol != "https" || parsed.host !in ALLOWED_HOSTS) {
                    failure("host no permitido: ${parsed.host}")
                } else {
                    request(parsed, headerName, headerValue)
                }
            } catch (e: Exception) {
                failure(e.message ?: "error de red")
            }
            reply(id, result)
        }
    }

    private fun request(parsed: URL, headerName: String?, headerValue: String?): JSONObject {
        val conn = (parsed.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("Accept", "application/json")
            if (!headerName.isNullOrEmpty() && !headerValue.isNullOrEmpty()) {
                setRequestProperty(headerName, headerValue)
            }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            JSONObject().put("status", code).put("body", body)
        } finally {
            conn.disconnect()
        }
    }

    private fun failure(message: String): JSONObject =
        JSONObject().put("status", 0).put("body", "").put("error", message)

    private fun reply(id: String, result: JSONObject) {
        val script = "window.__odNetResult && window.__odNetResult(${JSONObject.quote(id)}, $result)"
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private companion object {
        val ALLOWED_HOSTS = setOf(
            "api.football-data.org",
            "api.the-odds-api.com",
        )
    }
}
