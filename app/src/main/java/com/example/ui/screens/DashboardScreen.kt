package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.KernPulseUiState
import com.example.ui.components.CoreFrequencyTile
import com.example.ui.components.CpuPulseChart
import com.example.ui.components.RootStatusBanner
import com.example.ui.components.SectionHeader
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.MonoMetricStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    state: KernPulseUiState,
    onRequestRoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCmdline by remember { mutableStateOf(false) }
    val maxTemp = state.thermalZones.maxOfOrNull { it.tempCelsius } ?: state.battery.tempCelsius

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            RootStatusBanner(
                rootStatus = state.rootStatus,
                hint = state.overview.rootMethodHint,
                kernelRelease = state.overview.kernelRelease,
                is419Series = state.overview.isKernel419Series,
                isExact419325 = state.overview.isExact419325,
                onRequestRoot = onRequestRoot
            )
        }

        // Hero Kernel Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeveloperBoard,
                                    contentDescription = "Noyau Linux",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = state.overview.deviceModel.ifEmpty { "Appareil Android" },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${state.overview.androidVersion} • ${state.overview.architecture}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = "SELinux",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = state.overview.seLinuxMode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.overview.fullProcVersion.ifEmpty { "Lecture de /proc/version..." },
                            style = MonoCodeStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoMetricItem(
                            label = "UPTIME",
                            value = state.overview.uptimeFormatted,
                            modifier = Modifier.weight(1f)
                        )
                        InfoMetricItem(
                            label = "LOAD AVG",
                            value = "${String.format(java.util.Locale.US, "%.2f", state.overview.loadAvg1m)} / ${String.format(java.util.Locale.US, "%.2f", state.overview.loadAvg5m)}",
                            modifier = Modifier.weight(1f)
                        )
                        InfoMetricItem(
                            label = "TÂCHES",
                            value = state.overview.runningTasks,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (state.overview.bootCmdline.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCmdline = !showCmdline }
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Paramètres de boot (/proc/cmdline)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (showCmdline) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = "Afficher cmdline",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        AnimatedVisibility(visible = showCmdline) {
                            Text(
                                text = state.overview.bootCmdline,
                                style = MonoCodeStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Real-time Oscilloscope Chart
        item {
            CpuPulseChart(
                samples = state.telemetryHistory,
                currentCpuPercent = state.cpu.totalUsagePercent,
                currentTempCelsius = maxTemp
            )
        }

        // CPU Cores Section
        item {
            SectionHeader(
                title = "Processeur (${state.cpu.onlineCount}/${state.cpu.coreCount} cœurs)",
                subtitle = state.cpu.gpuClockMhz?.let {
                    "GPU ${state.cpu.gpuModel} • $it MHz"
                } ?: "Fréquences instantanées sysfs cpufreq"
            )
        }

        item {
            if (state.cpu.cores.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aucun cœur cpufreq exposé dans /sys/devices/system/cpu.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 2
                ) {
                    state.cpu.cores.forEach { core ->
                        CoreFrequencyTile(
                            core = core,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Memory & zRAM Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
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
                                imageVector = Icons.Filled.Memory,
                                contentDescription = "Mémoire RAM",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Mémoire RAM & zRAM",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "${state.memory.usedMb} / ${state.memory.totalMb} Mo",
                            style = MonoMetricStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { state.memory.usedPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoMetricItem("Disponible", "${state.memory.availableMb} Mo", Modifier.weight(1f))
                        InfoMetricItem("Cache", "${state.memory.cachedMb} Mo", Modifier.weight(1f))
                        InfoMetricItem("Slab Reclaim", "${state.memory.slabReclaimableMb} Mo", Modifier.weight(1f))
                    }

                    if (state.memory.swapTotalMb > 0L || state.memory.zramDiskSizeMb > 0L) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val swapUsed = (state.memory.swapTotalMb - state.memory.swapFreeMb).coerceAtLeast(0L)
                            InfoMetricItem("Swap utilisé", "$swapUsed / ${state.memory.swapTotalMb} Mo", Modifier.weight(1f))
                            InfoMetricItem(
                                "zRAM (${state.memory.zramAlgorithm.ifEmpty { "lz4" }})",
                                "${state.memory.zramComprDataMb} / ${state.memory.zramDiskSizeMb} Mo",
                                Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Thermal & Battery Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
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
                                imageVector = Icons.Filled.BatteryChargingFull,
                                contentDescription = "Batterie et thermique",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Alimentation & Capteurs Thermiques",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "${state.battery.levelPercent}%",
                            style = MonoMetricStyle,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoMetricItem("Statut", state.battery.statusLabel, Modifier.weight(1f))
                        InfoMetricItem("Tension", "${state.battery.voltageMv} mV", Modifier.weight(1f))
                        InfoMetricItem(
                            "Courant",
                            state.battery.currentMa?.let { "$it mA" } ?: "--",
                            Modifier.weight(1f)
                        )
                    }

                    if (state.thermalZones.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Thermostat,
                                contentDescription = "Sondes thermiques",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Zones thermiques (/sys/class/thermal)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.thermalZones.take(10).forEach { zone ->
                                val isHot = zone.tempCelsius >= 60f
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isHot) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = zone.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isHot) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.1f", zone.tempCelsius)}°C",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHot) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun InfoMetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
