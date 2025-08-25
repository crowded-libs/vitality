package io.github.crowdedlibs.vitality_sample.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.crowdedlibs.vitality_sample.state.HealthDemoState
import io.github.crowdedlibs.vitality_sample.viewmodel.HealthViewModel

@Composable
fun ReadDataScreen(
    state: HealthDemoState,
    viewModel: HealthViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with refresh button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Health Data",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(
                onClick = { viewModel.loadHealthData() },
                enabled = !state.isLoadingData
            ) {
                if (state.isLoadingData) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Data cards
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Heart Rate Card
            item {
                HealthDataCard(
                    title = "Heart Rate",
                    value = state.latestHeartRate?.let { "${it.bpm} bpm" } ?: "No data",
                    subtitle = state.latestHeartRate?.let {
                        "Variability: ${it.variability?.let { v -> "${v}ms" } ?: "N/A"}"
                    }
                )
            }
            
            // Steps Card
            item {
                HealthDataCard(
                    title = "Steps Today",
                    value = state.todaySteps?.let { "${it.count} steps" } ?: "No data",
                    subtitle = state.todaySteps?.cadence?.let { "Cadence: $it steps/min" }
                )
            }
            
            // Calories Card
            item {
                HealthDataCard(
                    title = "Calories Today",
                    value = state.todayCalories?.let { 
                        val total = it.activeCalories + (it.basalCalories ?: 0.0)
                        "${total.toInt()} kcal"
                    } ?: "No data",
                    subtitle = state.todayCalories?.let {
                        "Active: ${it.activeCalories.toInt()} kcal"
                    }
                )
            }
            
            // Weight Card
            item {
                HealthDataCard(
                    title = "Weight",
                    value = state.latestWeight?.let { 
                        "${it.weight} ${it.weightUnit ?: "kg"}"
                    } ?: "No data",
                    subtitle = state.latestWeight?.let {
                        it.bmi?.let { bmi -> 
                            val bmiInt = bmi.toInt()
                            val bmiDecimal = ((bmi * 10) % 10).toInt()
                            "BMI: $bmiInt.$bmiDecimal"
                        }
                    }
                )
            }
            
            // Recent Workouts
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Recent Workouts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (state.recentWorkouts.isEmpty()) {
                            Text(
                                text = "No recent workouts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            state.recentWorkouts.take(3).forEach { workout ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = workout.type.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "${workout.duration?.inWholeMinutes ?: 0} min",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        workout.totalCalories?.let {
                                            Text(
                                                text = "${it.toInt()} kcal",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthDataCard(
    title: String,
    value: String,
    subtitle: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}