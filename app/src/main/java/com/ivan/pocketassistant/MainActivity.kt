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
    companion object { @Volatile var visible = false }
    private lateinit var status: TextView
    private lateinit var toggle: Switch
    private lateinit var sensitivity: Switch
    private lateinit var timerButton: Button
    private lateinit var timerStatus: TextView
    private lateinit var launchButton: Button
    private lateinit var launchStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var refreshing = false
    private val refresh = object : Runnable {
        override fun run() {
            status.text = AssistantService.status + if (AssistantService.diagnostics.isBlank()) "" else "\n\nDiagnostics:\n${AssistantService.diagnostics}"
            timerButton.isEnabled = TimerRequests.pending(this@MainActivity) != null
            timerStatus.text = TimerRequests.status(this@MainActivity)
            launchButton.isEnabled = LaunchRequests.pending(this@MainActivity) != null
            launchStatus.text = LaunchRequests.status(this@MainActivity)
            refreshing = true
            toggle.isChecked = AssistantService.running
            sensitivity.isEnabled = !AssistantService.running
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
        label("Version 0.6 · Timers, apps and torch", 15f)
        label("No account or API key needed. Wake detection runs on your phone. Command recognition and voices may use your speech provider's network services.", 14f)
        sensitivity = Switch(this).apply {
            text = "More sensitive Jarvis detection"
            isChecked = getSharedPreferences("assistant", MODE_PRIVATE).getBoolean("sensitive", true)
            setOnCheckedChangeListener { _, enabled ->
                getSharedPreferences("assistant", MODE_PRIVATE).edit().putBoolean("sensitive", enabled).apply()
            }
        }
        layout.addView(sensitivity)
        label("Turn listening off to change sensitivity. If Jarvis wakes accidentally, disable the setting above. After a retry prompt, wait for the beep and repeat your command—no need to say Jarvis again.", 14f)
        toggle = Switch(this).apply { text = "Enable hands-free listening" }
        layout.addView(toggle)
        layout.addView(Button(this).apply {
            text = "Test command recognition"
            setOnClickListener {
                if (AssistantService.running) startService(Intent(this@MainActivity, AssistantService::class.java).setAction("TEST_COMMAND"))
                else Toast.makeText(this@MainActivity, "Enable listening and wait for Listening for Jarvis first.", Toast.LENGTH_LONG).show()
            }
        })
        label("For testing, keep this screen open and turn up media volume. The button above skips Jarvis and checks command recognition directly. Wait for Speak now before talking.", 14f)
        status = label(AssistantService.status)
        timerStatus = label("", 16f)
        timerButton = Button(this).apply {
            text = "Start timer in Clock"
            setOnClickListener {
                TimerRequests.pending(this@MainActivity)?.let { startActivity(TimerRequests.launchIntent(this@MainActivity, it)) }
            }
        }
        layout.addView(timerButton)
        launchStatus = label("", 16f)
        launchButton = Button(this).apply {
            text = "Open requested app"
            setOnClickListener { LaunchRequests.pending(this@MainActivity)?.let { startActivity(LaunchRequests.launchIntent(this@MainActivity, it)) } }
        }
        layout.addView(launchButton)
        layout.addView(Button(this).apply {
            text = "Enable torch permission"
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    Toast.makeText(this@MainActivity, "Torch permission is already enabled.", Toast.LENGTH_SHORT).show()
                else requestPermissions(arrayOf(Manifest.permission.CAMERA), 2)
            }
        })
        label("Try: “Open Spotify”, “Open Instagram”, “Open torch”, “Turn off the torch”, or “Cancel the timer”. Camera permission is used only to control the light; the app does not capture photos or video. Running Clock timers must be stopped in Clock.", 16f)
        label("Try: “Set a timer for five minutes” or “Set a timer for one minute and thirty seconds”. When this app is in the background, tap the timer notification to send the request to Clock. Check Clock to confirm it is running.", 16f)
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
        if (code == 2) {
            Toast.makeText(this, if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                "Torch enabled. Say Jarvis, then open torch."
            else "Torch needs camera permission. You can allow it in the app's settings; other commands still work.", Toast.LENGTH_LONG).show()
            return
        }
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

    override fun onResume() { super.onResume(); visible = true; handler.post(refresh) }
    override fun onPause() { visible = false; handler.removeCallbacks(refresh); super.onPause() }
}
