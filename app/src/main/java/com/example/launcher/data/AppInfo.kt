package com.example.launcher.data

import android.content.ComponentName
import android.graphics.drawable.Drawable

enum class AppCategory(val title: String, val colorHex: Long, val glowHex: Long) {
    SYSTEM("SYSTEM", 0xFFDDA15E, 0x66DDA15E),
    MEDIA("MEDIA", 0xFF4FC3F7, 0x660288D1),
    SOCIAL("SOCIAL", 0xFFFFB74D, 0x66F57C00),
    TOOLS("UTILITIES", 0xFFE0E0E0, 0x40FFFFFF)
}

data class AppInfo(
    val label: String,
    val component: ComponentName,
    val icon: Drawable,
    val category: AppCategory = AppCategory.TOOLS
) {
    val packageName: String get() = component.packageName
}
