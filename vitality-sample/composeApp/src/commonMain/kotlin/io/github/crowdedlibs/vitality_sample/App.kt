package io.github.crowdedlibs.vitality_sample

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.crowdedlibs.vitality_sample.screens.*
import io.github.crowdedlibs.vitality_sample.state.HealthTab
import io.github.crowdedlibs.vitality_sample.viewmodel.HealthViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import vitality.HealthConnector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App(
    healthConnector: HealthConnector = getHealthConnector()
) {
    MaterialTheme {
        val viewModel = viewModel { HealthViewModel(healthConnector) }
        val state by viewModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Vitality Demo") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    HealthTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            icon = { 
                                Icon(
                                    getTabIcon(tab),
                                    contentDescription = tab.name
                                )
                            },
                            label = { 
                                Text(
                                    getTabLabel(tab),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (state.selectedTab) {
                    HealthTab.Permissions -> PermissionsScreen(
                        state = state,
                        viewModel = viewModel
                    )
                    HealthTab.ReadData -> ReadDataScreen(
                        state = state,
                        viewModel = viewModel
                    )
                    HealthTab.WriteData -> WriteDataScreen(
                        state = state,
                        viewModel = viewModel
                    )
                    HealthTab.Monitor -> MonitoringScreen(
                        state = state,
                        viewModel = viewModel
                    )
                    HealthTab.Workout -> WorkoutScreen(
                        state = state,
                        viewModel = viewModel
                    )
                }
            }
        }
        
        // Show error/success messages
        LaunchedEffect(state.errorMessage) {
            state.errorMessage?.let { message ->
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearError()
            }
        }
        
        LaunchedEffect(state.successMessage) {
            state.successMessage?.let { message ->
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearSuccess()
            }
        }
    }
}

private fun getTabIcon(tab: HealthTab): ImageVector {
    return when (tab) {
        HealthTab.Permissions -> Icons.Default.Lock
        HealthTab.ReadData -> Icons.Default.Favorite
        HealthTab.WriteData -> Icons.Default.Edit
        HealthTab.Monitor -> Icons.Default.PlayArrow
        HealthTab.Workout -> Icons.Default.Star
    }
}

private fun getTabLabel(tab: HealthTab): String {
    return when (tab) {
        HealthTab.Permissions -> "Permissions"
        HealthTab.ReadData -> "Read"
        HealthTab.WriteData -> "Write"
        HealthTab.Monitor -> "Monitor"
        HealthTab.Workout -> "Workout"
    }
}