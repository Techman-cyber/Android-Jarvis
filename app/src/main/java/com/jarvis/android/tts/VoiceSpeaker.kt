package com.jarvis.android.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.jarvis.android.util.Prefs
import java.util.Locale

/**
 * Wraps Android's TextToSpeech engine. Picks a male- or female-sounding
 * system voice based on the user's Settings choice, and can address the
 * user as "sir" or "ma'am" in generated replies.
 *
 * Voice-matching by name (e.g. "female" in the voice ID) only works on a
 * handful of engines/voice packs, so it is treated as a bonus, not the
 * primary mechanism. Pitch + speech rate are shifted every time regardless
 * of whether a gendered voice was found, which is guaranteed to be audible
 * on every device/engine and is what actually makes the two options sound
 * different.
 */
class VoiceSpeaker(context: Context) {

    companion object {
        // Deliberately far apart so the difference is unmistakable, not subtle.
        private const val MALE_PITCH = 0.72f
        private const val MALE_RATE = 0.92f
        private const val FEMALE_PITCH = 1.35f
        private const val FEMALE_RATE = 1.05f
    }

    private val appContext = context.applicationContext
    private val prefs = Prefs(appContext)
    private var ready = false
    private var tts: TextToSpeech? = null
    private val pendingQueue = mutableListOf<String>()

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.language = Locale.US
                applyVoicePreference()
                pendingQueue.forEach { speakNow(it) }
                pendingQueue.clear()
            } else {
                Log.e("VoiceSpeaker", "TTS init failed: $status")
            }
        }
    }

    /** Call again after the user changes the voice gender in Settings. */
    fun applyVoicePreference() {
        val engine = tts ?: return
        val wantFemale = prefs.voiceGender == "female"

        // Bonus: if the engine happens to expose an explicitly gendered
        // English voice, use it as the base on top of the pitch/rate shift.
        val candidate: Voice? = engine.voices?.firstOrNull { v ->
            val name = v.name.lowercase()
            val genderHint = if (wantFemale) "female" else "male"
            name.contains(genderHint) && v.locale.language == "en" && !v.isNetworkConnectionRequired
        }
        candidate?.let { engine.voice = it }

        // Primary mechanism — always applied, always audible.
        engine.setPitch(if (wantFemale) FEMALE_PITCH else MALE_PITCH)
        engine.setSpeechRate(if (wantFemale) FEMALE_RATE else MALE_RATE)
    }

    fun speak(text: String) {
        // Re-apply every time in case the setting changed since this
        // VoiceSpeaker instance was created (e.g. edited in Settings while
        // the wake-word service is still running with an older instance).
        applyVoicePreference()
        if (ready) speakNow(text) else pendingQueue.add(text)
    }

    /** Prefixes a reply with the configured honorific, e.g. "Calling John, sir." */
    fun speakWithHonorific(text: String) {
        val honorificWord = if (prefs.honorific == "maam") "ma'am" else "sir"
        val finalText = if (text.trim().endsWith(".") || text.trim().endsWith("?") || text.trim().endsWith("!")) {
            "$text ${honorificWord.replaceFirstChar { it.uppercase() }}."
        } else {
            "$text, $honorificWord."
        }
        speak(finalText)
    }

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "jarvis_utt_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
