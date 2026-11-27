package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.LogEntry
import com.example.data.RootStatus
import com.example.ui.KernPulseUiState
import com.example.ui.LogSourceTab
import com.example.ui.ShellTranscriptEntry
import com.example.ui.components.QuickSelectableChip
import com.example.ui.theme.MonoCodeStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogsTerminalScreen(
    state: KernPulseUiState,
    onSelectLogTab: (LogSourceTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onLevelFilterChange: (String) -> Unit,
    onReloadLogs: () -> Unit,
    onExecuteCommand: (String) -> Unit,
    onClearConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("logs_terminal_screen")
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val tabs = listOf(
                LogSourceTab.DMESG to "dmesg",
                LogSourceTab.LOGCAT to "logcat",
                LogSourceTab.CONSOLE to "Console su"
            )
            tabs.forEachIndexed { idx, (tab, label) ->
                SegmentedButton(
                    selected = state.activeLogTab == tab,
                    onClick = { onSelectLogTab(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                    modifier = Modifier.testTag("log_tab_${tab.name.lowercase()}")
                ) {
                    Text(label)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.activeLogTab == LogSourceTab.CONSOLE) {
            RootConsoleContent(
                state = state,
                onExecuteCommand = onExecuteCommand,
                onClearConsole = onClearConsole
            )
        } else {
            val rawEntries = if (state.activeLogTab == LogSourceTab.DMESG) state.dmesgEntries else state.logcatEntries
            val errorMsg = if (state.activeLogTab == LogSourceTab.DMESG) state.dmesgError else state.logcatError

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.logSearchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Filtrer par mot-clé ou pilote...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("log_search_input"),
                    shape = MaterialTheme.shapes.medium
                )
                FilledTonalIconButton(
                    onClick = onReloadLogs,
                    modifier = Modifier.testTag("reload_logs_button")
                ) {
                    if (state.isLoadingLogs) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recharger les journaux")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "Tout", "ERR" to "Erreurs", "WARN" to "Alertes", "INFO" to "Info").forEach { (lvl, label) ->
                    QuickSelectableChip(
                        label = label,
                        selected = state.logLevelFilter == lvl,
                        onClick = { onLevelFilterChange(lvl) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val filtered = remember(rawEntries, state.logSearchQuery, state.logLevelFilter) {
                rawEntries.filter { entry ->
                    val matchesLvl = state.logLevelFilter == "ALL" || entry.level == state.logLevelFilter
                    val q = state.logSearchQuery.trim()
                    val matchesQuery = q.isEmpty() ||
                        entry.message.contains(q, ignoreCase = true) ||
                        entry.tag.contains(q, ignoreCase = true)
                    matchesLvl && matchesQuery
                }
            }

            if (errorMsg != null && filtered.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else if (filtered.isEmpty() && !state.isLoadingLogs) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aucune ligne correspondante dans les journaux.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(filtered) { entry ->
                        LogEntryCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    val badgeColor = when (entry.level) {
        "ERR" -> MaterialTheme.colorScheme.errorContainer
        "WARN" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val badgeText = when (entry.level) {
        "ERR" -> MaterialTheme.colorScheme.onErrorContainer
        "WARN" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(6.dp), color = badgeColor) {
                        Text(
                            text = entry.level,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.message,
                style = MonoCodeStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RootConsoleContent(
    state: KernPulseUiState,
    onExecuteCommand: (String) -> Unit,
    onClearConsole: () -> Unit
) {
    var commandText by remember { mutableStateOf("") }
    val quickCommands = listOf(
        "uname -a",
        "cat /proc/version",
        "lsmod",
        "getenforce",
        "cat /proc/interrupts | head -n 20",
        "df -h",
        "sysctl vm.swappiness"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                placeholder = {
                    Text(
                        if (state.rootStatus == RootStatus.GRANTED) "Commande su (ex: uname -a)" else "Commande shell (sh)"
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                singleLine = true,
                textStyle = MonoCodeStyle,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandText.isNotBlank()) {
                            onExecuteCommand(commandText)
                            commandText = ""
                        }
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("console_command_input"),
                shape = MaterialTheme.shapes.medium
            )

            Button(
                onClick = {
                    if (commandText.isNotBlank()) {
                        onExecuteCommand(commandText)
                        commandText = ""
                    }
                },
                enabled = commandText.isNotBlank() && !state.isExecutingCommand,
                modifier = Modifier.testTag("console_run_button")
            ) {
                if (state.isExecutingCommand) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Exécuter")
                }
            }

            if (state.shellHistory.isNotEmpty()) {
                IconButton(onClick = onClearConsole) {
                    Icon(Icons.Filled.ClearAll, contentDescription = "Effacer l'historique")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickCommands.forEach { cmd ->
                QuickSelectableChip(
                    label = cmd,
                    selected = false,
                    onClick = { onExecuteCommand(cmd) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (state.shellHistory.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Sélectionnez une commande rapide ci-dessus ou saisissez une commande shell pour inspecter le noyau 4.19.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(state.shellHistory, key = { "${it.timestampMs}_${it.command}" }) { entry ->
                    ShellTranscriptCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun ShellTranscriptCard(entry: ShellTranscriptEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${if (entry.result.usedRoot) "#" else "$"} ${entry.command}",
                    style = MonoCodeStyle,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (entry.result.isSuccess) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = "exit ${entry.result.exitCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.result.isSuccess) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (entry.result.stdout.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.result.stdout,
                    style = MonoCodeStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (entry.result.stderr.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.result.stderr,
                    style = MonoCodeStyle,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
