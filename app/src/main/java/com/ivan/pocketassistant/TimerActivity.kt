package com.ivan.pocketassistant

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** A user-tapped activity can launch Clock even when the listening service is in the background. */
class TimerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = TimerRequests.consume(this, intent.getStringExtra("requestId").orEmpty())
        if (request == null) {
            Toast.makeText(this, "This timer request was already sent or replaced.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val message = try {
            startActivity(TimerRequests.clockIntent(request.seconds))
            "Sent ${TimerCommand.describe(request.seconds)} to Clock. Check Clock to confirm it is running."
        } catch (_: RuntimeException) {
            "Could not open Clock. No timer is confirmed. Please ask again."
        }
        TimerRequests.setStatus(this, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
