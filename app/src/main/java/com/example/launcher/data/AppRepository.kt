package com.example.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

/** Loads launchable apps and resolves the default dock apps. */
class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    /** All launchable apps except this launcher, sorted by label. Call off the main thread. */
    fun loadApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString(),
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    icon = it.loadIcon(pm),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Default dock: dialer, messages, browser, camera (whichever are available). */
    fun defaultDockPackages(): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        return intents.mapNotNull {
            pm.resolveActivity(it, 0)?.activityInfo?.packageName
        }.distinct()
    }

    fun launch(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
    }
}

