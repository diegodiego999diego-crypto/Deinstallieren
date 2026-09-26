package com.github.deinstallieren.model

import android.graphics.drawable.Drawable

data class AppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    val isChipset: Boolean,
    var isEnabled: Boolean = true,
    var isSuspended: Boolean = false
) {
    companion object {
        private val CHIPSET_PREFIXES = listOf(
            "com.mediatek.",
            "com.qualcomm.",
            "com.qti.",
            "com.unisoc.",
            "com.sprd."
        )

        fun isChipsetPackage(packageName: String): Boolean {
            return CHIPSET_PREFIXES.any { packageName.startsWith(it, ignoreCase = true) }
        }
    }
}
