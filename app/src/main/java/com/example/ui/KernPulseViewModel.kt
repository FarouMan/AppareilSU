package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BatteryTelemetry
import com.example.data.CpuTelemetry
import com.example.data.InterfaceState
import com.example.data.KernelOverview
import com.example.data.KernelRepository
import com.example.data.KernelTunables
import com.example.data.LogEntry
import com.example.data.MemoryTelemetry
import com.example.data.PreferencesRepository
import com.example.data.RootShellExecutor
import com.example.data.RootStatus
import com.example.data.ShellResult
import com.example.data.TelemetrySample
import com.example.data.ThemeMode
import com.example.data.ThermalZone
import com.example.data.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class LogSourceTab {
    DMESG,
    LOGCAT,
    CONSOLE
}

data class ShellTranscriptEntry(
    val command: String,
    val result: ShellResult,
    val timestampMs: Long = System.currentTimeMillis()
)

data class KernPulseUiState(
    val rootStatus: RootStatus = RootStatus.CHECKING,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val overview: KernelOverview = KernelOverview(),
    val cpu: CpuTelemetry = CpuTelemetry(),
    val memory: MemoryTelemetry = MemoryTelemetry(),
    val thermalZones: List<ThermalZone> = emptyList(),
    val battery: BatteryTelemetry = BatteryTelemetry(),
    val tunables: KernelTunables = KernelTunables(),
    val interfaceState: InterfaceState = InterfaceState(),
    val telemetryHistory: List<TelemetrySample> = emptyList(),
    val userPreferences: UserPreferences = UserPreferences(),
    // Logs & Shell
    val activeLogTab: LogSourceTab = LogSourceTab.DMESG,
    val dmesgEntries: List<LogEntry> = emptyList(),
    val dmesgError: String? = null,
    val logcatEntries: List<LogEntry> = emptyList(),
    val logcatError: String? = null,
    val logSearchQuery: String = "",
    val logLevelFilter: String = "ALL",
    val isLoadingLogs: Boolean = false,
    val shellHistory: List<ShellTranscriptEntry> = emptyList(),
    val isExecutingCommand: Boolean = false,
    // Display safety countdown
    val pendingDisplayRevertSeconds: Int? = null,
    val pendingRevertActionLabel: String = ""
)

class KernPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val shellExecutor = RootShellExecutor()
    private val kernelRepo = KernelRepository(application.applicationContext, shellExecutor)
    private val prefsRepo = PreferencesRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(KernPulseUiState())
    val uiState: StateFlow<KernPulseUiState> = _uiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private var pollingJob: Job? = null
    private var revertJob: Job? = null
    private var previousDensityBeforeTest: Int? = null
    private var previousSizeBeforeTest: String? = null

    init {
        viewModelScope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                _uiState.update { it.copy(userPreferences = prefs) }
                restartPollingLoop(prefs.autoRefreshEnabled, prefs.pollingIntervalMs)
            }
        }
        initializeAndProbe()
    }

    private fun initializeAndProbe() {
        viewModelScope.launch {
            _uiState.update { it.copy(rootStatus = RootStatus.CHECKING, isInitialLoading = true) }
            val rootStatus = kernelRepo.checkRootStatus()
            _uiState.update { it.copy(rootStatus = rootStatus) }
            refreshAllDiagnostics(includeTunablesAndInterface = true)
            loadActiveLogs()
            _uiState.update { it.copy(isInitialLoading = false) }
        }
    }

    fun requestRootCheck() {
        viewModelScope.launch {
            _uiState.update { it.copy(rootStatus = RootStatus.CHECKING) }
            val status = kernelRepo.checkRootStatus()
            _uiState.update { it.copy(rootStatus = status) }
            refreshAllDiagnostics(includeTunablesAndInterface = true)
            if (status == RootStatus.GRANTED) {
                _snackbarEvents.tryEmit("Accès Root (UID 0) accordé.")
                loadActiveLogs()
            } else {
                _snackbarEvents.tryEmit("Accès su refusé ou indisponible — mode lecture seule.")
            }
        }
    }

    private fun restartPollingLoop(autoRefresh: Boolean, intervalMs: Long) {
        pollingJob?.cancel()
        if (!autoRefresh) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMs)
                refreshLiveMetrics()
            }
        }
    }

    fun refreshAllDiagnostics(includeTunablesAndInterface: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            refreshLiveMetrics()
            if (includeTunablesAndInterface) {
                val tunables = kernelRepo.readKernelTunables()
                val iface = kernelRepo.readInterfaceState()
                _uiState.update {
                    it.copy(tunables = tunables, interfaceState = iface)
                }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun refreshLiveMetrics() {
        val overview = kernelRepo.readKernelOverview()
        val cpu = kernelRepo.readCpuTelemetry()
        val memory = kernelRepo.readMemoryTelemetry()
        val thermals = kernelRepo.readThermalZones()
        val battery = kernelRepo.readBatteryTelemetry()

        val maxTemp = thermals.maxOfOrNull { it.tempCelsius } ?: battery.tempCelsius
        val sample = TelemetrySample(
            timestampMs = System.currentTimeMillis(),
            cpuPercent = cpu.totalUsagePercent,
            maxTempCelsius = maxTemp,
            memoryUsedPercent = memory.usedPercent * 100f
        )

        _uiState.update { state ->
            val updatedHistory = (state.telemetryHistory + sample).takeLast(32)
            state.copy(
                overview = overview,
                cpu = cpu,
                memory = memory,
                thermalZones = thermals,
                battery = battery,
                telemetryHistory = updatedHistory
            )
        }
    }

    // --- Kernel Controls ---

    fun applyCpuGovernor(policyName: String, governor: String) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val res = kernelRepo.setCpuGovernor(policyName, governor)
            emitShellFeedback(res, "Gouverneur $policyName -> $governor")
            refreshAllDiagnostics(includeTunablesAndInterface = false)
        }
    }

    fun applyClusterFreqBounds(policyName: String, minKhz: Long, maxKhz: Long) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val safeMin = minKhz.coerceAtMost(maxKhz)
            val safeMax = maxKhz.coerceAtLeast(minKhz)
            val res = kernelRepo.setClusterFrequencyBounds(policyName, safeMin, safeMax)
            emitShellFeedback(res, "Fréquences $policyName : ${safeMin / 1000}–${safeMax / 1000} MHz")
            refreshAllDiagnostics(includeTunablesAndInterface = false)
        }
    }

    fun applyIoScheduler(deviceName: String, scheduler: String) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val res = kernelRepo.setIoScheduler(deviceName, scheduler)
            emitShellFeedback(res, "Ordonnanceur I/O ($deviceName) -> $scheduler")
            _uiState.update { it.copy(tunables = kernelRepo.readKernelTunables()) }
        }
    }

    fun applyIoReadAhead(deviceName: String, readAheadKb: Int) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val res = kernelRepo.setIoReadAhead(deviceName, readAheadKb)
            emitShellFeedback(res, "Read-Ahead ($deviceName) -> $readAheadKb Ko")
            _uiState.update { it.copy(tunables = kernelRepo.readKernelTunables()) }
        }
    }

    fun applyTcpCongestion(algorithm: String) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val res = kernelRepo.setTcpCongestion(algorithm)
            emitShellFeedback(res, "Congestion TCP -> $algorithm")
            _uiState.update { it.copy(tunables = kernelRepo.readKernelTunables()) }
        }
    }

    fun applySysctlValue(key: String, value: Int) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val res = kernelRepo.setSysctlParam(key, value)
            emitShellFeedback(res, "$key = $value")
            _uiState.update { it.copy(tunables = kernelRepo.readKernelTunables()) }
        }
    }

    fun applySchedTuneTopApp(boost: Int, preferIdle: Boolean) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val res = kernelRepo.setSchedTuneTopApp(boost, preferIdle)
            emitShellFeedback(res, "SchedTune top-app : boost=$boost, prefer_idle=$preferIdle")
            _uiState.update { it.copy(tunables = kernelRepo.readKernelTunables()) }
        }
    }

    // --- Interface & Display Controls ---

    fun applyDisplayDensityWithSafety(dpi: Int) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val currentIface = _uiState.value.interfaceState
            previousDensityBeforeTest = currentIface.overrideDensity
            val res = kernelRepo.setDisplayDensity(dpi)
            if (res.isSuccess) {
                _uiState.update { it.copy(interfaceState = kernelRepo.readInterfaceState()) }
                startSafetyRevertCountdown(
                    label = "Densité ${dpi} DPI appliquée",
                    onRevert = {
                        val prev = previousDensityBeforeTest
                        if (prev != null) kernelRepo.setDisplayDensity(prev) else kernelRepo.resetDisplayDensity()
                    }
                )
            } else {
                emitShellFeedback(res, "Densité DPI")
            }
        }
    }

    fun resetDisplayDensity() {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            revertJob?.cancel()
            val res = kernelRepo.resetDisplayDensity()
            emitShellFeedback(res, "Densité restaurée par défaut")
            _uiState.update {
                it.copy(
                    pendingDisplayRevertSeconds = null,
                    interfaceState = kernelRepo.readInterfaceState()
                )
            }
        }
    }

    fun applyDisplayResolutionWithSafety(width: Int, height: Int) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val currentIface = _uiState.value.interfaceState
            previousSizeBeforeTest = currentIface.overrideResolution
            val res = kernelRepo.setDisplayResolution(width, height)
            if (res.isSuccess) {
                _uiState.update { it.copy(interfaceState = kernelRepo.readInterfaceState()) }
                startSafetyRevertCountdown(
                    label = "Résolution ${width}x${height} appliquée",
                    onRevert = {
                        val prev = previousSizeBeforeTest
                        if (prev != null && prev.contains("x")) {
                            val parts = prev.split("x")
                            val w = parts[0].toIntOrNull()
                            val h = parts[1].toIntOrNull()
                            if (w != null && h != null) kernelRepo.setDisplayResolution(w, h)
                            else kernelRepo.resetDisplayResolution()
                        } else {
                            kernelRepo.resetDisplayResolution()
                        }
                    }
                )
            } else {
                emitShellFeedback(res, "Résolution d'écran")
            }
        }
    }

    fun resetDisplayResolution() {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            revertJob?.cancel()
            val res = kernelRepo.resetDisplayResolution()
            emitShellFeedback(res, "Résolution native restaurée")
            _uiState.update {
                it.copy(
                    pendingDisplayRevertSeconds = null,
                    interfaceState = kernelRepo.readInterfaceState()
                )
            }
        }
    }

    private fun startSafetyRevertCountdown(label: String, onRevert: suspend () -> Unit) {
        revertJob?.cancel()
        revertJob = viewModelScope.launch {
            for (sec in 10 downTo 1) {
                _uiState.update {
                    it.copy(
                        pendingDisplayRevertSeconds = sec,
                        pendingRevertActionLabel = label
                    )
                }
                delay(1000L)
            }
            onRevert()
            _uiState.update {
                it.copy(
                    pendingDisplayRevertSeconds = null,
                    interfaceState = kernelRepo.readInterfaceState()
                )
            }
            _snackbarEvents.tryEmit("Changement d'affichage annulé automatiquement (sécurité 10s).")
        }
    }

    fun confirmDisplayChanges() {
        revertJob?.cancel()
        _uiState.update { it.copy(pendingDisplayRevertSeconds = null) }
        _snackbarEvents.tryEmit("Configuration d'affichage confirmée.")
    }

    fun revertDisplayChangesNow() {
        viewModelScope.launch {
            revertJob?.cancel()
            kernelRepo.resetDisplayDensity()
            kernelRepo.resetDisplayResolution()
            _uiState.update {
                it.copy(
                    pendingDisplayRevertSeconds = null,
                    interfaceState = kernelRepo.readInterfaceState()
                )
            }
            _snackbarEvents.tryEmit("Affichage réinitialisé.")
        }
    }

    fun applyAnimationScales(scale: Float) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val res = kernelRepo.setAllAnimationScales(scale)
            emitShellFeedback(res, "Échelle d'animation -> ${scale}x")
            _uiState.update { it.copy(interfaceState = kernelRepo.readInterfaceState()) }
        }
    }

    fun applySystemNightMode(mode: String) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val res = kernelRepo.setSystemNightMode(mode)
            emitShellFeedback(res, "Mode nuit système -> $mode")
            _uiState.update { it.copy(interfaceState = kernelRepo.readInterfaceState()) }
        }
    }

    fun applyRefreshRate(hz: Float) {
        viewModelScope.launch {
            if (!ensureRootForWrite()) return@launch
            val res = kernelRepo.setPeakRefreshRate(hz)
            emitShellFeedback(res, "Taux de rafraîchissement -> ${hz.toInt()} Hz")
            _uiState.update { it.copy(interfaceState = kernelRepo.readInterfaceState()) }
        }
    }

    // --- App Material You Preferences ---

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDynamicColorEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoRefreshEnabled(enabled) }
    }

    fun setPollingIntervalMs(intervalMs: Long) {
        viewModelScope.launch { prefsRepo.setPollingIntervalMs(intervalMs) }
    }

    // --- Logs & Root Terminal ---

    fun selectLogTab(tab: LogSourceTab) {
        _uiState.update { it.copy(activeLogTab = tab) }
        if (tab != LogSourceTab.CONSOLE) {
            loadActiveLogs()
        }
    }

    fun updateLogSearch(query: String) {
        _uiState.update { it.copy(logSearchQuery = query) }
    }

    fun updateLogLevelFilter(level: String) {
        _uiState.update { it.copy(logLevelFilter = level) }
    }

    fun loadActiveLogs() {
        val currentTab = _uiState.value.activeLogTab
        if (currentTab == LogSourceTab.CONSOLE) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLogs = true) }
            when (currentTab) {
                LogSourceTab.DMESG -> {
                    val (entries, err) = kernelRepo.fetchDmesg()
                    _uiState.update {
                        it.copy(dmesgEntries = entries, dmesgError = err, isLoadingLogs = false)
                    }
                }
                LogSourceTab.LOGCAT -> {
                    val (entries, err) = kernelRepo.fetchLogcat()
                    _uiState.update {
                        it.copy(logcatEntries = entries, logcatError = err, isLoadingLogs = false)
                    }
                }
                LogSourceTab.CONSOLE -> {
                    _uiState.update { it.copy(isLoadingLogs = false) }
                }
            }
        }
    }

    fun runConsoleCommand(rawCommand: String) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExecutingCommand = true) }
            val result = kernelRepo.executeInteractiveCommand(trimmed)
            val entry = ShellTranscriptEntry(command = trimmed, result = result)
            _uiState.update { state ->
                state.copy(
                    shellHistory = (listOf(entry) + state.shellHistory).take(25),
                    isExecutingCommand = false
                )
            }
        }
    }

    fun clearConsoleHistory() {
        _uiState.update { it.copy(shellHistory = emptyList()) }
    }

    // --- Helpers ---

    private fun ensureRootForWrite(): Boolean {
        if (!kernelRepo.isRootGranted()) {
            _snackbarEvents.tryEmit("Action refusée : droits Root (su) requis.")
            return false
        }
        return true
    }

    private fun emitShellFeedback(result: ShellResult, successLabel: String) {
        if (result.isSuccess) {
            _snackbarEvents.tryEmit("Appliqué : $successLabel")
        } else {
            val detail = result.stderr.ifBlank { "code sortie ${result.exitCode}" }
            _snackbarEvents.tryEmit("Échec ($successLabel) : $detail")
        }
    }

    fun buildDiagnosticReport(): String {
        val s = _uiState.value
        return buildString {
            appendLine("=== RAPPORT DIAGNOSTIC KERNPULSE ===")
            appendLine("Appareil : ${s.overview.deviceModel}")
            appendLine("OS : ${s.overview.androidVersion} (Patch ${s.overview.securityPatch})")
            appendLine("Statut Root : ${s.rootStatus} — ${s.overview.rootMethodHint}")
            appendLine("Noyau : ${s.overview.kernelRelease} (${s.overview.architecture})")
            appendLine("Version complète : ${s.overview.fullProcVersion}")
            appendLine("SELinux : ${s.overview.seLinuxMode}")
            appendLine("Uptime : ${s.overview.uptimeFormatted}")
            appendLine("Load Avg : ${s.overview.loadAvg1m} / ${s.overview.loadAvg5m} / ${s.overview.loadAvg15m} (tâches ${s.overview.runningTasks})")
            appendLine()
            appendLine("--- CPU & GPU ---")
            appendLine("Charge CPU totale : ${s.cpu.totalUsagePercent.toInt()}% (${s.cpu.onlineCount}/${s.cpu.coreCount} cœurs actifs)")
            s.cpu.clusters.forEach { c ->
                appendLine("Cluster ${c.policyName} [CPUs ${c.affectedCpus}] : ${c.currentGovernor} (${c.currentMinKhz / 1000}-${c.currentMaxKhz / 1000} MHz)")
            }
            s.cpu.gpuClockMhz?.let { appendLine("Fréquence GPU : $it MHz (${s.cpu.gpuModel})") }
            appendLine()
            appendLine("--- MÉMOIRE & zRAM ---")
            appendLine("RAM : ${s.memory.usedMb}/${s.memory.totalMb} Mo (Dispo: ${s.memory.availableMb} Mo, Cache: ${s.memory.cachedMb} Mo)")
            appendLine("Swap : ${s.memory.swapTotalMb - s.memory.swapFreeMb}/${s.memory.swapTotalMb} Mo | zRAM : ${s.memory.zramDiskSizeMb} Mo (${s.memory.zramAlgorithm})")
            appendLine()
            appendLine("--- THERMIQUE & BATTERIE ---")
            appendLine("Batterie : ${s.battery.levelPercent}% (${s.battery.statusLabel}), ${s.battery.tempCelsius}°C, ${s.battery.voltageMv} mV")
            s.thermalZones.take(6).forEach { z ->
                appendLine("Zone ${z.name} : ${String.format(java.util.Locale.US, "%.1f", z.tempCelsius)}°C")
            }
            appendLine()
            appendLine("--- INTERFACE & AFFICHAGE ---")
            appendLine("Résolution : ${s.interfaceState.overrideResolution ?: s.interfaceState.physicalResolution} (Native: ${s.interfaceState.physicalResolution})")
            appendLine("Densité : ${s.interfaceState.currentDensity} DPI (Native: ${s.interfaceState.physicalDensity} DPI)")
            appendLine("Rafraîchissement : ${s.interfaceState.currentRefreshRateHz.toInt()} Hz")
            appendLine("Échelles anim : win=${s.interfaceState.windowAnimationScale}, trans=${s.interfaceState.transitionAnimationScale}, dur=${s.interfaceState.animatorDurationScale}")
        }
    }
}
