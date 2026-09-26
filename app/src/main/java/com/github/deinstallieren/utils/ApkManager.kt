package com.github.deinstallieren.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ApkManager {

    suspend fun backupAppInternally(context: Context, appInfo: ApplicationInfo): Boolean = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, "backups/${appInfo.packageName}")
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        val apkPaths = mutableListOf<String>()
        apkPaths.add(appInfo.sourceDir)
        appInfo.splitSourceDirs?.let { apkPaths.addAll(it) }

        for (path in apkPaths) {
            val src = File(path)
            val dst = File(targetDir, src.name)
            // Usamos Shizuku para leer sin bloqueos de sandbox
            val (code, _) = ShizukuCommander.exec("cp \"${src.absolutePath}\" \"${dst.absolutePath}\" && chmod 644 \"${dst.absolutePath}\"")
            if (code != 0) return@withContext false
        }
        true
    }

    suspend fun exportToDownloads(context: Context, appInfo: ApplicationInfo, appName: String): Boolean = withContext(Dispatchers.IO) {
        val safeName = appName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val apkPaths = mutableListOf<String>()
        apkPaths.add(appInfo.sourceDir)
        appInfo.splitSourceDirs?.let { apkPaths.addAll(it) }

        val downloadsPath = "/sdcard/Download"
        ShizukuCommander.exec("mkdir -p $downloadsPath")

        if (apkPaths.size == 1) {
            // Un solo APK simple
            val targetFile = "$downloadsPath/${safeName}_${appInfo.packageName}.apk"
            val (code, _) = ShizukuCommander.exec("cp \"${apkPaths[0]}\" \"$targetFile\" && chmod 644 \"$targetFile\"")
            return@withContext code == 0
        } else {
            // App con Splits: crear ZIP
            val tempZip = File(context.cacheDir, "${safeName}_splits.zip")
            if (tempZip.exists()) tempZip.delete()

            try {
                ZipOutputStream(FileOutputStream(tempZip)).use { zipOut ->
                    for (path in apkPaths) {
                        val file = File(path)
                        val tempPart = File(context.cacheDir, file.name)
                        ShizukuCommander.exec("cp \"${file.absolutePath}\" \"${tempPart.absolutePath}\"")
                        if (tempPart.exists()) {
                            FileInputStream(tempPart).use { fi ->
                                val entry = ZipEntry(file.name)
                                zipOut.putNextEntry(entry)
                                fi.copyTo(zipOut)
                                zipOut.closeEntry()
                            }
                            tempPart.delete()
                        }
                    }
                }
                val targetZip = "$downloadsPath/${safeName}_splits.zip"
                val (code, _) = ShizukuCommander.exec("cp \"${tempZip.absolutePath}\" \"$targetZip\" && chmod 644 \"$targetZip\"")
                tempZip.delete()
                return@withContext code == 0
            } catch (e: Throwable) {
                return@withContext false
            }
        }
    }
}
