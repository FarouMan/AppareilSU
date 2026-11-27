package com.example.data

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class RootStatus {
    CHECKING,
    GRANTED,
    DENIED_OR_UNAVAILABLE
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val usedRoot: Boolean,
    val timedOut: Boolean = false
) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut
}

data class KernelOverview(
    val kernelRelease: String = "",
    val fullProcVersion: String = "",
    val architecture: String = "",
    val isKernel419Series: Boolean = false,
    val isExact419325: Boolean = false,
    val compilerInfo: String = "",
    val seLinuxMode: String = "Inconnu",
    val uptimeFormatted: String = "--",
    val loadAvg1m: Float = 0f,
    val loadAvg5m: Float = 0f,
    val loadAvg15m: Float = 0f,
    val runningTasks: String = "--",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val securityPatch: String = "",
    val bootCmdline: String = "",
    val rootMethodHint: String = ""
)

data class CpuTickSnapshot(
    val totalTicks: Long,
    val idleTicks: Long
)

data class CpuCoreInfo(
    val coreId: Int,
    val isOnline: Boolean,
    val currentFreqKhz: Long,
    val minFreqKhz: Long,
    val maxFreqKhz: Long,
    val governor: String,
    val usagePercent: Float
)

data class CpuClusterInfo(
    val policyName: String,
    val affectedCpus: String,
    val currentGovernor: String,
    val availableGovernors: List<String>,
    val currentMinKhz: Long,
    val currentMaxKhz: Long,
    val availableFreqsKhz: List<Long>
)

data class CpuTelemetry(
    val totalUsagePercent: Float = 0f,
    val coreCount: Int = 0,
    val onlineCount: Int = 0,
    val cores: List<CpuCoreInfo> = emptyList(),
    val clusters: List<CpuClusterInfo> = emptyList(),
    val gpuClockMhz: Int? = null,
    val gpuModel: String = ""
)

data class MemoryTelemetry(
    val totalMb: Long = 0L,
    val availableMb: Long = 0L,
    val usedMb: Long = 0L,
    val cachedMb: Long = 0L,
    val buffersMb: Long = 0L,
    val swapTotalMb: Long = 0L,
    val swapFreeMb: Long = 0L,
    val slabReclaimableMb: Long = 0L,
    val dirtyMb: Long = 0L,
    val zramDiskSizeMb: Long = 0L,
    val zramOrigDataMb: Long = 0L,
    val zramComprDataMb: Long = 0L,
    val zramAlgorithm: String = ""
) {
    val usedPercent: Float
        get() = if (totalMb > 0) (usedMb.toFloat() / totalMb.toFloat()).coerceIn(0f, 1f) else 0f
}

data class ThermalZone(
    val name: String,
    val tempCelsius: Float
)

data class BatteryTelemetry(
    val levelPercent: Int = 0,
    val tempCelsius: Float = 0f,
    val voltageMv: Int = 0,
    val currentMa: Int? = null,
    val statusLabel: String = "Inconnu",
    val healthLabel: String = "Inconnu",
    val technology: String = ""
)

data class BlockIoDevice(
    val deviceName: String,
    val activeScheduler: String,
    val availableSchedulers: List<String>,
    val readAheadKb: Int
)

data class KernelTunables(
    val vmSwappiness: Int? = null,
    val vmVfsCachePressure: Int? = null,
    val vmDirtyRatio: Int? = null,
    val vmDirtyBackgroundRatio: Int? = null,
    val tcpCongestionActive: String = "",
    val tcpCongestionAvailable: List<String> = emptyList(),
    val blockDevices: List<BlockIoDevice> = emptyList(),
    val schedTuneTopAppBoost: Int? = null,
    val schedTunePreferIdle: Boolean? = null,
    val schedTuneAvailable: Boolean = false,
    val kernelLogLevel: String = ""
)

data class InterfaceState(
    val physicalResolution: String = "--",
    val overrideResolution: String? = null,
    val physicalDensity: Int = 0,
    val overrideDensity: Int? = null,
    val currentDensity: Int = 0,
    val currentRefreshRateHz: Float = 60f,
    val supportedRefreshRatesHz: List<Float> = emptyList(),
    val windowAnimationScale: Float = 1.0f,
    val transitionAnimationScale: Float = 1.0f,
    val animatorDurationScale: Float = 1.0f,
    val systemNightMode: String = "auto",
    val smallestWidthDp: Int = 0,
    val xdpi: Float = 0f,
    val ydpi: Float = 0f,
    val hdrCapabilities: String = "Standard SDR"
)

data class LogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val raw: String
)

data class TelemetrySample(
    val timestampMs: Long,
    val cpuPercent: Float,
    val maxTempCelsius: Float,
    val memoryUsedPercent: Float
)
