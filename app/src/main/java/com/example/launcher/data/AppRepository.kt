package com.example.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    fun loadApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
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
