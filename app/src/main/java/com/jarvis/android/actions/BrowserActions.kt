package com.jarvis.android.actions

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Replaces browser_control.py. The desktop version used Playwright to drive
 * a real Chrome/Edge/Firefox profile with cookies, tabs, JS injection, etc.
 * That level of control does not exist for Android's browser sandbox.
 * This gives you the equivalent of what's actually possible: opening URLs,
 * triggering a search, and (optionally) an embedded WebView if you need
 * in-app page reading/scraping instead of full automation.
 */
class BrowserActions(private val context: Context) {

    /** Mirrors _normalize_url() + _open_url() from browser_control.py. */
    private fun normalizeUrl(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) return "about:blank"
        if ("://" in url) return url
        if ("." !in url) url += ".com"
        return "https://$url"
    }

    fun openUrl(rawUrl: String) {
        val url = normalizeUrl(rawUrl)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** Replaces weather_report.py's webbrowser.open(google search url) */
    fun searchGoogle(query: String) {
        openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
    }

    /** Replaces the YouTube scraping logic in youtube_video.py's simplest path. */
    fun openYoutubeSearch(query: String) {
        openUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
    }
}
