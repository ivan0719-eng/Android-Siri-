package com.ivan.pocketassistant

import android.speech.SpeechRecognizer

object RecognitionPolicy {
    enum class Recovery { FALLBACK, RETRY, FINISH }

    fun recovery(error: Int, onDevice: Boolean, recoveriesUsed: Int): Recovery {
        if (recoveriesUsed >= 1) return Recovery.FINISH
        if (onDevice && canFallback(error)) return Recovery.FALLBACK
        return if (error in setOf(
            SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED, SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        )) Recovery.RETRY else Recovery.FINISH
    }

    fun canFallback(error: Int): Boolean = error in setOf(
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        SpeechRecognizer.ERROR_CLIENT,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    )

    fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "English (en-US) is not supported by this recognizer."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "The English (en-US) speech model is not downloaded or available."
        SpeechRecognizer.ERROR_AUDIO -> "The recognizer could not capture microphone audio. Close other recording apps and retry."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech recognizer is busy. Wait a moment and retry."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access was denied. Check app permission and the phone's microphone privacy switch."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The speech provider needs a working internet connection."
        SpeechRecognizer.ERROR_NO_MATCH -> "No words were recognized. Wait for Speak now, then try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was heard. Wait for Speak now, then try again."
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "The speech recognition service failed or disconnected."
        SpeechRecognizer.ERROR_CLIENT -> "The speech provider rejected the recognition session."
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "The speech provider is limiting requests. Wait before trying again."
        else -> "The speech provider returned an unexpected error."
    }
}
