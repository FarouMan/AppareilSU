package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BlockIoDevice
import com.example.data.CpuClusterInfo
import com.example.data.RootStatus
import com.example.ui.KernPulseUiState
import com.example.ui.components.QuickSelectableChip
import com.example.ui.components.SectionHeader
import com.example.ui.theme.MonoCodeStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KernelControlScreen(
    state: KernPulseUiState,
    onSelectGovernor: (String, String) -> Unit,
    onApplyClusterBounds: (String, Long, Long) -> Unit,
    onSelectIoScheduler: (String, String) -> Unit,
    onSelectIoReadAhead: (String, Int) -> Unit,
    onSelectTcpCongestion: (String) -> Unit,
    onApplySysctl: (String, Int) -> Unit,
    onApplySchedTune: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val rootGranted = state.rootStatus == RootStatus.GRANTED

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("kernel_control_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Contrôle Noyau Linux 4.19",
                subtitle = if (rootGranted) {
                    "Écriture directe sur sysfs & sysctl via UID 0 (su)"
                } else {
                    "Mode lecture seule — accordez Root (su) pour modifier les paramètres"
                }
            )
        }

        // CPU Clusters & Governors
        if (state.cpu.clusters.isNotEmpty()) {
            items(state.cpu.clusters, key = { it.policyName }) { cluster ->
                CpuClusterControlCard(
                    cluster = cluster,
                    enabled = rootGranted,
                    onSelectGovernor = { gov -> onSelectGovernor(cluster.policyName, gov) },
                    onApplyFreqBounds = { minK, maxK -> onApplyClusterBounds(cluster.policyName, minK, maxK) }
                )
            }
        } else {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aucune politique cpufreq modifiable détectée sur cet environnement.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // Kernel 4.19 EAS / SchedTune card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = "SchedTune EAS",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "EAS / SchedTune (Kernel 4.19)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (state.tunables.schedTuneAvailable) {
                        var boostSlider by remember(state.tunables.schedTuneTopAppBoost) {
                            mutableFloatStateOf((state.tunables.schedTuneTopAppBoost ?: 0).toFloat())
                        }
                        var preferIdle by remember(state.tunables.schedTunePreferIdle) {
                            mutableIntStateOf(if (state.tunables.schedTunePreferIdle == true) 1 else 0)
                        }
                        Text(
                            text = "/dev/stune/top-app/schedtune.boost : ${boostSlider.roundToInt()}%",
                            style = MonoCodeStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = boostSlider,
                            onValueChange = { boostSlider = it },
                            valueRange = 0f..50f,
                            enabled = rootGranted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "schedtune.prefer_idle (top-app)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = preferIdle == 1,
                                onCheckedChange = { preferIdle = if (it) 1 else 0 },
                                enabled = rootGranted
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onApplySchedTune(boostSlider.roundToInt(), preferIdle == 1) },
                            enabled = rootGranted,
                            modifier = Modifier.testTag("apply_schedtune_button")
                        ) {
                            Text("Appliquer SchedTune")
                        }
                    } else {
                        Text(
                            text = "Nœud /dev/stune/top-app non monté sur ce noyau (uclamp ou noyau sans EAS stune).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Block I/O Schedulers
        if (state.tunables.blockDevices.isNotEmpty()) {
            items(state.tunables.blockDevices, key = { it.deviceName }) { dev ->
                BlockIoCard(
                    device = dev,
                    enabled = rootGranted,
                    onSelectScheduler = { sched -> onSelectIoScheduler(dev.deviceName, sched) },
                    onSelectReadAhead = { ra -> onSelectIoReadAhead(dev.deviceName, ra) }
                )
            }
        }

        // VM Memory Sysctl Tuning
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = "Mémoire virtuelle sysctl",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Paramètres VM & Cache (sysctl)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    SysctlPresetRow(
                        title = "vm.swappiness",
                        currentValue = state.tunables.vmSwappiness,
                        presets = listOf(10, 30, 60, 100),
                        enabled = rootGranted,
                        onSelect = { onApplySysctl("vm.swappiness", it) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    SysctlPresetRow(
                        title = "vm.vfs_cache_pressure",
                        currentValue = state.tunables.vmVfsCachePressure,
                        presets = listOf(50, 100, 150, 200),
                        enabled = rootGranted,
                        onSelect = { onApplySysctl("vm.vfs_cache_pressure", it) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    SysctlPresetRow(
                        title = "vm.dirty_ratio",
                        currentValue = state.tunables.vmDirtyRatio,
                        presets = listOf(10, 20, 30, 40),
                        enabled = rootGranted,
                        onSelect = { onApplySysctl("vm.dirty_ratio", it) }
                    )
                }
            }
        }

        // TCP Congestion Control
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NetworkCheck,
                            contentDescription = "TCP Congestion",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Contrôle de Congestion TCP",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Actif : ${state.tunables.tcpCongestionActive.ifEmpty { "Inconnu" }}",
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (state.tunables.tcpCongestionAvailable.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.tunables.tcpCongestionAvailable.forEach { algo ->
                                QuickSelectableChip(
                                    label = algo,
                                    selected = algo == state.tunables.tcpCongestionActive,
                                    enabled = rootGranted,
                                    onClick = { onSelectTcpCongestion(algo) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CpuClusterControlCard(
    cluster: CpuClusterInfo,
    enabled: Boolean,
    onSelectGovernor: (String) -> Unit,
    onApplyFreqBounds: (Long, Long) -> Unit
) {
    val freqs = cluster.availableFreqsKhz
    var selectedMinIdx by remember(cluster.policyName, cluster.currentMinKhz, freqs) {
        val idx = freqs.indexOfFirst { it >= cluster.currentMinKhz }.coerceAtLeast(0)
        mutableIntStateOf(idx)
    }
    var selectedMaxIdx by remember(cluster.policyName, cluster.currentMaxKhz, freqs) {
        val idx = freqs.indexOfLast { it <= cluster.currentMaxKhz }.let {
            if (it >= 0) it else (freqs.lastIndex).coerceAtLeast(0)
        }
        mutableIntStateOf(idx)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = "Cluster CPU",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Cluster ${cluster.policyName} (CPUs ${cluster.affectedCpus})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Gouverneur actif : ${cluster.currentGovernor}",
                            style = MonoCodeStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (cluster.availableGovernors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cluster.availableGovernors.forEach { gov ->
                        QuickSelectableChip(
                            label = gov,
                            selected = gov == cluster.currentGovernor,
                            enabled = enabled,
                            onClick = { onSelectGovernor(gov) }
                        )
                    }
                }
            }

            if (freqs.size >= 2) {
                Spacer(modifier = Modifier.height(14.dp))
                val minKhz = freqs.getOrElse(selectedMinIdx) { cluster.currentMinKhz }
                val maxKhz = freqs.getOrElse(selectedMaxIdx) { cluster.currentMaxKhz }

                Text(
                    text = "Plage fréquences : ${minKhz / 1000} MHz — ${maxKhz / 1000} MHz",
                    style = MonoCodeStyle,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fréquence Min (${minKhz / 1000} MHz)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = selectedMinIdx.toFloat(),
                    onValueChange = { v ->
                        val i = v.roundToInt().coerceIn(0, freqs.lastIndex)
                        selectedMinIdx = i
                        if (selectedMaxIdx < i) selectedMaxIdx = i
                    },
                    valueRange = 0f..freqs.lastIndex.toFloat(),
                    steps = (freqs.size - 2).coerceAtLeast(0),
                    enabled = enabled
                )

                Text(
                    text = "Fréquence Max (${maxKhz / 1000} MHz)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = selectedMaxIdx.toFloat(),
                    onValueChange = { v ->
                        val i = v.roundToInt().coerceIn(0, freqs.lastIndex)
                        selectedMaxIdx = i
                        if (selectedMinIdx > i) selectedMinIdx = i
                    },
                    valueRange = 0f..freqs.lastIndex.toFloat(),
                    steps = (freqs.size - 2).coerceAtLeast(0),
                    enabled = enabled
                )

                Button(
                    onClick = { onApplyFreqBounds(minKhz, maxKhz) },
                    enabled = enabled,
                    modifier = Modifier.testTag("apply_freq_${cluster.policyName}")
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Appliquer fréquences")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockIoCard(
    device: BlockIoDevice,
    enabled: Boolean,
    onSelectScheduler: (String) -> Unit,
    onSelectReadAhead: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = "Ordonnanceur I/O",
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Stockage /dev/${device.deviceName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Scheduler : ${device.activeScheduler} • Read-Ahead : ${device.readAheadKb} Ko",
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                device.availableSchedulers.forEach { sched ->
                    QuickSelectableChip(
                        label = sched,
                        selected = sched == device.activeScheduler,
                        enabled = enabled,
                        onClick = { onSelectScheduler(sched) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Read-Ahead (Ko)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(128, 256, 512, 1024, 2048).forEach { kb ->
                    QuickSelectableChip(
                        label = "${kb}K",
                        selected = device.readAheadKb == kb,
                        enabled = enabled,
                        onClick = { onSelectReadAhead(kb) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SysctlPresetRow(
    title: String,
    currentValue: Int?,
    presets: List<Int>,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MonoCodeStyle, fontWeight = FontWeight.SemiBold)
            Text(
                text = currentValue?.toString() ?: "--",
                style = MonoCodeStyle,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { value ->
                QuickSelectableChip(
                    label = value.toString(),
                    selected = currentValue == value,
                    enabled = enabled,
                    onClick = { onSelect(value) }
                )
            }
        }
    }
}
