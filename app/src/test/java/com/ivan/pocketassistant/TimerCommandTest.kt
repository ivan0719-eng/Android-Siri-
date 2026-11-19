package com.ivan.pocketassistant

import org.junit.Assert.*
import org.junit.Test

class TimerCommandTest {
    private fun seconds(text: String) = (TimerCommand.parse(text) as TimerCommand.Result.Valid).seconds
    @Test fun spokenAndNumericDurations() {
        assertEquals(300, seconds("Set a timer for five minutes."))
        assertEquals(300, seconds("set a 5-minute timer"))
        assertEquals(90, seconds("start a timer for one minute and thirty seconds"))
        assertEquals(5400, seconds("please set a timer for 1 hour 30 minutes"))
        assertEquals(1260, seconds("set a timer for twenty-one minutes please"))
        assertEquals(3600, seconds("set a timer for an hour"))
    }
    @Test fun invalidDurationsNeverCreateTimers() {
        for (text in listOf("set a timer", "set a timer for five", "set a timer for -5 minutes", "set a timer for 1.5 minutes", "set a timer for zero seconds", "set a timer for 25 hours", "set a timer for five minutes tomorrow", "cancel my timer", "set a timer for two minutes and two minutes", "set a timer for 999999999999999 seconds"))
            assertTrue(text, TimerCommand.parse(text) is TimerCommand.Result.Invalid)
    }
    @Test fun boundaryAndOtherCommands() {
        assertEquals(1, seconds("set a timer for 1 second"))
        assertEquals(86400, seconds("set a timer for 24 hours"))
        assertEquals(TimerCommand.Result.NotTimer, TimerCommand.parse("what time is it"))
        assertEquals("1 minute and 30 seconds", TimerCommand.describe(90))
    }
}
