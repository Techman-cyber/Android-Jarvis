package com.jarvis.android

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.android.tts.VoiceSpeaker
import com.jarvis.android.util.Prefs

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var speaker: VoiceSpeaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = Prefs(this)
        speaker = VoiceSpeaker(this)

        val apiKeyField = findViewById<EditText>(R.id.apiKeyField)
        val wakeWordField = findViewById<EditText>(R.id.wakeWordField)
        val honorificSpinner = findViewById<Spinner>(R.id.honorificSpinner)
        val voiceGenderSpinner = findViewById<Spinner>(R.id.voiceGenderSpinner)
        val aliasNameField = findViewById<EditText>(R.id.aliasNameField)
        val aliasContactField = findViewById<EditText>(R.id.aliasContactField)
        val aliasListText = findViewById<TextView>(R.id.aliasListText)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val addAliasButton = findViewById<Button>(R.id.addAliasButton)
        val testVoiceButton = findViewById<Button>(R.id.testVoiceButton)

        apiKeyField.setText(prefs.apiKey)
        wakeWordField.setText(prefs.wakeWord)

        val honorificOptions = listOf("sir", "maam")
        honorificSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, honorificOptions)
        honorificSpinner.setSelection(honorificOptions.indexOf(prefs.honorific).coerceAtLeast(0))

        val genderOptions = listOf("male", "female")
        voiceGenderSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genderOptions)
        voiceGenderSpinner.setSelection(genderOptions.indexOf(prefs.voiceGender).coerceAtLeast(0))

        fun refreshAliasList() {
            val aliases = prefs.getAliases()
            aliasListText.text = if (aliases.isEmpty()) {
                "No aliases yet. e.g. \"boss\" -> \"John Smith\""
            } else {
                aliases.entries.joinToString("\n") { "\"${it.key}\" -> ${it.value}" }
            }
        }
        refreshAliasList()

        saveButton.setOnClickListener {
            prefs.apiKey = apiKeyField.text.toString().trim()
            prefs.wakeWord = wakeWordField.text.toString().ifBlank { "jarvis" }
            prefs.honorific = honorificOptions[honorificSpinner.selectedItemPosition]
            prefs.voiceGender = genderOptions[voiceGenderSpinner.selectedItemPosition]
            speaker.applyVoicePreference()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        addAliasButton.setOnClickListener {
            val alias = aliasNameField.text.toString().trim()
            val contact = aliasContactField.text.toString().trim()
            if (alias.isNotBlank() && contact.isNotBlank()) {
                prefs.addAlias(alias, contact)
                aliasNameField.text.clear()
                aliasContactField.text.clear()
                refreshAliasList()
            } else {
                Toast.makeText(this, "Enter both an alias and a contact name", Toast.LENGTH_SHORT).show()
            }
        }

        testVoiceButton.setOnClickListener {
            prefs.honorific = honorificOptions[honorificSpinner.selectedItemPosition]
            prefs.voiceGender = genderOptions[voiceGenderSpinner.selectedItemPosition]
            speaker.applyVoicePreference()
            speaker.speakWithHonorific("Hello, I am Jarvis. This is a voice test")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speaker.shutdown()
    }
}
