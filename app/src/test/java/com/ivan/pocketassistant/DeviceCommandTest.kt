package com.ivan.pocketassistant

import org.junit.Assert.*
import org.junit.Test

class DeviceCommandTest {
    @Test fun recognizesTimerCancellationWithoutTouchingAlarmCommands() {
        for (text in listOf("cancel the timer", "stop my timer", "Please cancel timer."))
            assertEquals(DeviceCommand.Action.CancelTimer, DeviceCommand.parse(text))
        for (text in listOf("do not cancel the timer", "cancel all timers", "cancel my alarm"))
            assertEquals(DeviceCommand.Action.None, DeviceCommand.parse(text))
    }
    @Test fun recognizesTorchDirectionsBeforeAppNames() {
        for (text in listOf("open torch", "turn on the flashlight", "switch torch on", "flash light on"))
            assertEquals(DeviceCommand.Action.Torch(true), DeviceCommand.parse(text))
        for (text in listOf("close torch", "turn off the flashlight", "switch torch off", "torch off"))
            assertEquals(DeviceCommand.Action.Torch(false), DeviceCommand.parse(text))
        assertEquals(DeviceCommand.Action.None, DeviceCommand.parse("do not turn on the torch"))
    }
    @Test fun extractsExactAppNameWithoutGuessingCompoundCommands() {
        assertEquals(DeviceCommand.Action.OpenApp("spotify"), DeviceCommand.parse("Please open Spotify app."))
        assertEquals(DeviceCommand.Action.OpenApp("instagram"), DeviceCommand.parse("launch Instagram"))
        assertEquals(DeviceCommand.Action.OpenApp("spotify and instagram"), DeviceCommand.parse("open Spotify and Instagram"))
        assertEquals(DeviceCommand.Action.None, DeviceCommand.parse("what is Spotify"))
    }
}
