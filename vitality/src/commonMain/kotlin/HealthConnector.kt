package vitality

import vitality.models.*
import vitality.models.fhir.*
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Main interface for health data access across platforms.
 * Provides unified API for HealthKit (iOS) and Health Connect (Android).
 */
interface HealthConnector {
    // Initialization and capabilities
    suspend fun initialize(): Result<Unit>
    suspend fun getAvailableDataTypes(): Set<HealthDataType>
    suspend fun getPlatformCapabilities(): HealthCapabilities
    
    // Permission management
    suspend fun requestPermissions(permissions: Set<HealthPermission>): Result<PermissionResult>
    suspend fun checkPermissions(permissions: Set<HealthPermission>): PermissionStatus
    
    // Synchronous data reading (one-time fetch)
    suspend fun readLatestHeartRate(): Result<HeartRateData?>
    suspend fun readStepsToday(): Result<StepsData>
    suspend fun readCaloriesToday(): Result<CalorieData>
    suspend fun readLatestWeight(): Result<BodyMeasurements?>
    suspend fun readWorkouts(startDate: Instant, endDate: Instant): Result<List<WorkoutData>>
    suspend fun readHealthData(
        dataType: HealthDataType,
        startDate: Instant,
        endDate: Instant
    ): Result<List<HealthDataPoint>>
    
    // Flow-based data streams (continuous monitoring)
    
    /**
     * Observe real-time health data updates for a specific data type.
     * Returns a Flow of the appropriate data model based on the HealthDataType.
     * 
     * @param dataType The type of health data to observe
     * @param samplingInterval How often to check for new data (platform-specific behavior)
     * @return Flow of health data updates of the appropriate type
     * 
     * Example usage:
     * ```
     * val heartRateFlow: Flow<HeartRateData> = healthConnector.observe(HealthDataType.HeartRate)
     * val stepsFlow: Flow<StepsData> = healthConnector.observe(HealthDataType.Steps)
     * ```
     */
    fun <T> observe(
        dataType: HealthDataType,
        samplingInterval: Duration = 30.seconds
    ): Flow<T>
    
    // Specialized workout observation with complex session management
    fun observeActiveWorkout(): Flow<WorkoutData>
    
    // Workout session management
    suspend fun startWorkoutSession(
        workoutType: WorkoutType
    ): Result<WorkoutSession>
    
    suspend fun startWorkoutSession(
        configuration: WorkoutConfiguration
    ): Result<WorkoutSession>
    suspend fun pauseWorkoutSession(sessionId: String): Result<Unit>
    suspend fun resumeWorkoutSession(sessionId: String): Result<Unit>
    suspend fun endWorkoutSession(sessionId: String): Result<Unit>
    suspend fun discardWorkoutSession(sessionId: String): Result<Unit>
    
    // Data writing
    suspend fun writeWeight(weight: Double, unit: WeightUnit, timestamp: Instant = Clock.System.now()): Result<Unit>
    suspend fun writeWorkout(workoutData: WorkoutData): Result<Unit>
    suspend fun writeHealthData(dataPoint: HealthDataPoint): Result<Unit>
    
    // Statistical queries
    suspend fun readStatistics(
        dataType: HealthDataType,
        startDate: Instant,
        endDate: Instant,
        statisticOptions: Set<StatisticOption> = setOf(StatisticOption.AVERAGE),
        bucketDuration: Duration? = null
    ): Result<HealthStatistics>
    
    // Clinical Records (FHIR) - Medical data from healthcare providers
    /**
     * Read immunization records from the health store
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @return List of FHIR Immunization resources
     */
    suspend fun readImmunizations(
        startDate: Instant? = null,
        endDate: Instant? = null
    ): Result<List<FHIRImmunization>>
    
    /**
     * Read medication statements and requests from the health store
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @return List of FHIR MedicationStatement and MedicationRequest resources
     */
    suspend fun readMedications(
        startDate: Instant? = null,
        endDate: Instant? = null
    ): Result<List<FHIRResource>>
    
    /**
     * Read allergy and intolerance records from the health store
     * @param includeInactive Include inactive/resolved allergies
     * @return List of FHIR AllergyIntolerance resources
     */
    suspend fun readAllergies(
        includeInactive: Boolean = false
    ): Result<List<FHIRAllergyIntolerance>>
    
    /**
     * Read medical conditions/diagnoses from the health store
     * @param includeResolved Include resolved conditions
     * @return List of FHIR Condition resources
     */
    suspend fun readConditions(
        includeResolved: Boolean = false
    ): Result<List<FHIRCondition>>
    
    /**
     * Read lab results and observations from the health store
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @param category Optional category filter (e.g., "laboratory", "vital-signs")
     * @return List of FHIR Observation resources
     */
    suspend fun readLabResults(
        startDate: Instant? = null,
        endDate: Instant? = null,
        category: String? = null
    ): Result<List<FHIRObservation>>
    
    /**
     * Read medical procedures from the health store
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @return List of FHIR Procedure resources
     */
    suspend fun readProcedures(
        startDate: Instant? = null,
        endDate: Instant? = null
    ): Result<List<FHIRProcedure>>
    
    /**
     * Check if clinical records are available on this platform
     * @return true if clinical records can be accessed
     */
    suspend fun areClinicalRecordsAvailable(): Boolean
}