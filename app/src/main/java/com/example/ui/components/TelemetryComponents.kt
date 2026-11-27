package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CpuCoreInfo
import com.example.data.RootStatus
import com.example.data.TelemetrySample
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.MonoMetricStyle

@Composable
fun RootStatusBanner(
    rootStatus: RootStatus,
    hint: String,
    kernelRelease: String,
    is419Series: Boolean,
    isExact419325: Boolean,
    onRequestRoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGranted = rootStatus == RootStatus.GRANTED
    val containerColor by animateColorAsState(
        targetValue = when (rootStatus) {
            RootStatus.GRANTED -> MaterialTheme.colorScheme.primaryContainer
            RootStatus.CHECKING -> MaterialTheme.colorScheme.secondaryContainer
            RootStatus.DENIED_OR_UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
        },
        label = "rootBannerBg"
    )
    val contentColor = when (rootStatus) {
        RootStatus.GRANTED -> MaterialTheme.colorScheme.onPrimaryContainer
        RootStatus.CHECKING -> MaterialTheme.colorScheme.onSecondaryContainer
        RootStatus.DENIED_OR_UNAVAILABLE -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("root_status_banner"),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                when (rootStatus) {
                    RootStatus.CHECKING -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = contentColor
                    )
                    RootStatus.GRANTED -> Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = "Root actif",
                        tint = contentColor
                    )
                    RootStatus.DENIED_OR_UNAVAILABLE -> Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Mode lecture seule",
                        tint = contentColor
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when (rootStatus) {
                            RootStatus.GRANTED -> "ROOT UID 0 ACTIF"
                            RootStatus.CHECKING -> "VÉRIFICATION SU..."
                            RootStatus.DENIED_OR_UNAVAILABLE -> "LECTURE SEULE (SANS ROOT)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                    if (kernelRelease.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = contentColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = when {
                                    isExact419325 -> "4.19.325 LTS"
                                    is419Series -> "$kernelRelease (4.19)"
                                    else -> kernelRelease
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = hint.ifEmpty { "Inspection des nœuds /proc et /sys en cours" },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isGranted && rootStatus != RootStatus.CHECKING) {
                OutlinedButton(
                    onClick = onRequestRoot,
                    modifier = Modifier.testTag("retry_root_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Tester su",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("su")
                }
            } else if (isGranted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Root vérifié",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CpuPulseChart(
    samples: List<TelemetrySample>,
    currentCpuPercent: Float,
    currentTempCelsius: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Card(
        modifier = modifier.fillMaxWidth(),
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
                Column {
                    Text(
                        text = "Oscilloscope Charge CPU & Thermique",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Échantillonnage /proc/stat en temps réel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricPill(
                        label = "CPU",
                        value = "${currentCpuPercent.toInt()}%",
                        color = primaryColor
                    )
                    MetricPill(
                        label = "TEMP",
                        value = "${String.format(java.util.Locale.US, "%.1f", currentTempCelsius)}°C",
                        color = tertiaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                val w = size.width
                val h = size.height
                val padY = 12.dp.toPx()
                val usableH = (h - padY * 2f).coerceAtLeast(1f)

                // Horizontal subtle grid lines (25%, 50%, 75%)
                listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                    val y = padY + usableH * frac
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (samples.size >= 2) {
                    val stepX = w / (samples.size - 1).coerceAtLeast(1)

                    // CPU Path
                    val cpuPath = Path()
                    val cpuFill = Path()
                    samples.forEachIndexed { idx, s ->
                        val x = idx * stepX
                        val norm = (s.cpuPercent / 100f).coerceIn(0f, 1f)
                        val y = padY + usableH * (1f - norm)
                        if (idx == 0) {
                            cpuPath.moveTo(x, y)
                            cpuFill.moveTo(x, h)
                            cpuFill.lineTo(x, y)
                        } else {
                            cpuPath.lineTo(x, y)
                            cpuFill.lineTo(x, y)
                        }
                        if (idx == samples.lastIndex) {
                            cpuFill.lineTo(x, h)
                            cpuFill.close()
                        }
                    }

                    drawPath(
                        path = cpuFill,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        )
                    )
                    drawPath(
                        path = cpuPath,
                        color = primaryColor,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Thermal Path (normalized 20°C..95°C)
                    val tempPath = Path()
                    samples.forEachIndexed { idx, s ->
                        val x = idx * stepX
                        val norm = ((s.maxTempCelsius - 20f) / 75f).coerceIn(0f, 1f)
                        val y = padY + usableH * (1f - norm)
                        if (idx == 0) tempPath.moveTo(x, y) else tempPath.lineTo(x, y)
                    }
                    drawPath(
                        path = tempPath,
                        color = tertiaryColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoMetricStyle,
            color = color
        )
    }
}

@Composable
fun CoreFrequencyTile(
    core: CpuCoreInfo,
    modifier: Modifier = Modifier
) {
    val freqFraction = if (core.maxFreqKhz > 0L) {
        (core.currentFreqKhz.toFloat() / core.maxFreqKhz.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedFreq by animateFloatAsState(
        targetValue = if (core.isOnline) freqFraction else 0f,
        animationSpec = spring(),
        label = "coreFreq"
    )

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (core.isOnline) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CPU${core.coreId}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (core.isOnline) "${core.usagePercent.toInt()}%" else "OFFLINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (core.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (core.currentFreqKhz > 0L) "${core.currentFreqKhz / 1000} MHz" else "-- MHz",
                style = MonoCodeStyle,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { animatedFreq },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = core.governor,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MaterialYouPaletteStrip(
    modifier: Modifier = Modifier
) {
    val swatches = listOf(
        "Primary" to MaterialTheme.colorScheme.primary,
        "PrimCont" to MaterialTheme.colorScheme.primaryContainer,
        "Secondary" to MaterialTheme.colorScheme.secondary,
        "Tertiary" to MaterialTheme.colorScheme.tertiary,
        "SurfaceHi" to MaterialTheme.colorScheme.surfaceContainerHigh,
        "Error" to MaterialTheme.colorScheme.error
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        swatches.forEach { (name, color) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DisplaySafetyRevertDialog(
    secondsLeft: Int,
    actionLabel: String,
    onConfirm: () -> Unit,
    onRevertNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Require explicit choice or countdown */ },
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Sécurité affichage",
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = {
            Text("Confirmer l'affichage ? (${secondsLeft}s)")
        },
        text = {
            Text(
                "$actionLabel. L'affichage sera automatiquement restauré dans $secondsLeft seconde(s) pour éviter toute perte de lisibilité."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_display_change_button")
            ) {
                Text("Conserver")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onRevertNow,
                modifier = Modifier.testTag("revert_display_change_button")
            ) {
                Text("Restaurer")
            }
        }
    )
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun QuickSelectableChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "chipBg"
    )
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) fg else fg.copy(alpha = 0.45f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
