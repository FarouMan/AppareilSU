package com.example.ui.screens

import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.RootStatus
import com.example.data.ThemeMode
import com.example.ui.KernPulseUiState
import com.example.ui.components.MaterialYouPaletteStrip
import com.example.ui.components.QuickSelectableChip
import com.example.ui.components.SectionHeader
import com.example.ui.theme.MonoCodeStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterfaceManagerScreen(
    state: KernPulseUiState,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleAutoRefresh: (Boolean) -> Unit,
    onSelectPollingInterval: (Long) -> Unit,
    onApplyDensity: (Int) -> Unit,
    onResetDensity: () -> Unit,
    onApplyResolution: (Int, Int) -> Unit,
    onResetResolution: () -> Unit,
    onApplyAnimationScale: (Float) -> Unit,
    onApplySystemNightMode: (String) -> Unit,
    onApplyRefreshRate: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rootGranted = state.rootStatus == RootStatus.GRANTED
    val iface = state.interfaceState

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("interface_manager_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Gestion d'Interface & Material You",
                subtitle = "Apparence dynamique, métriques WindowManager, DPI et fluidité système"
            )
        }

        // Material You Palette & App Theme Card
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
                            imageVector = Icons.Filled.ColorLens,
                            contentDescription = "Material You",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Thème Material You (Monet)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    MaterialYouPaletteStrip()
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Couleurs dynamiques système",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    "Extrait la palette depuis le fond d'écran (Android 12+)"
                                } else {
                                    "Palette Teal/Slate adaptative (Android < 12)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.userPreferences.dynamicColorEnabled,
                            onCheckedChange = onToggleDynamicColor,
                            modifier = Modifier.testTag("dynamic_color_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Mode de thème de l'application",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickSelectableChip(
                            label = "Système",
                            selected = state.userPreferences.themeMode == ThemeMode.SYSTEM,
                            onClick = { onSelectThemeMode(ThemeMode.SYSTEM) }
                        )
                        QuickSelectableChip(
                            label = "Clair",
                            selected = state.userPreferences.themeMode == ThemeMode.LIGHT,
                            onClick = { onSelectThemeMode(ThemeMode.LIGHT) }
                        )
                        QuickSelectableChip(
                            label = "Sombre",
                            selected = state.userPreferences.themeMode == ThemeMode.DARK,
                            onClick = { onSelectThemeMode(ThemeMode.DARK) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rafraîchissement auto télémétrie", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Intervalle : ${state.userPreferences.pollingIntervalMs / 1000}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.userPreferences.autoRefreshEnabled,
                            onCheckedChange = onToggleAutoRefresh
                        )
                    }
                    if (state.userPreferences.autoRefreshEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1000L to "1s", 2000L to "2s", 5000L to "5s").forEach { (ms, label) ->
                                QuickSelectableChip(
                                    label = label,
                                    selected = state.userPreferences.pollingIntervalMs == ms,
                                    onClick = { onSelectPollingInterval(ms) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Display Density (wm density)
        item {
            var dpiSlider by remember(iface.currentDensity) {
                mutableFloatStateOf(iface.currentDensity.coerceIn(180, 640).toFloat())
            }
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
                            imageVector = Icons.Filled.DisplaySettings,
                            contentDescription = "Densité DPI",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Densité d'affichage (wm density)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Actuelle : ${iface.currentDensity} DPI (Native : ${iface.physicalDensity} DPI • sw${iface.smallestWidthDp}dp)",
                                style = MonoCodeStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Cible : ${dpiSlider.roundToInt()} DPI (retour auto en 10s si non confirmé)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = dpiSlider,
                        onValueChange = { dpiSlider = it },
                        valueRange = 200f..600f,
                        enabled = rootGranted
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(320, 380, 420, 480, 540).forEach { preset ->
                            QuickSelectableChip(
                                label = "${preset} dpi",
                                selected = iface.currentDensity == preset,
                                enabled = rootGranted,
                                onClick = {
                                    dpiSlider = preset.toFloat()
                                    onApplyDensity(preset)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onApplyDensity(dpiSlider.roundToInt()) },
                            enabled = rootGranted,
                            modifier = Modifier.testTag("apply_dpi_button")
                        ) {
                            Text("Tester ${dpiSlider.roundToInt()} DPI")
                        }
                        OutlinedButton(
                            onClick = onResetDensity,
                            enabled = rootGranted,
                            modifier = Modifier.testTag("reset_dpi_button")
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }
                }
            }
        }

        // Resolution & Refresh Rate Card
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
                            imageVector = Icons.Filled.AspectRatio,
                            contentDescription = "Résolution et fréquence",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Column {
                            Text(
                                text = "Résolution & Fréquence d'écran",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${iface.overrideResolution ?: iface.physicalResolution} • ${iface.currentRefreshRateHz.toInt()} Hz • ${iface.hdrCapabilities}",
                                style = MonoCodeStyle,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Résolution WindowManager (wm size)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickSelectableChip(
                            label = "Native (${iface.physicalResolution})",
                            selected = iface.overrideResolution == null,
                            enabled = rootGranted,
                            onClick = onResetResolution
                        )
                        QuickSelectableChip(
                            label = "720x1600",
                            selected = iface.overrideResolution == "720x1600",
                            enabled = rootGranted,
                            onClick = { onApplyResolution(720, 1600) }
                        )
                        QuickSelectableChip(
                            label = "1080x2400",
                            selected = iface.overrideResolution == "1080x2400",
                            enabled = rootGranted,
                            onClick = { onApplyResolution(1080, 2400) }
                        )
                    }

                    if (iface.supportedRefreshRatesHz.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Taux de rafraîchissement supportés",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            iface.supportedRefreshRatesHz.forEach { hz ->
                                QuickSelectableChip(
                                    label = "${hz.toInt()} Hz",
                                    selected = abs(iface.currentRefreshRateHz - hz) < 1.5f,
                                    enabled = rootGranted,
                                    onClick = { onApplyRefreshRate(hz) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Animations & System Night Mode Card
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
                            imageVector = Icons.Filled.Animation,
                            contentDescription = "Échelles d'animation",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Fluidité & Échelles d'animation",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Fenêtre ${iface.windowAnimationScale}x • Transition ${iface.transitionAnimationScale}x • Animateur ${iface.animatorDurationScale}x",
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
                        listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f, 1.5f).forEach { scale ->
                            QuickSelectableChip(
                                label = "${scale}x",
                                selected = abs(iface.animatorDurationScale - scale) < 0.05f,
                                enabled = rootGranted,
                                onClick = { onApplyAnimationScale(scale) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DarkMode,
                            contentDescription = "Mode Nuit Système",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Mode Nuit Android (cmd uimode night)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("yes" to "Forcer Sombre", "no" to "Forcer Clair", "auto" to "Auto").forEach { (cmd, label) ->
                            QuickSelectableChip(
                                label = label,
                                selected = iface.systemNightMode == cmd,
                                enabled = rootGranted,
                                onClick = { onApplySystemNightMode(cmd) }
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}
