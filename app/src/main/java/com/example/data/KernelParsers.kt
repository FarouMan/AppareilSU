package com.example.data

object KernelParsers {

    fun parseKernelRelease(procVersion: String, unameR: String = ""): String {
        val trimmedUname = unameR.trim()
        if (trimmedUname.isNotEmpty()) return trimmedUname
        val match = Regex("""Linux version\s+([^\s]+)""").find(procVersion)
        return match?.groupValues?.getOrNull(1) ?: procVersion.lineSequence().firstOrNull().orEmpty().trim()
    }

    fun isKernel419Series(release: String): Boolean = release.startsWith("4.19.")

    fun isExact419325(release: String): Boolean = release.startsWith("4.19.325")

    fun parseCompilerInfo(procVersion: String): String {
        val parenMatch = Regex("""\(([^)]*(?:clang|gcc|LLVM)[^)]*)\)""", RegexOption.IGNORE_CASE).find(procVersion)
        return parenMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    fun parseUptimeSeconds(procUptime: String): String {
        val secondsDouble = procUptime.trim().split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull() ?: return "--"
        val totalSeconds = secondsDouble.toLong().coerceAtLeast(0L)
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return buildString {
            if (days > 0) append("${days}j ")
            if (hours > 0 || days > 0) append("${hours}h ")
            append("${minutes}m ${secs}s")
        }.trim()
    }

    fun parseLoadAvg(procLoadAvg: String): Triple<Float, Float, Float> {
        val parts = procLoadAvg.trim().split(Regex("\\s+"))
        val l1 = parts.getOrNull(0)?.toFloatOrNull() ?: 0f
        val l5 = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
        val l15 = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
        return Triple(l1, l5, l15)
    }

    fun parseRunningTasks(procLoadAvg: String): String {
        val parts = procLoadAvg.trim().split(Regex("\\s+"))
        return parts.getOrNull(3) ?: "--"
    }

    /**
     * Parses `/proc/stat` into map of "cpu", "cpu0", "cpu1"... -> CpuTickSnapshot
     */
    fun parseProcStat(procStat: String): Map<String, CpuTickSnapshot> {
        val map = mutableMapOf<String, CpuTickSnapshot>()
        procStat.lineSequence().forEach { line ->
            if (!line.startsWith("cpu")) return@forEach
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 5) return@forEach
            val key = parts[0]
            val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (values.size >= 4) {
                val idle = values[3] + values.getOrElse(4) { 0L } // idle + iowait
                val total = values.sum()
                map[key] = CpuTickSnapshot(totalTicks = total, idleTicks = idle)
            }
        }
        return map
    }

    fun computeCpuUsagePercent(prev: CpuTickSnapshot?, curr: CpuTickSnapshot?): Float {
        if (prev == null || curr == null) return 0f
        val totalDiff = curr.totalTicks - prev.totalTicks
        val idleDiff = curr.idleTicks - prev.idleTicks
        if (totalDiff <= 0L) return 0f
        val busy = (totalDiff - idleDiff).coerceAtLeast(0L)
        return ((busy.toFloat() / totalDiff.toFloat()) * 100f).coerceIn(0f, 100f)
    }

    fun parseMemInfo(procMemInfo: String): MemoryTelemetry {
        val valuesKb = mutableMapOf<String, Long>()
        procMemInfo.lineSequence().forEach { line ->
            val colonIdx = line.indexOf(':')
            if (colonIdx <= 0) return@forEach
            val key = line.substring(0, colonIdx).trim()
            val numStr = line.substring(colonIdx + 1).trim().split(Regex("\\s+")).firstOrNull()
            val kb = numStr?.toLongOrNull()
            if (kb != null) {
                valuesKb[key] = kb
            }
        }
        val totalMb = (valuesKb["MemTotal"] ?: 0L) / 1024L
        val availableKb = valuesKb["MemAvailable"]
            ?: ((valuesKb["MemFree"] ?: 0L) + (valuesKb["Buffers"] ?: 0L) + (valuesKb["Cached"] ?: 0L))
        val availableMb = availableKb / 1024L
        val usedMb = (totalMb - availableMb).coerceAtLeast(0L)
        return MemoryTelemetry(
            totalMb = totalMb,
            availableMb = availableMb,
            usedMb = usedMb,
            cachedMb = (valuesKb["Cached"] ?: 0L) / 1024L,
            buffersMb = (valuesKb["Buffers"] ?: 0L) / 1024L,
            swapTotalMb = (valuesKb["SwapTotal"] ?: 0L) / 1024L,
            swapFreeMb = (valuesKb["SwapFree"] ?: 0L) / 1024L,
            slabReclaimableMb = (valuesKb["SReclaimable"] ?: 0L) / 1024L,
            dirtyMb = (valuesKb["Dirty"] ?: 0L) / 1024L
        )
    }

