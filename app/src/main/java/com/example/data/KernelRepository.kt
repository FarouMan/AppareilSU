package com.example.data

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class KernelRepository(
    private val context: Context,
    private val shell: RootShellExecutor
) {
    private var lastCpuTicks: Map<String, CpuTickSnapshot> = emptyMap()

    suspend fun checkRootStatus(): RootStatus {
        return if (shell.probeRoot()) RootStatus.GRANTED else RootStatus.DENIED_OR_UNAVAILABLE
    }

    fun isRootGranted(): Boolean = shell.isRootAvailable

    suspend fun readKernelOverview(): KernelOverview {
        val procVersion = shell.readNode("/proc/version")
        val unameRes = shell.execute("uname -r", preferRoot = false, timeoutMs = 2000L)
        val unameR = if (unameRes.isSuccess) unameRes.stdout else System.getProperty("os.version").orEmpty()
        val release = KernelParsers.parseKernelRelease(procVersion, unameR)

        val uptimeRaw = shell.readNode("/proc/uptime")
        val loadAvgRaw = shell.readNode("/proc/loadavg")
        val (l1, l5, l15) = KernelParsers.parseLoadAvg(loadAvgRaw)
        val tasks = KernelParsers.parseRunningTasks(loadAvgRaw)

        val seLinuxRes = shell.execute("getenforce 2>/dev/null", preferRoot = shell.isRootAvailable, timeoutMs = 2000L)
        val seLinux = seLinuxRes.stdout.trim().ifEmpty {
            val enforceNode = shell.readNode("/sys/fs/selinux/enforce")
            when (enforceNode) {
                "1" -> "Enforcing"
                "0" -> "Permissive"
                else -> "Non disponible"
            }
        }

        val cmdline = shell.readNode("/proc/cmdline")
        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else {
            "--"
        }

        return KernelOverview(
            kernelRelease = release,
            fullProcVersion = procVersion.ifEmpty { "Linux $release" },
            architecture = System.getProperty("os.arch").orEmpty().ifEmpty { Build.SUPPORTED_ABIS.firstOrNull().orEmpty() },
            isKernel419Series = KernelParsers.isKernel419Series(release),
            isExact419325 = KernelParsers.isExact419325(release),
            compilerInfo = KernelParsers.parseCompilerInfo(procVersion),
            seLinuxMode = seLinux,
            uptimeFormatted = KernelParsers.parseUptimeSeconds(uptimeRaw),
            loadAvg1m = l1,
            loadAvg5m = l5,
            loadAvg15m = l15,
            runningTasks = tasks,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            securityPatch = securityPatch,
            bootCmdline = cmdline,
            rootMethodHint = shell.rootMethodHint
        )
    }

    suspend fun readCpuTelemetry(): CpuTelemetry = withContext(Dispatchers.IO) {
        val procStat = shell.readNode("/proc/stat")
        val currTicks = KernelParsers.parseProcStat(procStat)
        val totalUsage = KernelParsers.computeCpuUsagePercent(lastCpuTicks["cpu"], currTicks["cpu"])

        val cpuDir = File("/sys/devices/system/cpu")
        val coreIndices = cpuDir.listFiles()
            ?.mapNotNull { f ->
                val m = Regex("^cpu(\\d+)$").matchEntire(f.name)
                m?.groupValues?.get(1)?.toIntOrNull()
            }
            ?.sorted()
            ?: (0 until Runtime.getRuntime().availableProcessors()).toList()

        val cores = coreIndices.map { idx ->
            val onlineStr = shell.readNode("/sys/devices/system/cpu/cpu$idx/online")
            val isOnline = idx == 0 || onlineStr.isEmpty() || onlineStr == "1"
            val curFreq = shell.readNode("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_cur_freq").toLongOrNull()
                ?: shell.readNode("/sys/devices/system/cpu/cpu$idx/cpufreq/cpuinfo_cur_freq").toLongOrNull()
                ?: 0L
            val minFreq = shell.readNode("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_min_freq").toLongOrNull() ?: 0L
            val maxFreq = shell.readNode("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_max_freq").toLongOrNull()
                ?: shell.readNode("/sys/devices/system/cpu/cpu$idx/cpufreq/cpuinfo_max_freq").toLongOrNull()
                ?: 0L
            val gov = shell.readNode("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_governor").ifEmpty { "--" }
            val usage = KernelParsers.computeCpuUsagePercent(lastCpuTicks["cpu$idx"], currTicks["cpu$idx"])
            CpuCoreInfo(
                coreId = idx,
                isOnline = isOnline,
                currentFreqKhz = curFreq,
                minFreqKhz = minFreq,
                maxFreqKhz = maxFreq,
                governor = gov,
                usagePercent = usage
            )
        }

        if (currTicks.isNotEmpty()) {
            lastCpuTicks = currTicks
        }

        val clusters = discoverCpuClusters()

        // GPU clock check (Adreno kgsl or Mali)
        val adrenoClock = shell.readNode("/sys/class/kgsl/kgsl-3d0/gpuclk").toLongOrNull()
        val adrenoModel = shell.readNode("/sys/class/kgsl/kgsl-3d0/gpu_model")
        val gpuClockMhz = adrenoClock?.let { (it / 1_000_000L).toInt() }

        CpuTelemetry(
            totalUsagePercent = totalUsage,
            coreCount = cores.size,
            onlineCount = cores.count { it.isOnline },
            cores = cores,
            clusters = clusters,
            gpuClockMhz = gpuClockMhz,
            gpuModel = adrenoModel
        )
    }

    private suspend fun discoverCpuClusters(): List<CpuClusterInfo> {
        val cpufreqDir = File("/sys/devices/system/cpu/cpufreq")
        val policyDirs = cpufreqDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("policy") }
            ?.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: 0 }
            .orEmpty()

        if (policyDirs.isNotEmpty()) {
            return policyDirs.mapNotNull { dir ->
                val base = dir.absolutePath
                val gov = shell.readNode("$base/scaling_governor")
                val availGovs = shell.readNode("$base/scaling_available_governors")
                    .split(Regex("\\s+")).filter { it.isNotBlank() }
                val availFreqs = shell.readNode("$base/scaling_available_frequencies")
                    .split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }.sorted()
                val minFreq = shell.readNode("$base/scaling_min_freq").toLongOrNull() ?: 0L
                val maxFreq = shell.readNode("$base/scaling_max_freq").toLongOrNull() ?: 0L
                val affected = shell.readNode("$base/affected_cpus").ifEmpty { dir.name }
                if (gov.isEmpty() && availGovs.isEmpty()) null else {
                    CpuClusterInfo(
                        policyName = dir.name,
                        affectedCpus = affected,
                        currentGovernor = gov.ifEmpty { "--" },
                        availableGovernors = availGovs,
                        currentMinKhz = minFreq,
                        currentMaxKhz = maxFreq,
                        availableFreqsKhz = availFreqs
                    )
                }
            }
        }

        // Fallback to cpu0 cpufreq when policy* directories aren't exposed
        val cpu0Base = "/sys/devices/system/cpu/cpu0/cpufreq"
        val gov = shell.readNode("$cpu0Base/scaling_governor")
        val availGovs = shell.readNode("$cpu0Base/scaling_available_governors")
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        val availFreqs = shell.readNode("$cpu0Base/scaling_available_frequencies")
            .split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }.sorted()
        val minFreq = shell.readNode("$cpu0Base/scaling_min_freq").toLongOrNull() ?: 0L
        val maxFreq = shell.readNode("$cpu0Base/scaling_max_freq").toLongOrNull() ?: 0L
        return if (gov.isNotEmpty() || availGovs.isNotEmpty()) {
            listOf(
                CpuClusterInfo(
                    policyName = "cpu0",
                    affectedCpus = "0+",
                    currentGovernor = gov.ifEmpty { "--" },
                    availableGovernors = availGovs,
                    currentMinKhz = minFreq,
                    currentMaxKhz = maxFreq,
                    availableFreqsKhz = availFreqs
                )
            )
        } else {
            emptyList()
        }
    }

    suspend fun readMemoryTelemetry(): MemoryTelemetry {
        val memInfoRaw = shell.readNode("/proc/meminfo")
        var mem = if (memInfoRaw.isNotEmpty()) {
            KernelParsers.parseMemInfo(memInfoRaw)
        } else {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val total = mi.totalMem / (1024L * 1024L)
            val avail = mi.availMem / (1024L * 1024L)
            MemoryTelemetry(totalMb = total, availableMb = avail, usedMb = (total - avail).coerceAtLeast(0L))
        }

        val zramSizeBytes = shell.readNode("/sys/block/zram0/disksize").toLongOrNull() ?: 0L
        val zramMmStat = shell.readNode("/sys/block/zram0/mm_stat")
        val (origMb, comprMb) = if (zramMmStat.isNotEmpty()) {
            KernelParsers.parseZramMmStat(zramMmStat)
        } else {
            0L to 0L
        }
        val zramAlgoRaw = shell.readNode("/sys/block/zram0/comp_algorithm")
        val (activeAlgo, _) = KernelParsers.parseBracketedSelection(zramAlgoRaw)

        mem = mem.copy(
            zramDiskSizeMb = zramSizeBytes / (1024L * 1024L),
            zramOrigDataMb = origMb,
            zramComprDataMb = comprMb,
            zramAlgorithm = activeAlgo
        )
        return mem
    }

    suspend fun readThermalZones(): List<ThermalZone> = withContext(Dispatchers.IO) {
        val thermalDir = File("/sys/class/thermal")
        val zones = thermalDir.listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 999 }
            .orEmpty()

        zones.mapNotNull { zoneDir ->
            val type = shell.readNode("${zoneDir.absolutePath}/type").ifEmpty { zoneDir.name }
            val tempRaw = shell.readNode("${zoneDir.absolutePath}/temp")
            val celsius = KernelParsers.normalizeThermalTemp(tempRaw)
            if (celsius != null && celsius > -40f && celsius < 130f) {
                ThermalZone(name = type, tempCelsius = celsius)
            } else {
                null
            }
        }.take(16)
    }

    fun readBatteryTelemetry(): BatteryTelemetry {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val status = when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "En charge"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Décharge"
            BatteryManager.BATTERY_STATUS_FULL -> "Chargée"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Branchée (pause)"
            else -> "Inconnu"
        }
        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Bonne"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Surchauffe"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Usée"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Surtension"
            else -> "Normale"
        }
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty()
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (currentUa != null && currentUa != Int.MIN_VALUE) currentUa / 1000 else null

        return BatteryTelemetry(
            levelPercent = pct,
            tempCelsius = tempTenths / 10f,
            voltageMv = voltageMv,
            currentMa = currentMa,
            statusLabel = status,
            healthLabel = health,
            technology = tech
        )
    }

    suspend fun readKernelTunables(): KernelTunables {
        val swappiness = shell.readSysctl("vm.swappiness").toIntOrNull()
        val vfsCache = shell.readSysctl("vm.vfs_cache_pressure").toIntOrNull()
        val dirtyRatio = shell.readSysctl("vm.dirty_ratio").toIntOrNull()
        val dirtyBgRatio = shell.readSysctl("vm.dirty_background_ratio").toIntOrNull()

        val tcpActive = shell.readSysctl("net.ipv4.tcp_congestion_control")
        val tcpAvail = shell.readSysctl("net.ipv4.tcp_available_congestion_control")
            .split(Regex("\\s+")).filter { it.isNotBlank() }

        val candidateBlockDevs = listOf("sda", "mmcblk0", "nvme0n1", "dm-0", "vda")
        val blockDevices = candidateBlockDevs.mapNotNull { dev ->
            val schedRaw = shell.readNode("/sys/block/$dev/queue/scheduler")
            if (schedRaw.isEmpty()) null else {
                val (active, avail) = KernelParsers.parseBracketedSelection(schedRaw)
                val ra = shell.readNode("/sys/block/$dev/queue/read_ahead_kb").toIntOrNull() ?: 128
                BlockIoDevice(
                    deviceName = dev,
                    activeScheduler = active,
                    availableSchedulers = avail,
                    readAheadKb = ra
                )
            }
        }

        val stuneBoost = shell.readNode("/dev/stune/top-app/schedtune.boost").toIntOrNull()
        val stunePreferIdleRaw = shell.readNode("/dev/stune/top-app/schedtune.prefer_idle")
        val stuneAvailable = stuneBoost != null || stunePreferIdleRaw.isNotEmpty()
        val printk = shell.readNode("/proc/sys/kernel/printk")

        return KernelTunables(
            vmSwappiness = swappiness,
            vmVfsCachePressure = vfsCache,
            vmDirtyRatio = dirtyRatio,
            vmDirtyBackgroundRatio = dirtyBgRatio,
            tcpCongestionActive = tcpActive,
            tcpCongestionAvailable = tcpAvail,
            blockDevices = blockDevices,
            schedTuneTopAppBoost = stuneBoost,
            schedTunePreferIdle = stunePreferIdleRaw == "1",
            schedTuneAvailable = stuneAvailable,
            kernelLogLevel = printk
        )
    }

    suspend fun readInterfaceState(): InterfaceState {
        val wmSizeRes = shell.execute("wm size 2>/dev/null", preferRoot = shell.isRootAvailable, timeoutMs = 2000L)
        val wmDensityRes = shell.execute("wm density 2>/dev/null", preferRoot = shell.isRootAvailable, timeoutMs = 2000L)
        val (physRes, ovrRes) = KernelParsers.parseWmSizeOutput(wmSizeRes.stdout)
        val (physDens, ovrDens) = KernelParsers.parseWmDensityOutput(wmDensityRes.stdout)

        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display: Display? = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration

        val currentHz = display?.mode?.refreshRate ?: display?.refreshRate ?: 60f
        val supportedHz = display?.supportedModes
            ?.map { it.refreshRate }
            ?.distinctBy { it.toInt() }
            ?.sorted()
            ?: listOf(currentHz)

        val fallbackRes = if (physRes == "--") "${metrics.widthPixels}x${metrics.heightPixels}" else physRes
        val fallbackDens = if (physDens == 0) metrics.densityDpi else physDens

        val winScale = readGlobalSettingFloat("window_animation_scale", 1.0f)
        val transScale = readGlobalSettingFloat("transition_animation_scale", 1.0f)
        val animScale = readGlobalSettingFloat("animator_duration_scale", 1.0f)

        val uiModeMgr = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val nightLabel = when (uiModeMgr?.nightMode) {
            UiModeManager.MODE_NIGHT_YES -> "yes"
            UiModeManager.MODE_NIGHT_NO -> "no"
            else -> "auto"
        }

        val hdrStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val types = display?.hdrCapabilities?.supportedHdrTypes
            if (types != null && types.isNotEmpty()) {
                types.joinToString(", ") {
                    when (it) {
                        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                        else -> "HDR"
                    }
                }
            } else {
                "Standard SDR"
            }
        } else {
            "Standard SDR"
        }

        return InterfaceState(
            physicalResolution = fallbackRes,
            overrideResolution = ovrRes,
            physicalDensity = fallbackDens,
            overrideDensity = ovrDens,
            currentDensity = ovrDens ?: fallbackDens,
            currentRefreshRateHz = currentHz,
            supportedRefreshRatesHz = supportedHz,
            windowAnimationScale = winScale,
            transitionAnimationScale = transScale,
            animatorDurationScale = animScale,
            systemNightMode = nightLabel,
            smallestWidthDp = config.smallestScreenWidthDp,
            xdpi = metrics.xdpi,
            ydpi = metrics.ydpi,
            hdrCapabilities = hdrStr
        )
    }

    private suspend fun readGlobalSettingFloat(key: String, default: Float): Float {
        val res = shell.execute("settings get global $key 2>/dev/null", preferRoot = shell.isRootAvailable, timeoutMs = 2000L)
        return res.stdout.trim().toFloatOrNull() ?: default
    }

    // --- Rooted Write Operations ---

    suspend fun setCpuGovernor(policyName: String, governor: String): ShellResult {
        val path = if (policyName == "cpu0") {
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
        } else {
            "/sys/devices/system/cpu/cpufreq/$policyName/scaling_governor"
        }
        return shell.writeNode(path, governor)
    }

    suspend fun setClusterFrequencyBounds(policyName: String, minKhz: Long, maxKhz: Long): ShellResult {
        val base = if (policyName == "cpu0") {
            "/sys/devices/system/cpu/cpu0/cpufreq"
        } else {
            "/sys/devices/system/cpu/cpufreq/$policyName"
        }
        val minRes = shell.writeNode("$base/scaling_min_freq", minKhz.toString())
        val maxRes = shell.writeNode("$base/scaling_max_freq", maxKhz.toString())
        return if (minRes.isSuccess && maxRes.isSuccess) maxRes else if (!minRes.isSuccess) minRes else maxRes
    }

    suspend fun setIoScheduler(deviceName: String, scheduler: String): ShellResult {
        return shell.writeNode("/sys/block/$deviceName/queue/scheduler", scheduler)
    }

    suspend fun setIoReadAhead(deviceName: String, readAheadKb: Int): ShellResult {
        return shell.writeNode("/sys/block/$deviceName/queue/read_ahead_kb", readAheadKb.toString())
    }

    suspend fun setTcpCongestion(algorithm: String): ShellResult {
        return shell.writeSysctl("net.ipv4.tcp_congestion_control", algorithm)
    }

    suspend fun setSysctlParam(key: String, value: Int): ShellResult {
        return shell.writeSysctl(key, value.toString())
    }

    suspend fun setSchedTuneTopApp(boost: Int, preferIdle: Boolean): ShellResult {
        val r1 = shell.writeNode("/dev/stune/top-app/schedtune.boost", boost.coerceIn(0, 100).toString())
        val r2 = shell.writeNode("/dev/stune/top-app/schedtune.prefer_idle", if (preferIdle) "1" else "0")
        return if (r1.isSuccess) r2 else r1
    }

    suspend fun setDisplayDensity(dpi: Int): ShellResult {
        return shell.executeRootOnly("wm density ${dpi.coerceIn(120, 720)}")
    }

    suspend fun resetDisplayDensity(): ShellResult {
        return shell.executeRootOnly("wm density reset")
    }

    suspend fun setDisplayResolution(width: Int, height: Int): ShellResult {
        return shell.executeRootOnly("wm size ${width}x${height}")
    }

    suspend fun resetDisplayResolution(): ShellResult {
        return shell.executeRootOnly("wm size reset")
    }

    suspend fun setAllAnimationScales(scale: Float): ShellResult {
        val formatted = String.format(java.util.Locale.US, "%.2f", scale)
        return shell.executeRootOnly(
            "settings put global window_animation_scale $formatted && " +
                "settings put global transition_animation_scale $formatted && " +
                "settings put global animator_duration_scale $formatted"
        )
    }

    suspend fun setSystemNightMode(mode: String): ShellResult {
        return shell.executeRootOnly("cmd uimode night $mode")
    }

    suspend fun setPeakRefreshRate(hz: Float): ShellResult {
        val hzVal = String.format(java.util.Locale.US, "%.1f", hz)
        return shell.executeRootOnly(
            "settings put system peak_refresh_rate $hzVal && settings put system min_refresh_rate $hzVal"
        )
    }

    suspend fun fetchDmesg(): Pair<List<LogEntry>, String?> {
        val res = shell.execute("dmesg -T 2>/dev/null || dmesg 2>/dev/null", preferRoot = true, timeoutMs = 5000L)
        return if (res.stdout.isNotBlank()) {
            KernelParsers.parseDmesgLines(res.stdout) to null
        } else {
            emptyList<LogEntry>() to (res.stderr.ifEmpty { "Impossible de lire dmesg sans accès Root (su)." })
        }
    }

    suspend fun fetchLogcat(): Pair<List<LogEntry>, String?> {
        val res = shell.execute("logcat -d -v threadtime -t 200", preferRoot = shell.isRootAvailable, timeoutMs = 5000L)
        return if (res.stdout.isNotBlank()) {
            KernelParsers.parseLogcatLines(res.stdout) to null
        } else {
            emptyList<LogEntry>() to (res.stderr.ifEmpty { "Aucun journal logcat disponible." })
        }
    }

    suspend fun executeInteractiveCommand(command: String): ShellResult {
        return shell.execute(command, preferRoot = true, timeoutMs = 8000L)
    }
}
