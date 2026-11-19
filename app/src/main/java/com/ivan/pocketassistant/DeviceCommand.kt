package com.ivan.pocketassistant

import java.util.Locale

object DeviceCommand {
    sealed interface Action {
        data object None : Action
        data object CancelTimer : Action
        data class Torch(val enabled: Boolean) : Action
        data class OpenApp(val name: String) : Action
    }
    fun normalize(text: String) = text.lowercase(Locale.ROOT).trim().trimEnd('.', '!', '?')
        .replace(Regex("\\s+"), " ").removePrefix("please ").removeSuffix(" please")

    fun parse(input: String): Action {
        val text = normalize(input)
        if (Regex("(?:cancel|stop|delete) (?:the |my )?timer").matches(text)) return Action.CancelTimer
        val light = "(?:torch|flashlight|flash light)"
        if (Regex("(?:(?:open|enable) (?:the )?$light|(?:turn|switch) on (?:the )?$light|(?:turn|switch) (?:the )?$light on|$light on)").matches(text)) return Action.Torch(true)
        if (Regex("(?:(?:close|disable) (?:the )?$light|(?:turn|switch) off (?:the )?$light|(?:turn|switch) (?:the )?$light off|$light off)").matches(text)) return Action.Torch(false)
        val name = Regex("(?:open|launch) (.+)").matchEntire(text)?.groupValues?.get(1)
            ?.removeSuffix(" app")
        return if (name.isNullOrBlank()) Action.None else Action.OpenApp(name)
    }
}
