package com.ivan.pocketassistant

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class CommandsTest {
    private val now = ZonedDateTime.parse("2026-11-16T23:05:00+08:00[Asia/Kuala_Lumpur]")
    @Test fun timeUsesDeviceLocalTime() {
        assertEquals("It's 11:05 PM.", Commands.reply("What time is it?", now))
    }
    @Test fun dateUsesSuppliedTime() {
        assertEquals("Today is Monday, November 16.", Commands.reply("What day is it?", now))
    }
    @Test fun unsupportedActionsNeverClaimSuccess() {
        assertTrue(Commands.reply("set an alarm for five", now).startsWith("I heard:"))
        assertTrue(Commands.reply("what is the time in London", now).startsWith("I heard:"))
    }
}
