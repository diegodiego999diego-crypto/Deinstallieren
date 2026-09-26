package com.github.deinstallieren.utils

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object ShizukuCommander {

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    suspend fun exec(command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            // Acceso por reflexión al proceso remoto de Shizuku
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val exitCode = process.waitFor()
            Pair(exitCode, output.toString().trim())
        } catch (e: Throwable) {
            Pair(-1, e.message ?: "Error desconocido")
        }
    }

    suspend fun uninstallSystem(packageName: String): Boolean {
        val (exitCode, _) = exec("pm uninstall --user 0 $packageName")
        return exitCode == 0
    }

    suspend fun uninstallUser(packageName: String): Boolean {
        val (exitCode, _) = exec("pm uninstall -k --user 0 $packageName")
        return exitCode == 0
    }

    suspend fun disable(packageName: String): Boolean {
        val (exitCode, _) = exec("pm disable-user --user 0 $packageName")
        return exitCode == 0
    }

    suspend fun enable(packageName: String): Boolean {
        val (exitCode, _) = exec("pm enable $packageName")
        return exitCode == 0
    }

    suspend fun suspendApp(packageName: String): Boolean {
        val (exitCode, _) = exec("pm suspend $packageName")
        return exitCode == 0
    }

    suspend fun reinstallExisting(packageName: String): Boolean {
        val (exitCode, _) = exec("cmd package install-existing $packageName")
        return exitCode == 0
    }

    suspend fun reinstallFromBackupSession(context: Context, packageName: String): Boolean = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, "backups/$packageName")
        if (!backupDir.exists()) return@withContext false

        val apks = backupDir.listFiles { file -> file.extension == "apk" } ?: return@withContext false
        if (apks.isEmpty()) return@withContext false

        val tempDir = "/data/local/tmp/restore_$packageName"
        exec("mkdir -p $tempDir")

        try {
            for (apk in apks) {
                exec("cp \"${apk.absolutePath}\" \"$tempDir/${apk.name}\"")
            }

            val (createCode, createOut) = exec("pm install-create -r -d -i com.android.vending")
            if (createCode != 0) return@withContext false

            val sessionId = "\\[created install session (\\d+)\\]".toRegex()
                .find(createOut)?.groupValues?.get(1) ?: return@withContext false

            for (apk in apks) {
                val writeCmd = "pm install-write $sessionId \"${apk.name}\" \"$tempDir/${apk.name}\""
                val (writeCode, _) = exec(writeCmd)
                if (writeCode != 0) {
                    exec("pm install-abandon $sessionId")
                    return@withContext false
                }
            }

            val commitCmd = "pm install-commit $sessionId; cmd appops set $packageName ACCESS_RESTRICTED_SETTINGS allow"
            val (commitCode, _) = exec(commitCmd)

            if (commitCode == 0) {
                backupDir.deleteRecursively()
                return@withContext true
            }
            return@withContext false
        } finally {
            exec("rm -rf $tempDir")
        }
    }
}
