package com.jarvis.android

import android.content.Context
import com.jarvis.android.actions.AppLauncherActions
import com.jarvis.android.actions.BrowserActions
import com.jarvis.android.actions.CallResult
import com.jarvis.android.actions.ContactActions
import com.jarvis.android.actions.MediaActions
import com.jarvis.android.actions.MessageActions
import com.jarvis.android.actions.SystemActions
import com.jarvis.android.network.GeminiClient
import com.jarvis.android.tts.VoiceSpeaker
import com.jarvis.android.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single entry point for handling a transcribed voice command, whether it
 * came from the on-screen mic button or the always-on wake-word service.
 * Tries fast rule-based matches first (instant, no network); anything it
 * doesn't recognize falls through to Gemini for a conversational reply.
 */
class CommandRouter(private val context: Context) {

    private val prefs = Prefs(context)
    private val systemActions = SystemActions(context)
    private val browserActions = BrowserActions(context)
    private val messageActions = MessageActions(context)
    private val contactActions = ContactActions(context)
    private val appLauncher = AppLauncherActions(context)
    private val mediaActions = MediaActions(context)
    private val speaker = VoiceSpeaker(context)

    private val scope = CoroutineScope(Dispatchers.Main)

    fun handle(rawCommand: String) {
        val command = rawCommand.trim()
        val lower = command.lowercase()
        if (lower.isBlank()) return

        when {
            // --- Calling contacts ---
            Regex("^(call|dial|phone)\\s+(.+)").containsMatchIn(lower) -> {
                val name = Regex("^(call|dial|phone)\\s+(.+)").find(lower)?.groupValues?.get(2) ?: ""
                when (val result = contactActions.callContact(name)) {
                    is CallResult.Calling -> speaker.speakWithHonorific("Calling ${result.name}")
                    is CallResult.DialerOpened -> speaker.speakWithHonorific("Opening dialer for ${result.name}")
                    CallResult.NotFound -> speaker.speakWithHonorific("I couldn't find a contact named $name")
                }
            }

            // --- YouTube ---
            Regex("play\\s+(.+)\\s+on\\s+youtube").containsMatchIn(lower) -> {
                val query = Regex("play\\s+(.+)\\s+on\\s+youtube").find(lower)?.groupValues?.get(1) ?: ""
                mediaActions.openYoutube(query)
                speaker.speakWithHonorific("Playing $query on YouTube")
            }
            lower.startsWith("search youtube for") || lower.startsWith("youtube search") -> {
                val query = lower.substringAfter("for").ifBlank { lower.substringAfter("search") }.trim()
                mediaActions.openYoutube(query)
                speaker.speakWithHonorific("Searching YouTube for $query")
            }
            lower == "open youtube" -> {
                mediaActions.openYoutube()
                speaker.speakWithHonorific("Opening YouTube")
            }

            // --- Media transport controls ---
            lower.contains("pause") -> { mediaActions.pause(); speaker.speak("Paused") }
            lower.contains("resume") || lower == "play" -> { mediaActions.play(); speaker.speak("Playing") }
            lower.contains("next") && (lower.contains("song") || lower.contains("track") || lower.contains("video")) -> {
                mediaActions.next(); speaker.speak("Next")
            }
            lower.contains("previous") || lower.contains("go back") -> {
                mediaActions.previous(); speaker.speak("Previous")
            }
            lower.contains("stop music") || lower.contains("stop video") -> {
                mediaActions.stop(); speaker.speak("Stopped")
            }

            // --- Opening apps ---
            Regex("^(open|launch|start)\\s+(.+)").containsMatchIn(lower) -> {
                val name = Regex("^(open|launch|start)\\s+(.+)").find(lower)?.groupValues?.get(2) ?: ""
                val opened = appLauncher.openAppByName(name)
                if (opened != null) {
                    speaker.speakWithHonorific("Opening $opened")
                } else {
                    speaker.speakWithHonorific("I couldn't find an app called $name")
                }
            }

            // --- System / volume ---
            lower.contains("volume up") -> { systemActions.volumeUp(); speaker.speak("Volume up") }
            lower.contains("volume down") -> { systemActions.volumeDown(); speaker.speak("Volume down") }
            lower.contains("mute") -> { systemActions.volumeMute(); speaker.speak("Muted") }

            // --- Web search ---
            lower.startsWith("search for") || lower.startsWith("google") -> {
                val query = lower.substringAfter("for").ifBlank { lower.substringAfter("google") }.trim()
                browserActions.searchGoogle(query)
                speaker.speakWithHonorific("Searching for $query")
            }

            // --- Messaging ---
            Regex("^(message|text|whatsapp)\\s+(\\w+)\\s+(saying|that says|)\\s*(.+)").containsMatchIn(lower) -> {
                val match = Regex("^(message|text|whatsapp)\\s+(\\w+)\\s+(saying|that says|)\\s*(.+)").find(lower)
                val target = match?.groupValues?.get(2) ?: ""
                val body = match?.groupValues?.get(4) ?: ""
                val contact = contactActions.findContact(target)
                if (contact != null) {
                    if (lower.startsWith("whatsapp")) {
                        messageActions.sendWhatsApp(contact.number.filter { it.isDigit() }, body)
                    } else {
                        messageActions.sendSms(contact.number, body)
                    }
                    speaker.speakWithHonorific("Message ready for ${contact.name}")
                } else {
                    speaker.speakWithHonorific("I couldn't find a contact named $target")
                }
            }

            // --- Fallback: ask Gemini for a conversational reply ---
            else -> askGemini(command)
        }
    }

    private fun askGemini(command: String) {
        val apiKey = prefs.apiKey
        if (apiKey.isBlank()) {
            speaker.speakWithHonorific("Please add your Gemini API key in Settings first")
            return
        }
        val client = GeminiClient(apiKey)
        val honorific = if (prefs.honorific == "maam") "ma'am" else "sir"
        scope.launch {
            val reply = withContext(Dispatchers.IO) {
                runCatching {
                    client.chat(
                        systemInstruction = "You are JARVIS, a concise, helpful voice assistant. " +
                            "Address the user as $honorific. Keep replies to 1-3 short sentences, " +
                            "suitable for being spoken aloud.",
                        userMessage = command
                    )
                }.getOrElse { "Sorry $honorific, something went wrong: ${it.message}" }
            }
            speaker.speak(reply)
        }
    }

    fun shutdown() {
        speaker.shutdown()
    }
}
