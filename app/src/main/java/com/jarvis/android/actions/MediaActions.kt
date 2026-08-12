package com.jarvis.android.actions

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent

/**
 * Controls whatever is currently playing media (YouTube, Spotify, etc.) using
 * system-wide media button events — the same signal a Bluetooth headset button
 * sends. This works with YouTube's now-playing session without needing YouTube's
 * (unpublished) API. Also handles opening/searching YouTube directly.
 */
class MediaActions(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun sendMediaKey(keyCode: Int) {
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    fun playPause() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun play() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    fun pause() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun next() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun stop() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_STOP)

    /** Opens the YouTube app (or web fallback) and searches for the query, ready to play. */
    fun openYoutube(query: String? = null) {
        if (query.isNullOrBlank()) {
            val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
            } else {
                openUrl("https://www.youtube.com")
            }
            return
        }
        // This deep link opens the YouTube app's search results directly if installed.
        val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage("com.google.android.youtube")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            openUrl(uri.toString())
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
