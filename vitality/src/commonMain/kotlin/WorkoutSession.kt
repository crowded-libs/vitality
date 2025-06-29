package vitality

import vitality.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant

/**
 * Represents an active workout session
 * Direct mapping to native workout session APIs:
 * - iOS: HKWorkoutSession 
 * - Android: ExerciseSessionRecord
 */
interface WorkoutSession {
    val id: String
    val type: WorkoutType
    val state: WorkoutState
    val startTime: Instant
    val isWearableSession: Boolean
    
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun end(): Result<Unit>
    suspend fun addMetadata(key: String, value: Any): Result<Unit>
    
    // Real-time metrics - raw data streams, no aggregation
    fun observeHeartRate(): Flow<HeartRateData>
    fun observeCalories(): Flow<CalorieData>
    fun observeDistance(): Flow<DistanceData>
    
    // Optional platform-specific metrics
    fun observeSpeed(): Flow<SpeedData> = flow { }
    fun observePower(): Flow<PowerData> = flow { }
    fun observeCadence(): Flow<CyclingCadenceData> = flow { }
    fun observeSteps(): Flow<StepsData> = flow { }
}

/**
 * Workout session states
 * Maps to native workout states
 */
enum class WorkoutState {
    PREPARING,
    RUNNING,
    PAUSED,
    STOPPED,
    ENDED
}