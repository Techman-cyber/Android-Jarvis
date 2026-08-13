package com.jarvis.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.android.CommandRouter
import com.jarvis.android.MainActivity
import com.jarvis.android.R
import com.jarvis.android.tts.VoiceSpeaker
import com.jarvis.android.util.Prefs

class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "jarvis_wake_word_channel"
        const val NOTIF_ID = 42
        private const val TAG = "WakeWordService"
        private const val STATE_WAKE = 0
        private const val STATE_COMMAND = 1
    }

    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var state = STATE_WAKE
    private var isDestroyed = false
    private var wakeHandledThisSession = false

    private lateinit var prefs: Prefs
    private lateinit var router: CommandRouter
    private lateinit var speaker: VoiceSpeaker

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        router = CommandRouter(this)
        speaker = VoiceSpeaker(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Listening for \"${prefs.wakeWord}\"")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        prefs.wakeServiceEnabled = true
        state = STATE_WAKE
        mainHandler.post { startRecognizer() }
        return START_STICKY
    }

    override fun onDestroy() {
        isDestroyed = true
        prefs.wakeServiceEnabled = false
        recognizer?.destroy()
        router.shutdown()
        speaker.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun startRecognizer() {
        if (isDestroyed) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available on this device")
            return
        }
        wakeHandledThisSession = false
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                if (state == STATE_COMMAND) 1200 else 1500
            )
        }
        recognizer?.startListening(intent)
    }

    private fun restartSoon(delayMs: Long = 400) {
        if (isDestroyed) return
        mainHandler.postDelayed({ startRecognizer() }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            restartSoon()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (state != STATE_WAKE || wakeHandledThisSession) return
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.lowercase() ?: return
            if (text.contains(prefs.wakeWord)) {
                wakeHandledThisSession = true
                onWakeWordDetected(text)
            }
        }

        override fun onResults(results: Bundle?) {
            if (wakeHandledThisSession) return
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.lowercase().orEmpty()

            when (state) {
                STATE_WAKE -> {
                    if (text.contains(prefs.wakeWord)) {
                        wakeHandledThisSession = true
                        onWakeWordDetected(text)
                    } else {
                        restartSoon(150)
                    }
                }
                STATE_COMMAND -> {
                    state = STATE_WAKE
                    if (text.isNotBlank()) {
                        router.handle(text)
                    }
                    updateNotification("Listening for \"${prefs.wakeWord}\"")
                    restartSoon(800)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun onWakeWordDetected(heardText: String) {
        val remainder = heardText.substringAfter(prefs.wakeWord).trim()

        recognizer?.cancel()
        state = STATE_COMMAND

        if (remainder.length > 2) {
            state = STATE_WAKE
            updateNotification("Yes?")
            router.handle(remainder)
            updateNotification("Listening for \"${prefs.wakeWord}\"")
            restartSoon(600)
        } else {
            updateNotification("Yes?")
            val honorificWord = if (prefs.honorific == "maam") "ma'am" else "sir"
            speaker.speak("Yes, $honorificWord?")
            restartSoon(1400)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_word_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) : android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification(text))
    }
}
