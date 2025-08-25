package io.github.crowdedlibs.vitality_sample.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.crowdedlibs.vitality_sample.state.HealthDemoState
import io.github.crowdedlibs.vitality_sample.state.WorkoutState
import io.github.crowdedlibs.vitality_sample.viewmodel.HealthViewModel
import vitality.WorkoutType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun WorkoutScreen(
    state: HealthDemoState,
    viewModel: HealthViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Workout Session",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (state.activeWorkoutSession == null) {
            // Workout selection
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Select Workout Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Workout type dropdown
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = state.selectedWorkoutType.name,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            label = { Text("Workout Type") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            }
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            WorkoutType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        viewModel.selectWorkoutType(type)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { viewModel.startWorkout() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Workout")
                    }
                }
            }
        } else {
            // Active workout display
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
                            text = state.activeWorkoutSession.type.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Badge(
                            containerColor = when (state.activeWorkoutSession.state) {
                                WorkoutState.RUNNING -> MaterialTheme.colorScheme.primary
                                WorkoutState.PAUSED -> MaterialTheme.colorScheme.secondary
                                WorkoutState.ENDED -> MaterialTheme.colorScheme.tertiary
                            }
                        ) {
                            Text(state.activeWorkoutSession.state.name)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Workout metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        WorkoutMetricDisplay(
                            label = "Duration",
                            value = formatDuration(
                                Clock.System.now().toEpochMilliseconds() - state.activeWorkoutSession.startTime
                            )
                        )
                        
                        WorkoutMetricDisplay(
                            label = "Distance",
                            value = "${(state.activeWorkoutSession.distance / 1000).toInt()}.${((state.activeWorkoutSession.distance / 1000 * 100) % 100).toInt()} km"
                        )
                        
                        WorkoutMetricDisplay(
                            label = "Calories",
                            value = "${state.activeWorkoutSession.calories}"
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (state.activeWorkoutSession.heartRate > 0) {
                            WorkoutMetricDisplay(
                                label = "Heart Rate",
                                value = "${state.activeWorkoutSession.heartRate} bpm"
                            )
                        }
                        
                        if (state.activeWorkoutSession.steps > 0) {
                            WorkoutMetricDisplay(
                                label = "Steps",
                                value = "${state.activeWorkoutSession.steps}"
                            )
                        }
                        
                        // Calculate pace if distance and duration are available
                        if (state.activeWorkoutSession.distance > 0 && state.activeWorkoutSession.duration > 0) {
                            val paceMinPerKm = (state.activeWorkoutSession.duration / 60000.0) / (state.activeWorkoutSession.distance / 1000.0)
                            WorkoutMetricDisplay(
                                label = "Pace",
                                value = "${paceMinPerKm.toInt()}:${((paceMinPerKm * 60) % 60).toInt().toString().padStart(2, '0')} min/km"
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Control buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (state.activeWorkoutSession.state) {
                            WorkoutState.RUNNING -> {
                                OutlinedButton(
                                    onClick = { viewModel.pauseWorkout() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Pause",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pause")
                                }
                                
                                Button(
                                    onClick = { viewModel.endWorkout() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "End",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("End")
                                }
                            }
                            
                            WorkoutState.PAUSED -> {
                                Button(
                                    onClick = { viewModel.resumeWorkout() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Resume",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resume")
                                }
                                
                                Button(
                                    onClick = { viewModel.endWorkout() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "End",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("End")
                                }
                            }
                            
                            WorkoutState.ENDED -> {
                                Text(
                                    text = "Workout ended",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Tips
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tips",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• Start the mock data generator in the Write tab to see live metrics",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "• Workouts are saved automatically when ended",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "• View completed workouts in the Read tab",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun WorkoutMetricDisplay(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = millis / (1000 * 60 * 60)
    
    return if (hours > 0) {
        "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}