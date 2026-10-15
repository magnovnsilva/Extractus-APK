package com.example.data.model

import android.graphics.Bitmap

data class AppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val sourceDir: String,
    val splitSourceDirs: List<String> = emptyList(),
    val apkSize: Long,
    val isSystemApp: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val iconBitmap: Bitmap? = null,
    val permissions: List<String> = emptyList(),
    val hasLaunchIntent: Boolean = false
)

data class ExtractedApk(
    val id: String,
    val fileName: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val fileSize: Long,
    val filePath: String,
    val extractedAt: Long
)

enum class AppFilter {
    ALL,
    USER,
    SYSTEM,
    EXTRACTED
}

enum class SortOrder {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC,
    DATE_DESC,
    DATE_ASC
}
