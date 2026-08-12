package com.jarvis.android.util

import android.content.Context
import org.json.JSONObject

/**
 * Single place for all user-configurable settings, backed by SharedPreferences.
 * Everything here is edited from SettingsActivity at runtime — nothing is
 * hardcoded or baked into the build.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_HONORIFIC = "honorific"          // "sir" or "maam"
        private const val KEY_VOICE_GENDER = "voice_gender"    // "male" or "female"
        private const val KEY_WAKE_WORD = "wake_word"          // default "jarvis"
        private const val KEY_ALIASES = "contact_aliases_json" // {"boss": "John Smith"}
        private const val KEY_SERVICE_ENABLED = "wake_service_enabled"
    }

    var apiKey: String
        get() = sp.getString(KEY_API_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_API_KEY, value).apply()

    var honorific: String
        get() = sp.getString(KEY_HONORIFIC, "sir") ?: "sir"
        set(value) = sp.edit().putString(KEY_HONORIFIC, value).apply()

    var voiceGender: String
        get() = sp.getString(KEY_VOICE_GENDER, "male") ?: "male"
        set(value) = sp.edit().putString(KEY_VOICE_GENDER, value).apply()

    var wakeWord: String
        get() = sp.getString(KEY_WAKE_WORD, "jarvis") ?: "jarvis"
        set(value) = sp.edit().putString(KEY_WAKE_WORD, value.lowercase()).apply()

    var wakeServiceEnabled: Boolean
        get() = sp.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    /** Maps a spoken alias (e.g. "boss") -> a real contact display name (e.g. "John Smith"). */
    fun getAliases(): Map<String, String> {
        val raw = sp.getString(KEY_ALIASES, "{}") ?: "{}"
        val obj = JSONObject(raw)
        val map = mutableMapOf<String, String>()
        obj.keys().forEach { k -> map[k] = obj.getString(k) }
        return map
    }

    fun setAliases(map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k.lowercase(), v) }
        sp.edit().putString(KEY_ALIASES, obj.toString()).apply()
    }

    fun addAlias(alias: String, contactName: String) {
        val current = getAliases().toMutableMap()
        current[alias.lowercase()] = contactName
        setAliases(current)
    }

    fun resolveAlias(spokenName: String): String {
        val aliases = getAliases()
        return aliases[spokenName.lowercase()] ?: spokenName
    }
}
