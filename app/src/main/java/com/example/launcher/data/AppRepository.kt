package com.example.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

object AppCache {
    @Volatile
    var cachedApps: List<AppInfo>? = null

    @Volatile
    var cachedDockPkgs: List<String>? = null
}

class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    fun getCachedApps(): List<AppInfo>? = AppCache.cachedApps

    fun getCachedDockPackages(): List<String>? = AppCache.cachedDockPkgs

    fun clearCache() {
        AppCache.cachedApps = null
        AppCache.cachedDockPkgs = null
    }

    /**
     * Fast-loads ONLY the target Solar Orbit apps (~10 apps).
     * Takes ~15ms instead of 2000ms, making orbits visible almost instantly on startup.
     */
    fun loadSolarApps(savedPackages: Set<String>): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }

        val targetActivities = if (savedPackages.isNotEmpty()) {
            val matched = activities.filter { savedPackages.contains(it.activityInfo.packageName) }
            if (matched.isNotEmpty()) matched else activities.take(10)
        } else {
            activities.take(10)
        }

        return targetActivities.map {
            val label = it.loadLabel(pm).toString()
            val pkg = it.activityInfo.packageName
            val component = ComponentName(pkg, it.activityInfo.name)
            val icon = it.loadIcon(pm)
            val category = detectCategory(label, pkg)
            AppInfo(label = label, component = component, icon = icon, category = category)
        }
    }

    fun loadApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                val label = it.loadLabel(pm).toString()
                val pkg = it.activityInfo.packageName
                val component = ComponentName(pkg, it.activityInfo.name)
                val icon = it.loadIcon(pm)
                val category = detectCategory(label, pkg)
                AppInfo(label = label, component = component, icon = icon, category = category)
            }
            .sortedBy { it.label.lowercase() }

        AppCache.cachedApps = apps
        return apps
    }

    private fun detectCategory(label: String, pkg: String): AppCategory {
        val l = label.lowercase()
        val p = pkg.lowercase()

        // Social / Communication
        if (p.contains("message") || p.contains("mms") || p.contains("sms") ||
            p.contains("contact") || p.contains("whatsapp") || p.contains("telegram") ||
            p.contains("instagram") || p.contains("facebook") || p.contains("twitter") ||
            p.contains("social") || p.contains("mail") || p.contains("gmail") ||
            p.contains("discord") || p.contains("reddit") || p.contains("signal") ||
            l.contains("chat") || l.contains("social") || l.contains("messenger")
        ) {
            return AppCategory.SOCIAL
        }

        // Media & Entertainment
        if (p.contains("music") || p.contains("audio") || p.contains("spotify") ||
            p.contains("camera") || p.contains("gallery") || p.contains("photos") ||
            p.contains("video") || p.contains("youtube") || p.contains("netflix") ||
            p.contains("media") || p.contains("player") || p.contains("sound") ||
            p.contains("podcast") || l.contains("music") || l.contains("camera") ||
            l.contains("photo") || l.contains("gallery") || l.contains("video")
        ) {
            return AppCategory.MEDIA
        }

        // System
        if (p.contains("android") || p.contains("setting") || p.contains("vending") ||
            p.contains("system") || p.contains("calculator") || p.contains("clock") ||
            p.contains("dialer") || p.contains("phone") || p.contains("security") ||
            p.contains("file") || p.contains("download") || l.contains("setting") ||
            l.contains("files") || l.contains("security")
        ) {
            return AppCategory.SYSTEM
        }

        return AppCategory.TOOLS
    }

    fun defaultDockPackages(): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        val resolved = intents.mapNotNull {
            pm.resolveActivity(it, 0)?.activityInfo?.packageName
        }.distinct().toMutableList()

        // Also check popular music/browser packages if not filled
        if (resolved.size < 5) {
            val extras = listOf("com.spotify.music", "com.google.android.apps.maps", "com.android.chrome")
            for (pkg in extras) {
                if (!resolved.contains(pkg)) {
                    try {
                        pm.getPackageInfo(pkg, 0)
                        resolved.add(pkg)
                        if (resolved.size >= 5) break
                    } catch (_: Exception) {}
                }
            }
        }
        AppCache.cachedDockPkgs = resolved
        return resolved
    }

    fun launch(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
    }
}
