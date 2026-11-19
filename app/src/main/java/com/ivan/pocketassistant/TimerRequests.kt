package com.ivan.pocketassistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.util.UUID

/** A single durable pending request; a newer timer replaces an unsubmitted request. */
object TimerRequests {
    data class Request(val id: String, val seconds: Int)
    private fun prefs(context: Context) = context.getSharedPreferences("timer_requests", Context.MODE_PRIVATE)
    fun pending(context: Context): Request? {
        val p = prefs(context)
        val id = p.getString("id", null) ?: return null
        val seconds = p.getInt("seconds", 0)
        return if (seconds in 1..86400) Request(id, seconds) else null
    }
    fun status(context: Context) = prefs(context).getString("status", "").orEmpty()
    fun setStatus(context: Context, message: String) { prefs(context).edit().putString("status", message).apply() }
    fun clockIntent(seconds: Int) = Intent(AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        putExtra(AlarmClock.EXTRA_MESSAGE, "Pocket Assistant")
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
    }
    fun launchIntent(context: Context, request: Request) = Intent(context, TimerActivity::class.java).putExtra("requestId", request.id)

    fun prepare(context: Context, seconds: Int): String {
        if (clockIntent(seconds).resolveActivity(context.packageManager) == null)
            return "No installed clock app can handle timer requests."
        val request = Request(UUID.randomUUID().toString(), seconds)
        val description = TimerCommand.describe(seconds)
        if (!prefs(context).edit().putString("id", request.id).putInt("seconds", seconds)
                .putString("status", "$description timer request pending. It has not been sent to Clock yet.").commit())
            return "I couldn't save the timer request. Please try again."
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("timers", "Timer requests", NotificationManager.IMPORTANCE_DEFAULT))
        if (MainActivity.visible && !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            try {
                context.startActivity(launchIntent(context, request).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Opening Clock for $description. Check Clock to confirm the timer is running."
            } catch (_: RuntimeException) { /* Leave request available through the app and notification. */ }
        }
        val tap = PendingIntent.getActivity(context, 20, launchIntent(context, request), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, "timers")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Start $description timer")
            .setContentText("Tap to send this request to Clock. Timer not started yet.")
            .setContentIntent(tap).setAutoCancel(true).build()
        val canNotify = manager.areNotificationsEnabled() && manager.getNotificationChannel("timers").importance != NotificationManager.IMPORTANCE_NONE
        if (canNotify) manager.notify(20, notification)
        return if (canNotify) "Your $description timer request is ready. Tap the timer notification to send it to Clock."
        else "Your $description timer request is ready. Open Pocket Assistant and tap Start timer in Clock."
    }

    fun consume(context: Context, id: String): Request? {
        val request = pending(context)?.takeIf { it.id == id } ?: return null
        if (!prefs(context).edit().remove("id").remove("seconds").commit()) return null
        context.getSystemService(NotificationManager::class.java).cancel(20)
        return request
    }
}
