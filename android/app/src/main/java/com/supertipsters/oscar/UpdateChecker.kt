package com.supertipsters.oscar

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the newest GitHub Release of this repository and installs it.
 *
 * The check runs once per app launch (with a cooldown so the anonymous GitHub
 * API quota is not burned): the `tag_name` of `releases/latest` is parsed as the
 * `versionCode` of the published build and compared with the installed one. A
 * higher tag means a newer APK is available.
 *
 * The download lands in the app cache and is handed to the package installer
 * through [FileProvider]; Android then asks the user to allow this source for
 * unknown apps, which is the only supported install path outside the Play
 * Store. Nothing here touches the WebView payload.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val API_URL =
        "https://api.github.com/repos/genucida-prog/supertipsters-oscar/releases/latest"
    private const val FILE_PROVIDER_AUTHORITY = "com.supertipsters.oscar.fileprovider"
    private const val COOLDOWN_MS = 30 * 60 * 1000L

    /** [tagCode] is the versionCode published as the release tag. */
    class Latest(val tagCode: Int, val versionName: String, val notes: String, val apkUrl: String)

    fun currentVersionCode(context: Context): Int {
        val info: PackageInfo =
            context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    /** True when the last check is still inside the cooldown window. */
    fun checkedRecently(context: Context): Boolean {
        val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_check", 0L)
        val now = System.currentTimeMillis()
        // Never skip because the device clock moved backwards.
        if (now < last) return false
        return now - last < COOLDOWN_MS
    }

    fun markChecked(context: Context) {
        context.getSharedPreferences("update", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_check", System.currentTimeMillis())
            .apply()
    }

    /** Fetches `releases/latest`; returns null when unavailable or not newer. */
    fun fetchLatest(currentCode: Int): Latest? {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SuperTipstersOscar-Updater")
        }
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagCode = json.optString("tag_name", "").toIntOrNull() ?: return null
            if (tagCode <= currentCode) return null
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            if (apkUrl == null) return null
            return Latest(
                tagCode = tagCode,
                versionName = json.optString("name", "v$tagCode"),
                notes = json.optString("body", ""),
                apkUrl = apkUrl
            )
        } catch (e: Exception) {
            // A missing connection must never block the app from starting.
            return null
        } finally {
            connection.disconnect()
        }
    }

    /** Streams the release asset into the cache; returns null on any failure. */
    fun downloadApk(context: Context, apkUrl: String, onBytes: (Long) -> Unit): File? {
        val target = File(context.cacheDir, "update.apk")
        if (target.exists() && !target.delete()) return null
        val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "SuperTipstersOscar-Updater")
        }
        return try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) return null
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        total += read
                        onBytes(total)
                    }
                }
            }
            if (total == 0L) null else target
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Intent that opens the system installer for [apk]. */
    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** True when this app may install packages on this device. */
    fun canInstall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /** Opens the per-app "install unknown apps" switch when the permission is missing. */
    fun installPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            @Suppress("DEPRECATION")
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun deleteDownloaded(context: Context) {
        val stale = File(context.cacheDir, "update.apk")
        if (stale.exists()) stale.delete()
    }
}
