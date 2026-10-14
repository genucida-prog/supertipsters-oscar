package com.supertipsters.oscar

import android.webkit.JavascriptInterface

/**
 * Exposed to the page as `window.AndroidUpdate`.
 *
 * Lets the "Buscar versión nueva" button on the Model screen force a GitHub
 * release check even when the automatic boot check is still inside its
 * cooldown. The work is delegated to [MainActivity.checkForUpdate] so there is
 * a single code path for both the automatic and the manual checks.
 */
class UpdateBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun check() {
        activity.runOnUiThread { activity.checkForUpdate(force = true) }
    }
}
