package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class RootShellExecutor {

    private val commonSuPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/vendor/bin/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su"
    )

    @Volatile
    var isRootAvailable: Boolean = false
        private set

    @Volatile
    var rootMethodHint: String = "Aucun binaire su détecté"
        private set

    suspend fun probeRoot(): Boolean = withContext(Dispatchers.IO) {
        val detectedPath = commonSuPaths.firstOrNull { File(it).exists() }
        val result = executeInternal(command = "id", useRoot = true, timeoutMs = 4000L)
        val granted = result.isSuccess && result.stdout.contains("uid=0")
        isRootAvailable = granted
        rootMethodHint = when {
            granted -> {
                val suPath = detectedPath ?: "su (PATH)"
                when {
                    suPath.contains("ksu") -> "KernelSU ($suPath)"
                    suPath.contains("ap") -> "APatch ($suPath)"
                    File("/data/adb/magisk").exists() || File("/sbin/.magisk").exists() -> "Magisk ($suPath)"
                    else -> "Root su ($suPath)"
                }
            }
            detectedPath != null -> "Binaire trouvé ($detectedPath), permission refusée"
            else -> "Appareil non rooté ou su absent du PATH"
        }
        granted
    }

    suspend fun execute(
        command: String,
        preferRoot: Boolean = true,
        timeoutMs: Long = 6000L
    ): ShellResult = withContext(Dispatchers.IO) {
        if (preferRoot) {
            val rootRes = executeInternal(command, useRoot = true, timeoutMs = timeoutMs)
            if (rootRes.isSuccess || isRootAvailable) {
                return@withContext rootRes
            }
        }
        executeInternal(command, useRoot = false, timeoutMs = timeoutMs)
    }

    suspend fun executeRootOnly(
        command: String,
        timeoutMs: Long = 6000L
    ): ShellResult = withContext(Dispatchers.IO) {
        executeInternal(command, useRoot = true, timeoutMs = timeoutMs)
    }

    suspend fun readNode(path: String): String = withContext(Dispatchers.IO) {
        val file = File(path)
        try {
            if (file.exists() && file.canRead()) {
                return@withContext file.readText().trim()
            }
        } catch (_: SecurityException) {
            // Fall through to su read if root is granted
        } catch (_: Exception) {
            // Fall through
        }

        if (isRootAvailable) {
            val res = executeInternal("cat '$path' 2>/dev/null", useRoot = true, timeoutMs = 2500L)
            if (res.isSuccess) return@withContext res.stdout.trim()
        }
        ""
    }

    suspend fun writeNode(path: String, value: String): ShellResult {
        val escapedPath = path.replace("'", "'\\''")
        val escapedVal = value.replace("'", "'\\''")
        val cmd = "chmod 644 '$escapedPath' 2>/dev/null; printf '%s' '$escapedVal' > '$escapedPath'"
        return executeRootOnly(cmd)
    }

    suspend fun readSysctl(key: String): String {
        val procPath = "/proc/sys/${key.replace('.', '/')}"
        val direct = readNode(procPath)
        if (direct.isNotEmpty()) return direct
        val res = execute("sysctl -n $key 2>/dev/null", preferRoot = isRootAvailable, timeoutMs = 2500L)
        return if (res.isSuccess) res.stdout.trim() else ""
    }

    suspend fun writeSysctl(key: String, value: String): ShellResult {
        val escapedVal = value.replace("'", "'\\''")
        return executeRootOnly("sysctl -w $key='$escapedVal'")
    }

    private suspend fun executeInternal(
        command: String,
        useRoot: Boolean,
        timeoutMs: Long
    ): ShellResult {
        val shellBin = if (useRoot) "su" else "sh"
        return try {
            val result = withTimeoutOrNull(timeoutMs) {
                val pb = ProcessBuilder(shellBin, "-c", command)
                val process = pb.start()
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                val code = process.waitFor()
                ShellResult(
                    exitCode = code,
                    stdout = stdout.trimEnd(),
                    stderr = stderr.trimEnd(),
                    usedRoot = useRoot
                )
            }
            result ?: ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = "Délai d'exécution dépassé (${timeoutMs}ms)",
                usedRoot = useRoot,
                timedOut = true
            )
        } catch (e: Exception) {
            ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Impossible de lancer $shellBin",
                usedRoot = useRoot
            )
        }
    }
}
