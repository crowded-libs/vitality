package vitality

import android.annotation.SuppressLint
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import vitality.extensions.toExerciseType
import vitality.helpers.observeWorkoutRecords
import vitality.models.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Android Health Connect implementation of WorkoutSession
 * Direct mapping to ExerciseSessionRecord without extra features
 */
class HealthConnectWorkoutSession(
    override val id: String,
    override val type: WorkoutType,
    override val startTime: Instant,
    private val healthConnectClient: HealthConnectClient,
    private val configuration: WorkoutConfiguration? = null
) : WorkoutSession {
    
    override var state: WorkoutState = WorkoutState.PREPARING
        private set
    
    override val isWearableSession: Boolean = false
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessionMetadata = mutableMapOf<String, Any>()
    
    fun start() {
        state = WorkoutState.RUNNING
    }
    
    override suspend fun pause(): Result<Unit> {
        return try {
            if (state == WorkoutState.RUNNING) {
                state = WorkoutState.PAUSED
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun resume(): Result<Unit> {
        return try {
            if (state == WorkoutState.PAUSED) {
                state = WorkoutState.RUNNING
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    @SuppressLint("RestrictedApi")
    override suspend fun end(): Result<Unit> {
        return try {
            state = WorkoutState.ENDED
            
            val endTime = Clock.System.now()
            
            val sessionRecord = ExerciseSessionRecord(
                startTime = startTime.toJavaInstant(),
                startZoneOffset = null,
                endTime = endTime.toJavaInstant(),
                endZoneOffset = null,
                exerciseType = type.toExerciseType(),
                title = null,
                notes = null,
                metadata = createMetadata()
            )
            
            healthConnectClient.insertRecords(listOf(sessionRecord))
            
            scope.cancel()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun addMetadata(key: String, value: Any): Result<Unit> {
        sessionMetadata[key] = value
        return Result.success(Unit)
    }
    
    override fun observeHeartRate(): Flow<HeartRateData> = 
        healthConnectClient.observeWorkoutRecords(
            startTime = startTime,
            workoutState = { state },
            pollingInterval = (configuration?.metadata?.get("pollingInterval") as? Long) ?: 30.seconds.inWholeMilliseconds
        ) { record: HeartRateRecord ->
            record.samples.lastOrNull()?.let { sample ->
                HeartRateData(
                    timestamp = sample.time.toInstant(),
                    bpm = sample.beatsPerMinute.toInt()
                )
            }
        }
    
    override fun observeCalories(): Flow<CalorieData> = 
        healthConnectClient.observeWorkoutRecords(
            startTime = startTime,
            workoutState = { state },
            pollingInterval = (configuration?.metadata?.get("pollingInterval") as? Long) ?: 30.seconds.inWholeMilliseconds
        ) { record: ActiveCaloriesBurnedRecord ->
            CalorieData(
                timestamp = record.startTime.toInstant(),
                activeCalories = record.energy.inKilocalories
            )
        }
    
    override fun observeDistance(): Flow<DistanceData> = 
        healthConnectClient.observeWorkoutRecords(
            startTime = startTime,
            workoutState = { state },
            pollingInterval = (configuration?.metadata?.get("pollingInterval") as? Long) ?: 30.seconds.inWholeMilliseconds
        ) { record: DistanceRecord ->
            DistanceData(
                timestamp = record.startTime.toInstant(),
                distance = record.distance.inMeters,
                unit = DistanceUnit.METERS,
                activityType = DistanceActivityType.UNKNOWN
            )
        }
    
    override fun observeSpeed(): Flow<SpeedData> = 
        healthConnectClient.observeWorkoutRecords(
            startTime = startTime,
            workoutState = { state },
            pollingInterval = (configuration?.metadata?.get("pollingInterval") as? Long) ?: 30.seconds.inWholeMilliseconds
        ) { record: SpeedRecord ->
            record.samples.lastOrNull()?.let { sample ->
                SpeedData(
                    timestamp = sample.time.toInstant(),
                    speedMetersPerSecond = sample.speed.inMetersPerSecond
                )
            }
        }
    
    override fun observePower(): Flow<PowerData> = 
        healthConnectClient.observeWorkoutRecords(
            startTime = startTime,
            workoutState = { state },
            pollingInterval = (configuration?.metadata?.get("pollingInterval") as? Long) ?: 30.seconds.inWholeMilliseconds
        ) { record: PowerRecord ->
            record.samples.lastOrNull()?.let { sample ->
                PowerData(
                    timestamp = sample.time.toInstant(),
                    watts = sample.power.inWatts
                )
            }
        }
    
    override fun observeCadence(): Flow<CyclingCadenceData> = 
        healthConnectClient.observeWorkoutRecords(
            startTime = startTime,
            workoutState = { state },
            pollingInterval = (configuration?.metadata?.get("pollingInterval") as? Long) ?: 30.seconds.inWholeMilliseconds
        ) { record: CyclingPedalingCadenceRecord ->
            record.samples.lastOrNull()?.let { sample ->
                CyclingCadenceData(
                    timestamp = sample.time.toInstant(),
                    rpm = sample.revolutionsPerMinute.toInt()
                )
            }
        }
    
    override fun observeSteps(): Flow<StepsData> = 
        healthConnectClient.observeWorkoutRecords(
            startTime = startTime,
            workoutState = { state },
            pollingInterval = (configuration?.metadata?.get("pollingInterval") as? Long) ?: 30.seconds.inWholeMilliseconds
        ) { record: StepsRecord ->
            StepsData(
                timestamp = record.startTime.toInstant(),
                count = record.count.toInt()
            )
        }
    
    private fun createMetadata(): Metadata {
        return Metadata.manualEntry()
    }
    
    private fun java.time.Instant.toInstant(): Instant {
        return Instant.fromEpochMilliseconds(this.toEpochMilli())
    }
}