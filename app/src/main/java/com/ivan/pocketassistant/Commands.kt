package com.ivan.pocketassistant

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Commands {
    fun reply(input: String, now: ZonedDateTime = ZonedDateTime.now()): String {
        val words = input.lowercase(Locale.ROOT).replace(Regex("[^a-z ]"), " ")
            .trim().replace(Regex("\\s+"), " ")
        return when (words) {
            "what time is it", "what is the time", "tell me the time" ->
                "It's ${now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))}."
            "what is the date", "what date is it", "what day is it", "what is todays date" ->
                "Today is ${now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))}."
            "hello", "hi", "hello jarvis" -> "Hello! I'm listening."
            "help", "what can you do" -> "Try asking what time it is, today's date, or say hello."
            else -> "I heard: $input. For now, try asking the time or date, or say hello."
        }
    }
}