    /**
     * Parses `/sys/block/zram0/mm_stat` -> Pair(origDataMb, comprDataMb)
     */
    fun parseZramMmStat(mmStat: String): Pair<Long, Long> {
        val parts = mmStat.trim().split(Regex("\\s+"))
        val origBytes = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val comprBytes = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        return (origBytes / (1024L * 1024L)) to (comprBytes / (1024L * 1024L))
    }

    fun normalizeThermalTemp(rawTempStr: String): Float? {
        val raw = rawTempStr.trim().toFloatOrNull() ?: return null
        return when {
            raw > 1000f || raw < -1000f -> raw / 1000f
            raw > 200f -> raw / 10f
            else -> raw
        }
    }

    /**
     * Parses sysfs scheduler or algorithm list like: `noop deadline [cfq] bfq`
     */
    fun parseBracketedSelection(raw: String): Pair<String, List<String>> {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var active = ""
        val clean = tokens.map { token ->
            if (token.startsWith("[") && token.endsWith("]") && token.length > 2) {
                val stripped = token.substring(1, token.length - 1)
                active = stripped
                stripped
            } else {
                token
            }
        }
        if (active.isEmpty() && clean.isNotEmpty()) {
            active = clean.first()
        }
        return active to clean
    }

    fun parseWmSizeOutput(output: String): Pair<String, String?> {
        var physical = "--"
        var override: String? = null
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Physical size:", ignoreCase = true) -> {
                    physical = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("Override size:", ignoreCase = true) -> {
                    override = trimmed.substringAfter(":").trim()
                }
            }
        }
        return physical to override
    }

    fun parseWmDensityOutput(output: String): Pair<Int, Int?> {
        var physical = 0
        var override: Int? = null
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Physical density:", ignoreCase = true) -> {
                    physical = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                trimmed.startsWith("Override density:", ignoreCase = true) -> {
                    override = trimmed.substringAfter(":").trim().toIntOrNull()
                }
            }
        }
        return physical to override
    }

    fun parseDmesgLines(raw: String, maxLines: Int = 250): List<LogEntry> {
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(maxLines)
            .reversed()
            .map { line ->
                val bracketEnd = line.indexOf(']')
                val timestamp = if (line.startsWith("[") && bracketEnd > 1) {
                    line.substring(1, bracketEnd).trim()
                } else {
                    "kernel"
                }
                val body = if (bracketEnd > 0 && bracketEnd + 1 < line.length) {
                    line.substring(bracketEnd + 1).trim()
                } else {
                    line.trim()
                }
                val lower = body.lowercase()
                val level = when {
                    lower.contains("error") || lower.contains("err:") || lower.contains("fail") ||
                        lower.contains("panic") || lower.contains("oops") || lower.contains("avc: denied") -> "ERR"
                    lower.contains("warn") || lower.contains("throttl") -> "WARN"
                    else -> "INFO"
                }
                val tag = body.substringBefore(":", "kernel").take(24).trim().ifEmpty { "kernel" }
                LogEntry(
                    timestamp = timestamp,
                    level = level,
                    tag = tag,
                    message = body,
                    raw = line
                )
            }
    }

    fun parseLogcatLines(raw: String, maxLines: Int = 250): List<LogEntry> {
        val threadTimeRegex = Regex("""^\d{2}-\d{2}\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEFA])\s+([^:]+):\s*(.*)$""")
        return raw.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("---------") }
            .toList()
            .takeLast(maxLines)
            .reversed()
            .map { line ->
                val match = threadTimeRegex.find(line)
                if (match != null) {
                    val time = match.groupValues[1]
                    val lvlCode = match.groupValues[2]
                    val tag = match.groupValues[3].trim()
                    val msg = match.groupValues[4]
                    val level = when (lvlCode) {
                        "E", "F", "A" -> "ERR"
                        "W" -> "WARN"
                        "D", "V" -> "DEBUG"
                        else -> "INFO"
                    }
                    LogEntry(timestamp = time, level = level, tag = tag, message = msg, raw = line)
                } else {
                    LogEntry(timestamp = "--", level = "INFO", tag = "logcat", message = line, raw = line)
                }
            }
    }
}
