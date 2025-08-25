package io.github.crowdedlibs.vitality_sample.repository

import kotlinx.coroutines.flow.Flow
import vitality.*
import vitality.models.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class HealthRepository(
    private val healthConnector: HealthConnector
) {
    private var isInitialized = false
    
    suspend fun initialize(): Result<Unit> {
        return try {
            val result = healthConnector.initialize()
            if (result.isSuccess) {
                isInitialized = true
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun requestPermissions(permissions: Set<HealthPermission>): Result<PermissionResult> {
        return healthConnector.requestPermissions(permissions)
    }
    
    suspend fun checkPermissions(permissions: Set<HealthPermission>): PermissionStatus {
        return healthConnector.checkPermissions(permissions)
    }
    
    suspend fun getAvailableDataTypes(): Set<HealthDataType> {
        return healthConnector.getAvailableDataTypes()
    }
    
    // Read operations
    suspend fun readLatestHeartRate(): Result<HeartRateData?> {
        return healthConnector.readLatestHeartRate()
    }
    
    suspend fun readStepsToday(): Result<StepsData> {
        return healthConnector.readStepsToday()
    }
    
    suspend fun readCaloriesToday(): Result<CalorieData> {
        return healthConnector.readCaloriesToday()
    }
    
    suspend fun readLatestWeight(): Result<BodyMeasurements?> {
        return healthConnector.readLatestWeight()
    }
    
    suspend fun readRecentWorkouts(): Result<List<WorkoutData>> {
        val now = Clock.System.now()
        val weekAgo = now - 7.days
        return healthConnector.readWorkouts(weekAgo, now)
    }
    
    // Write operations
    suspend fun writeWeight(weight: Double, unit: WeightUnit): Result<Unit> {
        return healthConnector.writeWeight(weight, unit)
    }
    
    suspend fun writeHeartRate(bpm: Int): Result<Unit> {
        val dataPoint = HeartRateData(
            timestamp = Clock.System.now(),
            bpm = bpm,
            source = null,
            metadata = emptyMap()
        )
        return healthConnector.writeHealthData(dataPoint)
    }
    
    suspend fun writeSteps(count: Int): Result<Unit> {
        val now = Clock.System.now()
        // For manual entry, assume steps were accumulated over the last minute
        // This ensures the data will be visible to real-time monitors that look back 5 minutes
        val dataPoint = StepsData(
            timestamp = now,
            count = count,
            startTime = now - 1.minutes,
            endTime = now,
            source = null,
            metadata = emptyMap()
        )
        return healthConnector.writeHealthData(dataPoint)
    }
    
    suspend fun writeCalories(activeCalories: Double, basalCalories: Double? = null): Result<Unit> {
        val now = Clock.System.now()
        // For manual entry, assume calories were burned over the last minute
        val dataPoint = CalorieData(
            timestamp = now,
            activeCalories = activeCalories,
            basalCalories = basalCalories,
            startTime = now - 1.minutes,
            endTime = now,
            source = null,
            metadata = emptyMap()
        )
        return healthConnector.writeHealthData(dataPoint)
    }
    
    // Real-time monitoring
    fun <T> observeHealthData(
        dataType: HealthDataType,
        samplingInterval: Duration = 5.seconds
    ): Flow<T>? {
        return healthConnector.observe(dataType, samplingInterval)
    }
    
    // Workout management
    suspend fun startWorkoutSession(workoutType: WorkoutType): Result<WorkoutSession> {
        return healthConnector.startWorkoutSession(workoutType)
    }
    
    suspend fun pauseWorkoutSession(sessionId: String): Result<Unit> {
        return healthConnector.pauseWorkoutSession(sessionId)
    }
    
    suspend fun resumeWorkoutSession(sessionId: String): Result<Unit> {
        return healthConnector.resumeWorkoutSession(sessionId)
    }
    
    suspend fun endWorkoutSession(sessionId: String): Result<Unit> {
        return healthConnector.endWorkoutSession(sessionId)
    }
    
    fun observeActiveWorkout(): Flow<WorkoutData>? {
        return healthConnector.observeActiveWorkout()
    }
}