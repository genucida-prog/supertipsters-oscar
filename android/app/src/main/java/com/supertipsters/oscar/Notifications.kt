package com.supertipsters.oscar

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Exposed to the page as `window.AndroidNotify`.
 *
 * The matchday calendar lives in the WebView, so the page computes when the
 * current round ends and asks the native side to remember a single reminder.
 * An [AlarmManager] alarm is enough; no background-work dependency is needed.
 */
class NotifyBridge(private val context: Context) {

    @JavascriptInterface
    fun schedule(atMillis: Long, title: String?, text: String?) {
        NotificationScheduler.schedule(context, atMillis, title ?: "", text ?: "")
    }
}

/** Owns the notification channel and the pending "round finished" alarm. */
object NotificationScheduler {

    const val CHANNEL_ID = "matchday"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_TEXT = "extra_text"
    const val REQUEST_CODE = 4201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    /** Replaces any previous alarm; a date in the past cancels the reminder. */
    fun schedule(context: Context, atMillis: Long, title: String, text: String) {
        if (atMillis <= System.currentTimeMillis()) return
        ensureChannel(context)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotifyReceiver::class.java)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.set(AlarmManager.RTC_WAKEUP, atMillis, pending)
    }
}

/** Posts the scheduled reminder; does nothing while notifications are blocked. */
class NotifyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_TITLE) ?: return
        val text = intent.getStringExtra(NotificationScheduler.EXTRA_TEXT) ?: ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationScheduler.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationScheduler.REQUEST_CODE, notification)
    }
}
