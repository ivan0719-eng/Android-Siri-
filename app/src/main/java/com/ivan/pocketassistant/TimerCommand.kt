package com.ivan.pocketassistant

import java.util.Locale

object TimerCommand {
    sealed interface Result {
        data object NotTimer : Result
        data class Invalid(val message: String) : Result
        data class Valid(val seconds: Int) : Result
    }
    private val small = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
    private const val help = "Please say set a timer for five minutes, or one minute and thirty seconds. Use whole seconds, minutes or hours, up to 24 hours."

    fun parse(input: String): Result {
        val text = input.lowercase(Locale.ROOT).trim().trimEnd('.', '!', '?')
            .replace(Regex("(?<=[a-z])-(?=[a-z])"), " ").replace(Regex("\\s+"), " ")
            .replace(Regex("(?<=[0-9])-(?=[a-z])"), " ")
            .removePrefix("please ").removeSuffix(" please")
        if (!Regex("\\btimers?\\b").containsMatchIn(text)) return Result.NotTimer
        val duration = Regex("(?:set|start) (?:a |the )?timer (?:for )?(.+)").matchEntire(text)?.groupValues?.get(1)
            ?: Regex("(?:set|start) (?:a )?(.+) timer").matchEntire(text)?.groupValues?.get(1)
            ?: return Result.Invalid(help)
        val pieces = Regex("([a-z0-9 ]+?) (hours?|hrs?|minutes?|mins?|seconds?|secs?)(?: and | |$)")
        var remaining = duration
        var total = 0L
        val used = mutableSetOf<Int>()
        while (remaining.isNotEmpty()) {
            val match = pieces.find(remaining)?.takeIf { it.range.first == 0 } ?: return Result.Invalid(help)
            val amount = number(match.groupValues[1]) ?: return Result.Invalid(help)
            val unit = match.groupValues[2]
            val multiplier = if (unit.startsWith("h")) 3600 else if (unit.startsWith("m")) 60 else 1
            if (!used.add(multiplier)) return Result.Invalid(help)
            total += amount.toLong() * multiplier
            if (total > 86400) return Result.Invalid(help)
            remaining = remaining.substring(match.range.last + 1)
        }
        return if (total in 1..86400) Result.Valid(total.toInt()) else Result.Invalid(help)
    }

    private fun number(text: String): Int? {
        text.toIntOrNull()?.let { return it.takeIf { n -> n in 0..86400 } }
        if (text == "a" || text == "an") return 1
        small.indexOf(text).takeIf { it >= 0 }?.let { return it }
        tens[text]?.let { return it }
        val parts = text.split(' ')
        return if (parts.size == 2 && tens.containsKey(parts[0]) && small.indexOf(parts[1]) in 1..9)
            tens.getValue(parts[0]) + small.indexOf(parts[1]) else null
    }

    fun describe(seconds: Int): String = listOf(seconds / 3600 to "hour", seconds % 3600 / 60 to "minute", seconds % 60 to "second")
        .filter { it.first > 0 }.joinToString(" and ") { (n, unit) -> "$n $unit${if (n == 1) "" else "s"}" }
}
