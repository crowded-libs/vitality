package vitality

import vitality.models.*
import HealthKitBindings.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toNSDate
import platform.Foundation.*
import platform.HealthKit.*
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.seconds

/**
 * iOS implementation of WorkoutSession using Swift WorkoutSessionManager
 */
@OptIn(ExperimentalForeignApi::class)
class HealthKitWorkoutSession(
    override val id: String,
    override val type: WorkoutType,
    override val startTime: Instant,
    private val healthStore: HKHealthStore,
    private val configuration: WorkoutConfiguration? = null
) : WorkoutSession {
    
    private val sessionManager = WorkoutSessionManager()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _state = MutableStateFlow(WorkoutState.PREPARING)
    override var state: WorkoutState
        get() = _state.value
        private set(value) { _state.value = value }
    
    private var workoutTitle: String? = null
    private var workoutMetadata = mutableMapOf<String, Any>()
    private var locations = mutableListOf<LocationData>()
    private var currentSegmentIndex = 0
    
    override val isWearableSession: Boolean = false
    
    val isActive: Boolean
        get() = sessionManager.isActive()
    
    val stateFlow: StateFlow<WorkoutState> = _state.asStateFlow()
    
    private val heartRateFlow = MutableStateFlow<HeartRateData?>(null)
    private val calorieFlow = MutableStateFlow<CalorieData?>(null)
    private val distanceFlow = MutableStateFlow<DistanceData?>(null)
    private val elevationFlow = MutableStateFlow<Double?>(null)
    private val cadenceFlow = MutableStateFlow<Double?>(null)
    private val powerFlow = MutableStateFlow<Double?>(null)
    private val speedFlow = MutableStateFlow<Double?>(null)
    private val strokeCountFlow = MutableStateFlow<Double?>(null)
    
    private val workoutDelegate = object : NSObject(), WorkoutDataDelegateProtocol {
        override fun workoutDidUpdateHeartRate(heartRate: Double, timestamp: NSDate, metadata: SampleMetadata?) {
            val data = HeartRateData(
                timestamp = timestamp.toKotlinInstant(),
                bpm = heartRate.toInt(),
            )
            heartRateFlow.value = data
        }
        
        override fun workoutDidUpdateCalories(activeCalories: Double, basalCalories: Double, timestamp: NSDate) {
            val data = CalorieData(
                timestamp = timestamp.toKotlinInstant(),
                activeCalories = activeCalories,
                basalCalories = basalCalories
            )
            calorieFlow.value = data
        }
        
        override fun workoutDidUpdateDistance(distance: Double, timestamp: NSDate, metadata: SampleMetadata?) {
            val data = DistanceData(
                timestamp = timestamp.toKotlinInstant(),
                distance = distance,
                unit = DistanceUnit.METERS,
                activityType = when (type) {
                    WorkoutType.RUNNING -> DistanceActivityType.RUNNING
                    WorkoutType.WALKING -> DistanceActivityType.WALKING
                    WorkoutType.CYCLING -> DistanceActivityType.CYCLING
                    WorkoutType.SWIMMING -> DistanceActivityType.SWIMMING
                    else -> DistanceActivityType.OTHER
                }
            )
            distanceFlow.value = data
        }
        
        override fun workoutDidUpdateElevation(elevation: Double, timestamp: NSDate, metadata: SampleMetadata?) {
            elevationFlow.value = elevation
        }
        
        override fun workoutDidUpdateCadence(cadence: Double, timestamp: NSDate, metadata: SampleMetadata?) {
            cadenceFlow.value = cadence
        }
        
        override fun workoutDidUpdatePower(power: Double, timestamp: NSDate, metadata: SampleMetadata?) {
            powerFlow.value = power
        }
        
        override fun workoutDidUpdateSpeed(speed: Double, timestamp: NSDate, metadata: SampleMetadata?) {
            speedFlow.value = speed
        }
        
        override fun workoutDidUpdateStrokeCount(strokeCount: Double, timestamp: NSDate, metadata: SampleMetadata?) {
            strokeCountFlow.value = strokeCount
        }
        
        override fun workoutDidChangeState(state: String) {
            this@HealthKitWorkoutSession.state = when (state) {
                "RUNNING" -> WorkoutState.RUNNING
                "PAUSED" -> WorkoutState.PAUSED
                "STOPPED" -> WorkoutState.STOPPED
                "ENDED" -> WorkoutState.ENDED
                else -> WorkoutState.PREPARING
            }
        }
        
        override fun workoutDidFailWithError(error: String) {
            // Handle error
            println("Workout failed: $error")
            this@HealthKitWorkoutSession.state = WorkoutState.STOPPED
        }
    }
    
    init {
        sessionManager.setDelegate(workoutDelegate)
    }
    
    fun start() {
        val activityType = HealthKitHelper.workoutActivityTypeFrom(type.name)
        val locationType = if (configuration?.isIndoor == true) {
            HKWorkoutSessionLocationTypeIndoor
        } else {
            HKWorkoutSessionLocationTypeOutdoor
        }
        
        sessionManager.startWorkoutWithActivityType(
            activityType,
            locationType
        )
    }
    
    override suspend fun addMetadata(key: String, value: Any): Result<Unit> {
        return try {
            workoutMetadata[key] = value
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun observeLiveData(): Flow<WorkoutData> = callbackFlow {
        scope.launch {
            while (isActive) {
                val currentData = WorkoutData(
                    timestamp = Clock.System.now(),
                    id = id,
                    type = type,
                    startTime = startTime,
                    endTime = null,
                    duration = Clock.System.now() - startTime,
                    activeCalories = null,
                    totalDistance = null,
                    distanceUnit = DistanceUnit.METERS,
                    averageHeartRate = null,
                    maxHeartRate = null,
                    metadata = workoutMetadata
                )
                trySend(currentData)
                delay(1.seconds)
            }
        }
        
        awaitClose {
            // Cleanup handled by session manager
        }
    }
    
    override fun observeHeartRate(): Flow<HeartRateData> = callbackFlow {
        val job = scope.launch {
            heartRateFlow.collect { data ->
                data?.let { trySend(it) }
            }
        }
        
        awaitClose {
            job.cancel()
        }
    }
    
    override fun observeCalories(): Flow<CalorieData> = callbackFlow {
        val job = scope.launch {
            calorieFlow.collect { data ->
                data?.let { trySend(it) }
            }
        }
        
        awaitClose {
            job.cancel()
        }
    }
    
    override fun observeDistance(): Flow<DistanceData> = callbackFlow {
        val job = scope.launch {
            distanceFlow.collect { data ->
                data?.let { trySend(it) }
            }
        }
        
        awaitClose {
            job.cancel()
        }
    }
    
    /**
     * Add a workout event
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun addEvent(
        eventType: WorkoutEventType,
        timestamp: Instant,
        metadata: Map<String, Any>
    ): Result<Unit> = suspendCoroutine { continuation ->
        val hkEventType = when (eventType) {
            WorkoutEventType.PAUSE -> HKWorkoutEventTypePause
            WorkoutEventType.RESUME -> HKWorkoutEventTypeResume
            WorkoutEventType.LAP -> HKWorkoutEventTypeLap
            WorkoutEventType.MARKER -> HKWorkoutEventTypeMarker
            WorkoutEventType.SEGMENT -> HKWorkoutEventTypeSegment
            WorkoutEventType.MOTION_PAUSED -> HKWorkoutEventTypeMotionPaused
            WorkoutEventType.MOTION_RESUMED -> HKWorkoutEventTypeMotionResumed
        }
        
        // Use the Swift API to add workout event
        val nsMetadata: Map<Any?, *>? = if (metadata.isNotEmpty()) {
            metadata.mapKeys { it.key as NSString }.mapValues { it.value }
        } else {
            null
        }
        
        sessionManager.addWorkoutEventWithEventType(
            hkEventType,
            timestamp.toNSDate(),
            nsMetadata
        ) { success, error ->
            if (success) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(
                    Exception("Failed to add workout event: ${error?.localizedDescription}")
                ))
            }
        }
    }
    
    /**
     * Start a new segment (for multi-sport or interval workouts)
     */
    suspend fun startSegment(
        name: String? = null,
        type: WorkoutType? = null
    ): Result<Unit> {
        // Start segment tracking
        currentSegmentIndex++
        
        val metadata = mutableMapOf<String, Any>(
            "segmentName" to (name ?: "Segment $currentSegmentIndex"),
            "segmentType" to (type?.name ?: this.type.name)
        )
        
        // Add segment event to HealthKit
        return addEvent(
            eventType = WorkoutEventType.SEGMENT,
            timestamp = Clock.System.now(),
            metadata = metadata
        )
    }
    
    /**
     * Add GPS location data
     */
    suspend fun addLocation(location: LocationData): Result<Unit> = suspendCoroutine { continuation ->
        // Add location to current segment or route
        locations.add(location)
        
        continuation.resume(Result.success(Unit))
    }
    
    /**
     * Get workout data with all segments and metrics
     */
    suspend fun getWorkoutData(): Result<WorkoutData> = suspendCoroutine { continuation ->
        val title = configuration?.metadata?.get("title") as? String
        if (title != null) {
            workoutTitle = title
        }
        workoutMetadata["sessionId"] = id
        
        
        // Build workout data directly
        val currentTime = Clock.System.now()
        val workoutDuration = currentTime - startTime
        
        val workoutData = WorkoutData(
            timestamp = startTime,
            id = id,
            type = type,
            title = workoutTitle,
            startTime = startTime,
            endTime = currentTime,
            duration = workoutDuration,
            totalCalories = null,
            activeCalories = null,
            totalDistance = null,
            distanceUnit = DistanceUnit.METERS,
            averageHeartRate = null,
            maxHeartRate = null,
            minHeartRate = null,
            elevationGained = workoutMetadata["elevationGained"] as? Double,
            elevationLost = workoutMetadata["elevationLost"] as? Double,
            segments = null,
            routeData = if (locations.isNotEmpty()) locations else null,
            metadata = workoutMetadata.toMap()
        )
        continuation.resume(Result.success(workoutData))
    }
    
    // MARK: - Watch communication handled by app layer
    
    /**
     * Override pause to also pause on watch if mirrored
     */
    override suspend fun pause(): Result<Unit> {
        return try {
            sessionManager.pauseWorkout()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Override resume to also resume on watch if mirrored
     */
    override suspend fun resume(): Result<Unit> {
        return try {
            sessionManager.resumeWorkout()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Override end to also end on watch if mirrored
     */
    override suspend fun end(): Result<Unit> = suspendCoroutine { continuation ->
        sessionManager.endWorkoutWithCompletion { success, error ->
            if (success) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(
                    Exception(error?.localizedDescription ?: "Failed to end workout")
                ))
            }
        }
    }
    
    // Advanced metrics support
    override fun observeSpeed(): Flow<SpeedData> = callbackFlow {
        launch {
            speedFlow.collect { speed ->
                speed?.let {
                    trySend(SpeedData(
                        timestamp = Clock.System.now(),
                        speedMetersPerSecond = it
                    ))
                }
            }
        }
        awaitClose()
    }
    
    override fun observePower(): Flow<PowerData> = callbackFlow {
        launch {
            powerFlow.collect { power ->
                power?.let {
                    trySend(PowerData(
                        timestamp = Clock.System.now(),
                        watts = it
                    ))
                }
            }
        }
        awaitClose()
    }
    
    override fun observeCadence(): Flow<CyclingCadenceData> = callbackFlow {
        launch {
            cadenceFlow.collect { cadence ->
                cadence?.let {
                    trySend(CyclingCadenceData(
                        timestamp = Clock.System.now(),
                        rpm = it.toInt()
                    ))
                }
            }
        }
        awaitClose()
    }
    
    override fun observeSteps(): Flow<StepsData> = callbackFlow {
        val stepsType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount) ?: return@callbackFlow
        
        // Create observer query for real-time step updates
        val query = HKObserverQuery(
            sampleType = stepsType,
            predicate = HKQuery.predicateForSamplesWithStartDate(
                startDate = startTime.toNSDate(),
                endDate = null,
                options = HKQueryOptionNone
            )
        ) { _, completionHandler, error ->
            if (error == null) {
                // Query for the actual step data when observer fires
                val stepsQuery = HKSampleQuery(
                    sampleType = stepsType,
                    predicate = HKQuery.predicateForSamplesWithStartDate(
                        startDate = startTime.toNSDate(),
                        endDate = NSDate(),
                        options = HKQueryOptionNone
                    ),
                    limit = HKObjectQueryNoLimit,
                    sortDescriptors = listOf(
                        NSSortDescriptor(key = HKSampleSortIdentifierStartDate, ascending = true)
                    )
                ) { _, samples, queryError ->
                    if (queryError == null && samples != null) {
                        val totalSteps = samples.filterIsInstance<HKQuantitySample>()
                            .sumOf { sample ->
                                sample.quantity.doubleValueForUnit(HKUnit.countUnit()).toInt()
                            }
                        
                        trySend(StepsData(
                            timestamp = Clock.System.now(),
                            count = totalSteps,
                            metadata = mapOf(
                                "source" to "HealthKit",
                                "isRealTime" to true
                            )
                        ))
                    }
                }
                
                healthStore.executeQuery(stepsQuery)
            }
            
            completionHandler?.invoke()
        }
        
        healthStore.executeQuery(query)
        
        awaitClose {
            healthStore.stopQuery(query)
        }
    }
    
}