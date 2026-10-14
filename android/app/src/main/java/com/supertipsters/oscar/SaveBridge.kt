package com.supertipsters.oscar

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * Exposed to the page as `window.AndroidSave`.
 *
 * The prototype produces the combination card with `canvas.toDataURL()`, so the
 * value arriving here is already a `data:image/png;base64,...` URL and the
 * bridge only has to validate and persist it. Methods annotated with
 * [JavascriptInterface] run on a background thread, so every touch of the UI is
 * posted back to the main thread.
 */
class SaveBridge(
    private val activity: Activity,
    private val webView: WebView
) {

    @JavascriptInterface
    fun savePng(name: String?, dataUrl: String?) {
        val fileName = sanitize(name)
        val bytes = decodePngDataUrl(dataUrl)

        val uri = if (bytes == null) null else try {
            write(bytes, fileName)
        } catch (e: Exception) {
            null
        }

        if (uri == null) {
            toast(activity.getString(R.string.image_save_failed))
        } else {
            toast(activity.getString(R.string.image_saved))
        }
        reply(uri != null, if (uri != null) fileName else null)
    }

    private fun write(bytes: ByteArray, fileName: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, MIME)
                // Keep the entry invisible until the bytes are fully written.
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = activity.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }

        // API 24-28: the public Downloads folder needs WRITE_EXTERNAL_STORAGE,
        // requested by MainActivity on first launch for these API levels.
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        activity.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
        return Uri.fromFile(file)
    }

    private fun decodePngDataUrl(dataUrl: String?): ByteArray? {
        if (dataUrl == null) return null
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return null
        val header = dataUrl.substring(0, comma)
        // Only PNG data URLs are accepted; anything else is rejected outright.
        if (!header.startsWith(DATA_URL_PREFIX) || !header.contains("base64")) return null
        return try {
            Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Keeps the name to characters that are safe both as a filename and in JS. */
    private fun sanitize(name: String?): String {
        val cleaned = (name ?: DEFAULT_NAME).replace(UNSAFE_FILENAME_CHARS, "-")
        return if (cleaned.endsWith(".png")) cleaned else "$cleaned.png"
    }

    /**
     * Notifies the page through the optional `window.__odSaveResult` hook.
     * [fileName] has already been restricted to `[A-Za-z0-9._-]`, so embedding it
     * in the call cannot break out of the string literal.
     */
    private fun reply(ok: Boolean, fileName: String?) {
        val nameLiteral = if (fileName == null) "null" else "\"$fileName\""
        val script = "window.__odSaveResult && window.__odSaveResult(${ok.toJsLiteral()}, $nameLiteral)"
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun toast(message: String) {
        activity.runOnUiThread { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val MIME = "image/png"
        const val DATA_URL_PREFIX = "data:image/png;base64"
        const val DEFAULT_NAME = "combinada"
        val UNSAFE_FILENAME_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}

private fun Boolean.toJsLiteral(): String = if (this) "true" else "false"