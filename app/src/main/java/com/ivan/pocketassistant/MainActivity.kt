package com.ivan.pocketassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowInsets
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var toggle: Switch
    private val handler = Handler(Looper.getMainLooper())
    private var refreshing = false
    private val refresh = object : Runnable {
        override fun run() {
            status.text = AssistantService.status
            refreshing = true
            toggle.isChecked = AssistantService.running
            refreshing = false
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Remove any key left by version 0.1 when updating in place.
        getPreferences(MODE_PRIVATE).edit().remove("key").apply()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun label(text: String, size: Float = 18f) = TextView(this).apply {
            this.text = text; textSize = size; setPadding(0, 16, 0, 16)
            layout.addView(this)
        }
        label("Pocket Assistant", 30f)
        label("Say Jarvis. Wait for the beep. Then ask a question.")
        label("Version 0.2 · Time, date, greetings and spoken replies", 15f)
        label("No account or API key needed. Wake detection runs on your phone. Command recognition and voices may use your speech provider's network services.", 14f)
        toggle = Switch(this).apply { text = "Enable hands-free listening" }
        layout.addView(toggle)
        status = label(AssistantService.status)
        label("Try: “What time is it?”, “What day is it?”, “Hello”.\n\nTurn listening off here or use Stop in the notification. After restarting the phone, open this app to enable listening again.", 16f)
        val scroll = ScrollView(this).apply { addView(layout) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        toggle.setOnCheckedChangeListener { _, enabled ->
            if (!refreshing) {
                if (enabled) requestStart() else stopService(Intent(this, AssistantService::class.java))
            }
        }
    }

    private fun requestStart() {
        val missing = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1) else startListening()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != 1) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
        else {
            AssistantService.status = "Microphone permission is required. Enable it in app settings to listen."
            toggle.isChecked = false
        }
    }

    private fun startListening() {
        try {
            startForegroundService(Intent(this, AssistantService::class.java))
        } catch (_: RuntimeException) {
            AssistantService.status = "Could not start listening. Keep the app open and check microphone permission."
            toggle.isChecked = false
        }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
