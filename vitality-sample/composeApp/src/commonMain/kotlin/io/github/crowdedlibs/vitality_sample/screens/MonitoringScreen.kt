package io.github.crowdedlibs.vitality_sample.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.crowdedlibs.vitality_sample.state.HealthDemoState
import io.github.crowdedlibs.vitality_sample.viewmodel.HealthViewModel
import vitality.HealthDataType

@Composable
fun MonitoringScreen(
    state: HealthDemoState,
    viewModel: HealthViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Real-time Monitoring",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (state.isMonitoring) {
                IconButton(
                    onClick = { viewModel.stopAllMonitoring() }
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Stop All",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        Text(
            text = "Select data types to monitor in real-time",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Monitoring toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MonitoringChip(
                label = "Heart Rate",
                isActive = HealthDataType.HeartRate in state.monitoringDataTypes,
                onClick = { viewModel.toggleMonitoring(HealthDataType.HeartRate) }
            )
            
            MonitoringChip(
                label = "Steps",
                isActive = HealthDataType.Steps in state.monitoringDataTypes,
                onClick = { viewModel.toggleMonitoring(HealthDataType.Steps) }
            )
            
            MonitoringChip(
                label = "Calories",
                isActive = HealthDataType.Calories in state.monitoringDataTypes,
                onClick = { viewModel.toggleMonitoring(HealthDataType.Calories) }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Live data display
        if (state.isMonitoring) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Heart Rate Data
                if (HealthDataType.HeartRate in state.monitoringDataTypes) {
                    item {
                        LiveDataCard(
                            title = "Heart Rate",
                            dataCount = state.liveHeartRateData.size
                        ) {
                            if (state.liveHeartRateData.isEmpty()) {
                                Text(
                                    text = "Waiting for data...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                state.liveHeartRateData.takeLast(5).forEach { data ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${data.bpm} bpm",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Just now",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Steps Data
                if (HealthDataType.Steps in state.monitoringDataTypes) {
                    item {
                        LiveDataCard(
                            title = "Steps",
                            dataCount = state.liveStepsData.size
                        ) {
                            if (state.liveStepsData.isEmpty()) {
                                Text(
                                    text = "Waiting for data...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val totalSteps = state.liveStepsData.sumOf { it.count }
                                Text(
                                    text = "$totalSteps total steps",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                state.liveStepsData.takeLast(3).forEach { data ->
                                    Text(
                                        text = "+${data.count} steps",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Calories Data
                if (HealthDataType.Calories in state.monitoringDataTypes) {
                    item {
                        LiveDataCard(
                            title = "Calories",
                            dataCount = state.liveCaloriesData.size
                        ) {
                            if (state.liveCaloriesData.isEmpty()) {
                                Text(
                                    text = "Waiting for data...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val totalCalories = state.liveCaloriesData
                                    .map { it.activeCalories + (it.basalCalories ?: 0.0) }
                                    .sum()
                                Text(
                                    text = "${totalCalories.toInt()} kcal",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                state.liveCaloriesData.takeLast(3).forEach { data ->
                                    val calories = data.activeCalories + (data.basalCalories ?: 0.0)
                                    Text(
                                        text = "+${calories.toInt()} kcal",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Not monitoring",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Select data types above to start",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MonitoringChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun LiveDataCard(
    title: String,
    dataCount: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Badge {
                    Text("$dataCount updates")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            content()
        }
    }
}