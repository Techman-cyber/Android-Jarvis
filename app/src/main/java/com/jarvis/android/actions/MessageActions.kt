package com.jarvis.android.actions

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Replaces send_message.py. The Python version used pyautogui to click/type
 * into desktop WhatsApp/Telegram/Discord windows — Android has no equivalent
 * "windows" to automate. Instead, apps expose Intents that do this properly
 * and don't require Accessibility permissions.
 */
class MessageActions(private val context: Context) {

    /** WhatsApp — opens a chat with prefilled text. Number must be intl format, no '+' or spaces. */
    fun sendWhatsApp(phoneNumber: String, message: String) {
        val uri = Uri.parse("https://wa.me/$phoneNumber?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        // Note: this opens the chat with the message prefilled; WhatsApp requires
        // the user to tap send (WhatsApp blocks fully automated sending without
        // their Business API). This matches Android's platform restrictions.
    }

    /** Generic SMS via the default messaging app. */
    fun sendSms(phoneNumber: String, message: String) {
        val uri = Uri.parse("smsto:$phoneNumber")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** Opens Telegram to a specific username's chat. */
    fun openTelegram(username: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$username")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** Launches any installed app by package name — replaces _open_app(). */
    fun launchApp(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(launchIntent)
        return true
    }
}
