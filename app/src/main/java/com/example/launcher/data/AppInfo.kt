package com.example.launcher.data

import android.content.ComponentName
import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val component: ComponentName,
    val icon: Drawable,
) {
    val packageName: String get() = component.packageName
}

