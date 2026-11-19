package com.ivan.pocketassistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.util.UUID

/** One pending app/Clock navigation request, separate from pending timer creation. */
object LaunchRequests {
    data class Request(val id: String, val kind: String, val label: String, val packageName: String, val instructions: String)
    private fun prefs(context: Context) = context.getSharedPreferences("launch_requests", Context.MODE_PRIVATE)
    fun pending(context: Context): Request? {
        val p = prefs(context)
        val id = p.getString("id", null) ?: return null
        val kind = p.getString("kind", "").orEmpty()
        if (kind !in listOf("app", "timers")) return null
        return Request(id, kind, p.getString("label", "").orEmpty(), p.getString("package", "").orEmpty(), p.getString("instructions", "").orEmpty())
    }
    fun status(context: Context) = prefs(context).getString("status", "").orEmpty()
    fun setStatus(context: Context, text: String) { prefs(context).edit().putString("status", text).apply() }
    fun launchIntent(context: Context, request: Request) = Intent(context, LaunchActivity::class.java).putExtra("requestId", request.id)
    fun target(context: Context, request: Request): Intent? = when (request.kind) {
        "timers" -> Intent(AlarmClock.ACTION_SHOW_TIMERS).takeIf { it.resolveActivity(context.packageManager) != null }
        "app" -> context.packageManager.getLaunchIntentForPackage(request.packageName)
        else -> null
    }
    fun prepare(context: Context, kind: String, label: String, packageName: String, instructions: String): String {
        val request = Request(UUID.randomUUID().toString(), kind, label, packageName, instructions)
        if (target(context, request) == null) return "I couldn't find an app to open $label."
        if (!prefs(context).edit().putString("id", request.id).putString("kind", kind).putString("label", label)
                .putString("package", packageName).putString("instructions", instructions)
                .putString("status", "Ready to open $label. $instructions").commit()) return "I couldn't save the request. Please try again."
        if (MainActivity.visible && !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            try {
                context.startActivity(launchIntent(context, request).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Opening $label. $instructions"
            } catch (_: RuntimeException) { }
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("actions", "App and Clock requests", NotificationManager.IMPORTANCE_DEFAULT))
        val tap = PendingIntent.getActivity(context, 30, launchIntent(context, request), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (manager.areNotificationsEnabled() && manager.getNotificationChannel("actions").importance != NotificationManager.IMPORTANCE_NONE) {
            try {
                manager.notify(30, Notification.Builder(context, "actions").setSmallIcon(android.R.drawable.ic_menu_view)
                    .setContentTitle("Open $label").setContentText("Tap to continue. $instructions")
                    .setContentIntent(tap).setAutoCancel(true).build())
                return "Tap the notification to open $label. $instructions"
            } catch (_: SecurityException) { }
        }
        return "Open Pocket Assistant and tap Open requested app. $instructions"
    }
    fun consume(context: Context, id: String): Request? {
        val request = pending(context)?.takeIf { it.id == id } ?: return null
        if (!prefs(context).edit().remove("id").commit()) return null
        context.getSystemService(NotificationManager::class.java).cancel(30)
        return request
    }
}
