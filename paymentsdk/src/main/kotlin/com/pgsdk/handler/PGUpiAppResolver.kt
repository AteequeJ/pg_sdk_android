package com.pgsdk.handler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager

/** Discovers UPI-capable apps installed on the device via `PackageManager` queries. */
internal object PGUpiAppResolver {

    /** A generic, unaddressed `upi://pay` intent used purely to query installed handlers. */
    private fun probeIntent() = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))

    fun resolveInstalledApps(context: Context): List<PGUpiApp> {
        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(probeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        return activities
            .map { resolveInfo ->
                PGUpiApp(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    icon = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun hasAnyUpiApp(context: Context): Boolean =
        context.packageManager
            .queryIntentActivities(probeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            .isNotEmpty()
}
