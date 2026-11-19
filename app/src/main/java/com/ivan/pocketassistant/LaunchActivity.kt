package com.ivan.pocketassistant

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = LaunchRequests.consume(this, intent.getStringExtra("requestId").orEmpty())
        val message = if (request == null) "This request was already opened or replaced." else try {
            val target = LaunchRequests.target(this, request)
            if (target == null) "The requested app is no longer available." else {
                startActivity(target)
                "Opened ${request.label}. ${request.instructions}"
            }
        } catch (_: RuntimeException) { "Could not open ${request.label}. Try opening it from your app drawer." }
        LaunchRequests.setStatus(this, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
