package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.model.AppItem
import com.example.data.model.ExtractedApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class AppManagerRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    fun getApkStorageDir(): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "apks")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun getInstalledApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val apps = mutableListOf<AppItem>()
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_PERMISSIONS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_PERMISSIONS
            }

            val packages: List<PackageInfo> = try {
                packageManager.getInstalledPackages(flags)
            } catch (e: Exception) {
                emptyList()
            }

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val sourceDir = appInfo.sourceDir ?: continue
                val apkFile = File(sourceDir)
                if (!apkFile.exists()) continue

                val appName = try {
                    appInfo.loadLabel(packageManager).toString()
                } catch (_: Exception) {
                    pkg.packageName
                }

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                val size = apkFile.length()

                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo.minSdkVersion
                } else {
                    24
                }

                val splitDirs = appInfo.splitSourceDirs?.toList() ?: emptyList()

                val permissions = pkg.requestedPermissions?.toList() ?: emptyList()

                val hasLaunchIntent = packageManager.getLaunchIntentForPackage(pkg.packageName) != null

                // Load icon as Bitmap safely
                val iconBitmap = try {
                    val drawable = appInfo.loadIcon(packageManager)
                    drawableToBitmap(drawable)
                } catch (_: Exception) {
                    null
                }

                val vName = pkg.versionName ?: "1.0"
                val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkg.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkg.versionCode.toLong()
                }

                apps.add(
                    AppItem(
                        packageName = pkg.packageName,
                        appName = appName,
                        versionName = vName,
                        versionCode = vCode,
                        minSdkVersion = minSdk,
                        targetSdkVersion = appInfo.targetSdkVersion,
                        sourceDir = sourceDir,
                        splitSourceDirs = splitDirs,
                        apkSize = size,
                        isSystemApp = isSystem,
                        firstInstallTime = pkg.firstInstallTime,
                        lastUpdateTime = pkg.lastUpdateTime,
                        iconBitmap = iconBitmap,
                        permissions = permissions,
                        hasLaunchIntent = hasLaunchIntent
                    )
                )
            }
        } catch (_: Exception) {
        }
        apps.sortedBy { it.appName.lowercase() }
    }

    suspend fun extractApk(
        app: AppItem,
        onProgress: ((progress: Float) -> Unit)? = null
    ): Result<ExtractedApk> = withContext(Dispatchers.IO) {
        try {
            // sourceDir é o APK base; splitSourceDirs contém as partes adicionais.
            val sourceFiles = listOf(File(app.sourceDir)) + app.splitSourceDirs.map(::File)
            val missingFile = sourceFiles.firstOrNull { !it.isFile }
            if (missingFile != null) {
                return@withContext Result.failure(
                    Exception("Arquivo APK não encontrado: ${missingFile.absolutePath}")
                )
            }

            val isSplitApk = app.splitSourceDirs.isNotEmpty()
            val storageDir = getApkStorageDir()
            val sanitizedName = app.appName
                .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                .trim('_')
                .ifEmpty { app.packageName.replace(".", "_") }
            val extension = if (isSplitApk) "zip" else "apk"
            val fileName = "${sanitizedName}_v${app.versionName}.$extension"
            val destFile = File(storageDir, fileName)

            val totalBytes = sourceFiles.sumOf { it.length() }.coerceAtLeast(1L)
            var bytesCopied = 0L
            val buffer = ByteArray(64 * 1024)

            fun copyWithProgress(input: java.io.InputStream, output: java.io.OutputStream) {
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesCopied += read
                    onProgress?.invoke((bytesCopied.toFloat() / totalBytes).coerceIn(0f, 1f))
                }
            }

            if (isSplitApk) {
                ZipOutputStream(FileOutputStream(destFile)).use { zip ->
                    sourceFiles.forEachIndexed { index, sourceFile ->
                        val entryName = if (index == 0) "base.apk" else "split_${index}.apk"
                        zip.putNextEntry(ZipEntry(entryName))
                        FileInputStream(sourceFile).use { input ->
                            copyWithProgress(input, zip)
                        }
                        zip.closeEntry()
                    }
                }
            } else {
                FileInputStream(sourceFiles.first()).use { input ->
                    FileOutputStream(destFile).use { output ->
                        copyWithProgress(input, output)
                    }
                }
            }
            onProgress?.invoke(1f)

            val extracted = ExtractedApk(
                id = UUID.randomUUID().toString(),
                fileName = destFile.name,
                appName = app.appName,
                packageName = app.packageName,
                versionName = app.versionName,
                fileSize = destFile.length(),
                filePath = destFile.absolutePath,
                extractedAt = System.currentTimeMillis()
            )

            Result.success(extracted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadExtractedApks(): List<ExtractedApk> = withContext(Dispatchers.IO) {
        val dir = getApkStorageDir()
        val files = dir.listFiles { file ->
            file.extension.equals("apk", ignoreCase = true) ||
                file.extension.equals("zip", ignoreCase = true)
        } ?: emptyArray()

        files.map { file ->
            var appName = file.nameWithoutExtension
            var pkgName = ""
            var verName = ""
            var metadataApk: File? = file
            var temporaryBaseApk: File? = null

            // Para um ZIP de splits, o Android PackageManager precisa ler o base.apk.
            if (file.extension.equals("zip", ignoreCase = true)) {
                try {
                    val temp = File.createTempFile("extractus-base-", ".apk", context.cacheDir)
                    temporaryBaseApk = temp
                    ZipFile(file).use { archive ->
                        val baseEntry = archive.getEntry("base.apk")
                            ?: throw IllegalArgumentException("ZIP sem base.apk")
                        archive.getInputStream(baseEntry).use { input ->
                            FileOutputStream(temp).use { output -> input.copyTo(output) }
                        }
                    }
                    metadataApk = temp
                } catch (_: Exception) {
                    temporaryBaseApk?.delete()
                    metadataApk = null
                }
            }

            try {
                metadataApk?.let { apkForMetadata ->
                    val archiveInfo = packageManager.getPackageArchiveInfo(apkForMetadata.absolutePath, 0)
                    if (archiveInfo != null) {
                        pkgName = archiveInfo.packageName ?: ""
                        verName = archiveInfo.versionName ?: ""
                        val appInfo = archiveInfo.applicationInfo
                        if (appInfo != null) {
                            appInfo.sourceDir = apkForMetadata.absolutePath
                            appInfo.publicSourceDir = apkForMetadata.absolutePath
                            val label = appInfo.loadLabel(packageManager).toString()
                            if (label.isNotEmpty()) appName = label
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                temporaryBaseApk?.delete()
            }

            ExtractedApk(
                id = file.name,
                fileName = file.name,
                appName = appName,
                packageName = pkgName,
                versionName = verName,
                fileSize = file.length(),
                filePath = file.absolutePath,
                extractedAt = file.lastModified()
            )
        }.sortedByDescending { it.extractedAt }
    }

    suspend fun deleteExtractedApk(apk: ExtractedApk): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(apk.filePath)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    fun createShareIntent(apkFiles: List<File>): Intent {
        val authority = "${context.packageName}.fileprovider"

        if (apkFiles.size == 1) {
            val file = apkFiles.first()
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            return Intent(Intent.ACTION_SEND).apply {
                type = if (file.extension.equals("zip", ignoreCase = true)) {
                    "application/zip"
                } else {
                    "application/vnd.android.package-archive"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Compartilhar APK: ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            val uris = ArrayList<Uri>()
            for (file in apkFiles) {
                uris.add(FileProvider.getUriForFile(context, authority, file))
            }

            return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "Compartilhar ${apkFiles.size} APKs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun createInstallIntent(apkFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128

        val targetWidth = width.coerceIn(96, 192)
        val targetHeight = height.coerceIn(96, 192)

        val bitmap = Bitmap.createBitmap(
            targetWidth,
            targetHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
