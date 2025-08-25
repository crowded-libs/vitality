package io.github.crowdedlibs.vitality_sample.state

import vitality.HealthDataType
import vitality.HealthPermission
import vitality.PermissionStatus
import vitality.WorkoutType
import vitality.models.*

data class HealthDemoState(
    // Navigation
    val selectedTab: HealthTab = HealthTab.Permissions,
    
    // Permissions
    val availablePermissions: List<HealthPermission> = emptyList(),
    val selectedPermissions: Set<HealthPermission> = emptySet(),
    val permissionStatus: PermissionStatus = PermissionStatus.NotDetermined,
    val isRequestingPermissions: Boolean = false,
    
    // Read Data
    val latestHeartRate: HeartRateData? = null,
    val todaySteps: StepsData? = null,
    val todayCalories: CalorieData? = null,
    val latestWeight: BodyMeasurements? = null,
    val recentWorkouts: List<WorkoutData> = emptyList(),
    val isLoadingData: Boolean = false,
    
    // Write Data
    val weightInput: String = "",
    val heartRateInput: String = "",
    val stepsInput: String = "",
    val isWritingData: Boolean = false,
    
    // Real-time Monitoring
    val monitoringDataTypes: Set<HealthDataType> = emptySet(),
    val isMonitoring: Boolean = false,
    val liveHeartRateData: List<HeartRateData> = emptyList(),
    val liveStepsData: List<StepsData> = emptyList(),
    val liveCaloriesData: List<CalorieData> = emptyList(),
    
    // Mock Data Generator
    val isGeneratingMockData: Boolean = false,
    val mockDataInterval: Long = 5000L, // milliseconds
    
    // Workout
    val selectedWorkoutType: WorkoutType = WorkoutType.RUNNING,
    val activeWorkoutSession: WorkoutSession? = null,
    val workoutMetrics: WorkoutMetrics = WorkoutMetrics(),
    
    // General
    val errorMessage: String? = null,
    val successMessage: String? = null
)

enum class HealthTab {
    Permissions,
    ReadData,
    WriteData,
    Monitor,
    Workout
}

data class WorkoutSession(
    val sessionId: String,
    val type: WorkoutType,
    val startTime: Long,
    val state: WorkoutState = WorkoutState.RUNNING,
    val duration: Long = 0L,
    val heartRate: Int = 0,
    val calories: Int = 0,
    val distance: Float = 0f,
    val steps: Int = 0
)

enum class WorkoutState {
    RUNNING,
    PAUSED,
    ENDED
}

data class WorkoutMetrics(
    val duration: Long = 0L,
    val distance: Double = 0.0,
    val calories: Double = 0.0,
    val heartRate: Int? = null,
    val pace: Double? = null,
    val cadence: Int? = null
)