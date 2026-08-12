package com.jarvis.android.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.jarvis.android.util.Prefs

data class ContactMatch(val name: String, val number: String)

/**
 * Looks up contacts by spoken name (with alias support, e.g. "call mom")
 * and places the call directly via ACTION_CALL. Falls back to ACTION_DIAL
 * if CALL_PHONE permission hasn't been granted, so the app still does
 * something useful instead of silently failing.
 */
class ContactActions(private val context: Context) {

    private val prefs = Prefs(context)

    private fun hasPermission(perm: String) =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    /** Finds the best-matching contact for a spoken name, resolving aliases first. */
    fun findContact(spokenName: String): ContactMatch? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null
        val resolvedName = prefs.resolveAlias(spokenName).lowercase().trim()

        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        ) ?: return null

        var best: ContactMatch? = null
        var bestScore = -1

        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numIdx) ?: continue
                val lower = name.lowercase()

                val score = when {
                    lower == resolvedName -> 100
                    lower.startsWith(resolvedName) -> 80
                    lower.contains(resolvedName) -> 60
                    resolvedName.split(" ").any { part -> part.length > 2 && lower.contains(part) } -> 40
                    else -> -1
                }
                if (score > bestScore) {
                    bestScore = score
                    best = ContactMatch(name, number)
                }
            }
        }
        return best
    }

    /** Places the call directly if permission granted, otherwise opens the dialer pre-filled. */
    fun callContact(spokenName: String): CallResult {
        val match = findContact(spokenName) ?: return CallResult.NotFound
        val uri = Uri.parse("tel:${match.number}")

        return if (hasPermission(Manifest.permission.CALL_PHONE)) {
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CallResult.Calling(match.name)
        } else {
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CallResult.DialerOpened(match.name)
        }
    }
}

sealed class CallResult {
    data class Calling(val name: String) : CallResult()
    data class DialerOpened(val name: String) : CallResult()
    object NotFound : CallResult()
}
