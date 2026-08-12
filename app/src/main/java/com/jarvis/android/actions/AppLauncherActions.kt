package com.jarvis.android.actions

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Opens any installed app by its spoken label — "open Spotify", "open camera" —
 * even if the app isn't currently running. Uses PackageManager.getLaunchIntentForPackage,
 * which cold-starts the app exactly like tapping its icon on the home screen.
 */
class AppLauncherActions(private val context: Context) {

    private val pm = context.packageManager

    private fun installedLaunchableApps(): List<Pair<String, String>> {
        // Returns (label, packageName) for every app that has a launcher icon.
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return resolved.mapNotNull { ri ->
            val label = ri.loadLabel(pm)?.toString() ?: return@mapNotNull null
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            label to pkg
        }.distinctBy { it.second }
    }

    /** Fuzzy-matches a spoken app name against installed app labels and launches the best match. */
    fun openAppByName(spokenName: String): String? {
        val query = spokenName.lowercase().trim()
        if (query.isEmpty()) return null

        val apps = installedLaunchableApps()
        var bestLabel: String? = null
        var bestPkg: String? = null
        var bestScore = -1

        for ((label, pkg) in apps) {
            val lower = label.lowercase()
            val score = when {
                lower == query -> 100
                lower.startsWith(query) -> 80
                lower.contains(query) -> 60
                query.contains(lower) && lower.length > 2 -> 50
                else -> -1
            }
            if (score > bestScore) {
                bestScore = score
                bestLabel = label
                bestPkg = pkg
            }
        }

        if (bestPkg == null) return null
        val launchIntent = pm.getLaunchIntentForPackage(bestPkg) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return bestLabel
    }
}
