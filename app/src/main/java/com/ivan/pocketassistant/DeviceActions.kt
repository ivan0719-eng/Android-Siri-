package com.ivan.pocketassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock

object DeviceActions {
    fun execute(context: Context, action: DeviceCommand.Action): String? = when (action) {
        DeviceCommand.Action.None -> null
        DeviceCommand.Action.CancelTimer -> {
            if (TimerRequests.pending(context) != null) {
                if (TimerRequests.cancelPending(context)) "Cancelled the pending timer request. Any timer already running in Clock is unchanged."
                else "I couldn't cancel the pending request. Please try again."
            } else {
                val clock = Intent(AlarmClock.ACTION_SHOW_TIMERS)
                if (clock.resolveActivity(context.packageManager) == null) "Please open Clock and stop the timer there. This clock app doesn't support opening its timer list."
                else LaunchRequests.prepare(context, "timers", "Clock timers", "", "Stop the running timer in Clock. I cannot confirm cancellation.")
            }
        }
        is DeviceCommand.Action.Torch -> torch(context, action.enabled)
        is DeviceCommand.Action.OpenApp -> openApp(context, action.name)
    }

    private fun openApp(context: Context, requested: String): String {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = context.packageManager.queryIntentActivities(launcher, 0)
        val alias = mapOf("spotify" to "com.spotify.music", "instagram" to "com.instagram.android", "insta" to "com.instagram.android")[requested]
        val matches = apps.filter {
            if (alias != null) it.activityInfo.packageName == alias
            else DeviceCommand.normalize(it.loadLabel(context.packageManager).toString()) == requested
        }.distinctBy { it.activityInfo.packageName }
        if (matches.isEmpty()) return "I couldn't find an installed app named $requested. Try its name as shown in your app drawer."
        if (matches.size > 1) return "More than one app has that name. Please open the one you want from your app drawer."
        val app = matches.single()
        return LaunchRequests.prepare(context, "app", app.loadLabel(context.packageManager).toString(), app.activityInfo.packageName, "")
    }

    private fun torch(context: Context, enabled: Boolean): String {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            return "To use the torch, open Pocket Assistant and tap Enable torch permission, then allow camera access."
        return try {
            val manager = context.getSystemService(CameraManager::class.java)
            val camera = manager.cameraIdList.firstOrNull {
                val properties = manager.getCameraCharacteristics(it)
                properties.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    properties.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return "This device has no available rear torch."
            manager.setTorchMode(camera, enabled)
            if (enabled) "Torch switched on." else "Torch switched off."
        } catch (_: SecurityException) {
            "Camera permission is unavailable. Check the app's camera permission and phone privacy settings."
        } catch (_: Exception) {
            "The torch is unavailable. Close any app using the camera, then try again."
        }
    }
}
