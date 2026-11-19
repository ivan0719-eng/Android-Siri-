package com.ivan.pocketassistant

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Commands {
    fun reply(input: String, now: ZonedDateTime = ZonedDateTime.now()): String {
        val words = input.lowercase(Locale.ROOT).replace("'", "").replace("’", "").replace(Regex("[^a-z ]"), " ")
            .trim().replace(Regex("\\s+"), " ")
        return when (words) {
            "what time is it", "what is the time", "whats the time", "tell me the time" ->
                "It's ${now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))}."
            "what is the date", "whats the date", "what date is it", "what day is it", "what is the day", "what is todays date", "whats todays date", "what day is it today" ->
                "Today is ${now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))}."
            "hello", "hi", "hello jarvis" -> "Hello! I'm listening."
            "help", "what can you do" -> "Ask the time or date, say hello, or say set a timer for five minutes."
            else -> "I heard: $input. Try asking the time or date, or say set a timer for five minutes."
        }
    }
}
