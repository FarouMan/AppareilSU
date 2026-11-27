package com.example.ui

import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ThemeMode
import com.example.ui.components.DisplaySafetyRevertDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InterfaceManagerScreen
import com.example.ui.screens.KernelControlScreen
import com.example.ui.screens.LogsTerminalScreen
import com.example.ui.theme.KernPulseTheme

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_KERNEL = "kernel"
const val ROUTE_INTERFACE = "interface"
const val ROUTE_LOGS = "logs"

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val navItems = listOf(
    NavDestinationItem(
        route = ROUTE_DASHBOARD,
        label = "Diagnostic",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_dashboard"
    ),
    NavDestinationItem(
        route = ROUTE_KERNEL,
        label = "Noyau 4.19",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_kernel"
    ),
    NavDestinationItem(
        route = ROUTE_INTERFACE,
        label = "Interface",
        selectedIcon = Icons.Filled.DisplaySettings,
        unselectedIcon = Icons.Outlined.DisplaySettings,
        testTag = "nav_interface"
    ),
    NavDestinationItem(
        route = ROUTE_LOGS,
        label = "Journaux",
        selectedIcon = Icons.Filled.Terminal,
        unselectedIcon = Icons.Outlined.Terminal,
        testTag = "nav_logs"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernPulseApp(viewModel: KernPulseViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val isDarkTheme = when (state.userPreferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    KernPulseTheme(
        darkTheme = isDarkTheme,
        dynamicColor = state.userPreferences.dynamicColorEnabled
    ) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_DASHBOARD

        val navigateToTab: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        val pendingSeconds = state.pendingDisplayRevertSeconds
        if (pendingSeconds != null) {
            DisplaySafetyRevertDialog(
                secondsLeft = pendingSeconds,
                actionLabel = state.pendingRevertActionLabel,
                onConfirm = { viewModel.confirmDisplayChanges() },
                onRevertNow = { viewModel.revertDisplayChangesNow() }
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "KernPulse",
                                    fontWeight = FontWeight.Bold
                                )
                                val badgeText = state.overview.kernelRelease.ifEmpty { "4.19.325" }
                                Surface(
                                    modifier = Modifier.padding(start = 10.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.refreshAllDiagnostics(includeTunablesAndInterface = true) },
                                modifier = Modifier.testTag("refresh_all_button")
                            ) {
                                if (state.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Actualiser les diagnostics"
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val report = viewModel.buildDiagnosticReport()
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Rapport Diagnostic KernPulse")
                                        putExtra(Intent.EXTRA_TEXT, report)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Exporter le diagnostic"))
                                },
                                modifier = Modifier.testTag("export_report_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Exporter le diagnostic"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar {
                            navItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail {
                            navItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_DASHBOARD,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp)
                        ) {
                            composable(ROUTE_DASHBOARD) {
                                DashboardScreen(
                                    state = state,
                                    onRequestRoot = { viewModel.requestRootCheck() }
                                )
                            }
                            composable(ROUTE_KERNEL) {
                                KernelControlScreen(
                                    state = state,
                                    onSelectGovernor = { policy, gov -> viewModel.applyCpuGovernor(policy, gov) },
                                    onApplyClusterBounds = { policy, minK, maxK ->
                                        viewModel.applyClusterFreqBounds(policy, minK, maxK)
                                    },
                                    onSelectIoScheduler = { dev, sched -> viewModel.applyIoScheduler(dev, sched) },
                                    onSelectIoReadAhead = { dev, kb -> viewModel.applyIoReadAhead(dev, kb) },
                                    onSelectTcpCongestion = { algo -> viewModel.applyTcpCongestion(algo) },
                                    onApplySysctl = { key, value -> viewModel.applySysctlValue(key, value) },
                                    onApplySchedTune = { boost, preferIdle ->
                                        viewModel.applySchedTuneTopApp(boost, preferIdle)
                                    }
                                )
                            }
                            composable(ROUTE_INTERFACE) {
                                InterfaceManagerScreen(
                                    state = state,
                                    onToggleDynamicColor = { viewModel.setDynamicColorEnabled(it) },
                                    onSelectThemeMode = { viewModel.setThemeMode(it) },
                                    onToggleAutoRefresh = { viewModel.setAutoRefreshEnabled(it) },
                                    onSelectPollingInterval = { viewModel.setPollingIntervalMs(it) },
                                    onApplyDensity = { viewModel.applyDisplayDensityWithSafety(it) },
                                    onResetDensity = { viewModel.resetDisplayDensity() },
                                    onApplyResolution = { w, h -> viewModel.applyDisplayResolutionWithSafety(w, h) },
                                    onResetResolution = { viewModel.resetDisplayResolution() },
                                    onApplyAnimationScale = { viewModel.applyAnimationScales(it) },
                                    onApplySystemNightMode = { viewModel.applySystemNightMode(it) },
                                    onApplyRefreshRate = { viewModel.applyRefreshRate(it) }
                                )
                            }
                            composable(ROUTE_LOGS) {
                                LogsTerminalScreen(
                                    state = state,
                                    onSelectLogTab = { viewModel.selectLogTab(it) },
                                    onSearchChange = { viewModel.updateLogSearch(it) },
                                    onLevelFilterChange = { viewModel.updateLogLevelFilter(it) },
                                    onReloadLogs = { viewModel.loadActiveLogs() },
                                    onExecuteCommand = { viewModel.runConsoleCommand(it) },
                                    onClearConsole = { viewModel.clearConsoleHistory() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
