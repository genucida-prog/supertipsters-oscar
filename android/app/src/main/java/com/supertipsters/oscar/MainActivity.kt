package com.supertipsters.oscar

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

/**
 * Hosts the prototype in a WebView.
 *
 * The page is served from `https://appassets.androidplatform.net/...` by
 * [WebViewAssetLoader] rather than loaded from `file:///android_asset/`. That is
 * deliberate: the asset loader satisfies every asset request locally and a real
 * https origin is what keeps `localStorage` (the saved-predictions store)
 * working.
 *
 * The optional in-app updater needs to reach football-data.org and The Odds
 * API. Those calls are made here in [NetBridge] instead of in the page because
 * football-data.org only authorises `http://localhost` through CORS, so a
 * `fetch()` from the WebView origin would be blocked. Everything the bundled
 * snapshot already contains keeps working with no key and no connection.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    /** Release the user chose to install, held while the settings switch is open. */
    private var pendingUpdate: UpdateChecker.Latest? = null

    /**
     * Only relevant on API 24-28, where writing into the public Downloads
     * folder needs the legacy storage permission. On API 29+ the bridge uses
     * MediaStore and scoped storage makes the permission irrelevant.
     */
    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.storage_permission_needed, Toast.LENGTH_LONG).show()
            }
        }

    /** API 33+ needs the runtime permission before the round reminder can post. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.notif_permission_needed, Toast.LENGTH_LONG).show()
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestLegacyStorageIfNeeded()
        requestNotificationIfNeeded()

        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(ASSET_DOMAIN)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(BACKGROUND_COLOR)
            overScrollMode = WebView.OVER_SCROLL_NEVER
        }
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            // Saved forecasts live in localStorage; without this they are lost
            // on every restart.
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            // Assets are served through the loader, not the filesystem.
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.addJavascriptInterface(SaveBridge(this, webView), BRIDGE_NAME)
        webView.addJavascriptInterface(NetBridge(webView), NET_BRIDGE_NAME)
        webView.addJavascriptInterface(UpdateBridge(this), UPDATE_BRIDGE_NAME)
        webView.addJavascriptInterface(NotifyBridge(this), NOTIFY_BRIDGE_NAME)
        webView.addJavascriptInterface(WidgetBridge(this), WIDGET_BRIDGE_NAME)

        webView.webViewClient = object : WebViewClientCompat() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                // Anything that is not our own asset origin is handed off to a
                // real app (WhatsApp, Telegram, ...).
                return if (url.host == ASSET_DOMAIN) false else openExternally(url)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.loadUrl(START_URL)

        checkForUpdate()
    }

    /**
     * Asks GitHub for the newest release and, when its tag is higher than the
     * installed `versionCode`, offers to download and install it. Runs on a
     * background thread so the WebView boot never waits for the network, and
     * silently does nothing when offline or rate-limited.
     *
     * [force] is set by the manual "Buscar versión nueva" button: it skips the
     * boot cooldown and confirms when the installed build is already current.
     */
    fun checkForUpdate(force: Boolean = false) {
        if (!force && UpdateChecker.checkedRecently(this)) return
        UpdateChecker.markChecked(this)
        val current = UpdateChecker.currentVersionCode(this)
        Thread {
            val latest = UpdateChecker.fetchLatest(current)
            runOnUiThread {
                if (latest != null) {
                    showUpdateDialog(latest)
                } else if (force) {
                    Toast.makeText(this, R.string.update_up_to_date, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showUpdateDialog(latest: UpdateChecker.Latest) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(R.string.update_title)
            .setMessage(getString(R.string.update_message, latest.versionName))
            .setPositiveButton(R.string.update_now) { _, _ -> startUpdate(latest) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun startUpdate(latest: UpdateChecker.Latest) {
        pendingUpdate = latest
        if (!UpdateChecker.canInstall(this)) {
            // The user has to flip the per-app switch first; onResume retries.
            startActivity(UpdateChecker.installPermissionIntent(this))
        } else {
            downloadAndInstall(pendingUpdate ?: return)
        }
    }

    private fun downloadAndInstall(latest: UpdateChecker.Latest) {
        pendingUpdate = null
        Toast.makeText(this, R.string.update_downloading, Toast.LENGTH_SHORT).show()
        Thread {
            val file = UpdateChecker.downloadApk(this, latest.apkUrl) { }
            runOnUiThread {
                if (file == null) {
                    Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()
                } else {
                    try {
                        startActivity(UpdateChecker.installIntent(this, file))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    override fun onResume() {
        webView.onResume()
        // Returning from the "unknown apps" switch: continue the pending update.
        val pending = pendingUpdate
        if (pending != null && UpdateChecker.canInstall(this)) downloadAndInstall(pending)
        super.onResume()
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.removeJavascriptInterface(NET_BRIDGE_NAME)
        webView.removeJavascriptInterface(UPDATE_BRIDGE_NAME)
        webView.removeJavascriptInterface(NOTIFY_BRIDGE_NAME)
        webView.removeJavascriptInterface(WIDGET_BRIDGE_NAME)
        webView.destroy()
        super.onDestroy()
    }

    private fun requestLegacyStorageIfNeeded() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Returns true when navigation was consumed, so the WebView stays put. */
    private fun openExternally(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
            true
        }
    }

    private companion object {
        const val ASSET_DOMAIN = "appassets.androidplatform.net"
        const val START_URL = "https://appassets.androidplatform.net/assets/www/index.html"
        const val BRIDGE_NAME = "AndroidSave"
        const val NET_BRIDGE_NAME = "AndroidNet"
        const val UPDATE_BRIDGE_NAME = "AndroidUpdate"
        const val NOTIFY_BRIDGE_NAME = "AndroidNotify"
        const val WIDGET_BRIDGE_NAME = "AndroidWidget"
        const val BACKGROUND_COLOR = 0xFF090C11.toInt()
    }
}