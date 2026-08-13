package com.jarvis.android

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.android.service.WakeWordService
import com.jarvis.android.util.Prefs

class MainActivity : AppCompatActivity() {

    private lateinit var router: CommandRouter
    private lateinit var prefs: Prefs
    private lateinit var statusText: TextView
    private lateinit var wakeToggleButton: Button

    private val speechRequestCode = 100

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
    ) + if (android.os.Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else emptyArray()

    // Only these are actually needed to start the wake-word service. Contacts/call
    // permissions are nice-to-have (for "call X") but must never block mic listening.
    private val wakeServicePermissions = arrayOf(Manifest.permission.RECORD_AUDIO) +
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else emptyArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        router = CommandRouter(this)

        statusText = findViewById(R.id.statusText)
        wakeToggleButton = findViewById(R.id.wakeToggleButton)

        requestPermissionsIfNeeded()

        findViewById<Button>(R.id.micButton).setOnClickListener { startListening() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        wakeToggleButton.setOnClickListener { toggleWakeService() }

        updateWakeButtonLabel()
        startRingAnimations()

        if (prefs.apiKey.isBlank()) {
            statusText.text = "Add your Gemini API key in Settings to enable full commands."
        }
    }

    /** Slow counter-rotating HUD rings behind the mic button, purely decorative. */
    private fun startRingAnimations() {
        findViewById<ImageView>(R.id.outerRing)?.let { ring ->
            ObjectAnimator.ofFloat(ring, "rotation", 0f, 360f).apply {
                duration = 22000
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
        findViewById<ImageView>(R.id.innerRing)?.let { ring ->
            ObjectAnimator.ofFloat(ring, "rotation", 360f, 0f).apply {
                duration = 14000
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateWakeButtonLabel()
    }

    private fun updateWakeButtonLabel() {
        wakeToggleButton.text = if (prefs.wakeServiceEnabled) {
            "Stop \"${prefs.wakeWord}\" listening"
        } else {
            "Start \"${prefs.wakeWord}\" listening"
        }
    }

    private fun toggleWakeService() {
        val missing = wakeServicePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            Toast.makeText(this, "Grant microphone permission first", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
            return
        }
        val intent = Intent(this, WakeWordService::class.java)
        if (prefs.wakeServiceEnabled) {
            stopService(intent)
            prefs.wakeServiceEnabled = false
            statusText.text = "Wake-word listening stopped."
        } else {
            ContextCompat.startForegroundService(this, intent)
            statusText.text = "Listening for \"${prefs.wakeWord}\"…"
        }
        updateWakeButtonLabel()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command")
        }
        try {
            startActivityForResult(intent, speechRequestCode)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == speechRequestCode && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val command = results?.firstOrNull() ?: return
            statusText.text = "You said: \"$command\""
            router.handle(command)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        router.shutdown()
    }
}
