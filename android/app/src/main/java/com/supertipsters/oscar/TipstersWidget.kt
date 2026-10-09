package com.supertipsters.oscar

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * Home-screen widget showing the next match and the saved forecasts. The data
 * arrives from the WebView (see [WidgetBridge]) and is cached locally, so the
 * widget keeps its last known content even when the app is not running.
 */
class TipstersWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) { WidgetPublisher.updateOne(context, manager, id) }
    }
}
