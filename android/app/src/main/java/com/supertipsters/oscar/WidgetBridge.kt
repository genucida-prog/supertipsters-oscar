package com.supertipsters.oscar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.webkit.JavascriptInterface
import android.widget.RemoteViews
import org.json.JSONObject

/**
 * Exposed to the page as `window.AndroidWidget`.
 *
 * The home-screen widget cannot read the WebView's localStorage, so the page
 * pushes a small pre-rendered snapshot (a few text lines) that is cached in
 * SharedPreferences and drawn by [TipstersWidget] through [WidgetPublisher].
 */
class WidgetBridge(private val context: Context) {

    @JavascriptInterface
    fun update(json: String?) {
        if (json.isNullOrBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SNAPSHOT, json)
            .apply()
        WidgetPublisher.refresh(context)
    }

    companion object {
        const val PREFS = "widget"
        const val KEY_SNAPSHOT = "snapshot"
    }
}

/** Turns the cached snapshot into RemoteViews for every placed widget. */
object WidgetPublisher {

    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TipstersWidget::class.java))
        for (id in ids) { updateOne(context, manager, id) }
    }

    fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
        val raw = context.getSharedPreferences(WidgetBridge.PREFS, Context.MODE_PRIVATE)
            .getString(WidgetBridge.KEY_SNAPSHOT, null)
        val lines = parse(raw)

        val views = RemoteViews(context.packageName, R.layout.widget_tipsters)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))

        val slots = intArrayOf(R.id.widget_line_1, R.id.widget_line_2, R.id.widget_line_3)
        for ((index, slot) in slots.withIndex()) {
            val value = lines.getOrNull(index)
            val hasValue = !value.isNullOrBlank()
            views.setTextViewText(slot, value ?: "")
            views.setViewVisibility(slot, if (hasValue) View.VISIBLE else View.GONE)
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        manager.updateAppWidget(id, views)
    }

    private fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONObject(raw).optJSONArray("lines") ?: return emptyList()
            (0 until array.length())
                .map { array.optString(it, "") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
