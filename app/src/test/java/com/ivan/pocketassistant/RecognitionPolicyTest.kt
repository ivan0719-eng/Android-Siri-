package com.ivan.pocketassistant

import org.junit.Assert.*
import org.junit.Test

class RecognitionPolicyTest {
    @Test fun temporaryFailuresRetryWithoutAnotherWakeWord() {
        for (code in listOf(3, 4, 5, 6, 7, 8, 11))
            assertEquals(RecognitionPolicy.Recovery.RETRY, RecognitionPolicy.recovery(code, false, 0))
    }
    @Test fun recoveryIsBoundedAcrossBothProviders() {
        for (code in 1..15) for (onDevice in listOf(true, false))
            assertEquals(RecognitionPolicy.Recovery.FINISH, RecognitionPolicy.recovery(code, onDevice, 1))
    }
    @Test fun permissionAndRateLimitsNeverAutomaticallyRetry() {
        for (code in listOf(9, 10)) for (onDevice in listOf(true, false))
            assertEquals(RecognitionPolicy.Recovery.FINISH, RecognitionPolicy.recovery(code, onDevice, 0))
        assertEquals(RecognitionPolicy.Recovery.FALLBACK, RecognitionPolicy.recovery(13, true, 0))
    }
    @Test fun missingLanguageAndBrokenServiceAllowFallback() {
        for (code in listOf(12, 13, 4, 11, 5, 8)) assertTrue(RecognitionPolicy.canFallback(code))
    }
    @Test fun permissionAndNoSpeechErrorsDoNotTriggerAnotherProvider() {
        for (code in listOf(9, 6, 7, 10, 3)) assertFalse(RecognitionPolicy.canFallback(code))
    }
    @Test fun missingModelErrorExplainsWhatIsWrong() {
        assertTrue(RecognitionPolicy.describe(13).contains("not downloaded"))
        assertTrue(RecognitionPolicy.describe(9).contains("permission"))
    }
}
