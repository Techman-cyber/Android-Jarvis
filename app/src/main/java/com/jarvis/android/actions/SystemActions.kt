package com.jarvis.android.actions

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.os.BatteryManager
import android.app.ActivityManager

/**
 * Replaces computer_settings.py's volume_up/volume_down/volume_set/brightness_*.
 * No pycaw, no WMI, no osascript, no pactl — Android exposes these directly.
 */
class SystemActions(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun volumeUp() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }

    fun volumeDown() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    fun volumeMute() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
    }

    /** value: 0-100, mirrors volume_set(value) in computer_settings.py */
    fun volumeSet(value: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (value.coerceIn(0, 100) / 100.0 * max).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
    }

    /**
     * Brightness needs Settings.System.canWrite(context) == true first —
     * prompt the user via ACTION_MANAGE_WRITE_SETTINGS if false.
     */
    fun brightnessSet(value: Int) {
        if (!Settings.System.canWrite(context)) {
            throw SecurityException("Need WRITE_SETTINGS permission — direct user to system settings first")
        }
        val clamped = value.coerceIn(0, 255)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, clamped)
    }

    /** Replaces system_monitor.py's get_system_status() — no psutil/ctypes/wmi needed. */
    fun getSystemStatus(): Map<String, Any?> {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        return mapOf(
            "battery_percent" to batteryPct,
            "ram_available_mb" to memInfo.availMem / (1024 * 1024),
            "ram_total_mb" to memInfo.totalMem / (1024 * 1024),
            "low_memory" to memInfo.lowMemory
        )
    }
}
