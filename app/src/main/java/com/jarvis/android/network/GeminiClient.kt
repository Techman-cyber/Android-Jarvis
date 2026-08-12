package com.jarvis.android.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct Kotlin port of web_search.py / media_generator.py's Gemini calls.
 * Same idea as _get_api_key() in your python code, but the key lives in
 * BuildConfig / local.properties instead of config/api_keys.json.
 *
 * Get a key at: https://aistudio.google.com/apikey
 */
class GeminiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Mirrors _gemini_search() in web_search.py — grounded web search via Gemini. */
    fun search(query: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", query)))
            }))
            put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw RuntimeException("Empty Gemini response")
            if (!response.isSuccessful) throw RuntimeException("Gemini error ${response.code}: $text")

            val json = JSONObject(text)
            val parts = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")

            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text", ""))
            }
            return sb.toString().trim()
        }
    }

    /** Generic chat call for the assistant's main reasoning turn. */
    fun chat(systemInstruction: String, userMessage: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
            }))
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw RuntimeException("Empty Gemini response")
            if (!response.isSuccessful) throw RuntimeException("Gemini error ${response.code}: $text")
            val json = JSONObject(text)
            return json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        }
    }
}
