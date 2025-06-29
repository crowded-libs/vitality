package vitality

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toNSDate
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.*
import platform.HealthKit.*
import vitality.exceptions.*
import vitality.extensions.*
import vitality.healthkit.ClinicalRecordReader
import vitality.healthkit.ECGReader
import vitality.healthkit.HealthKitMetadataExtractor
import vitality.helpers.observeCategoryType
import vitality.models.*
import vitality.models.fhir.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toDuration

/**
 * iOS HealthKit implementation of HealthConnector
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class HealthKitConnector : HealthConnector {
    private val healthStore = HKHealthStore()
    private val workoutSessions = mutableMapOf<String, HealthKitWorkoutSession>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override suspend fun initialize(): Result<Unit> = suspendCoroutine { continuation ->
        if (!HKHealthStore.isHealthDataAvailable()) {
            continuation.resume(Result.failure(
                HealthKitException.DataUnavailableException(
                    dataType = null,
                    errorCode = null,
                    errorDomain = "HealthKitConnector"
                )
            ))
            return@suspendCoroutine
        }

        continuation.resume(Result.success(Unit))
    }
    
    override suspend fun getAvailableDataTypes(): Set<HealthDataType> {
        val available = mutableSetOf<HealthDataType>()
        
        // Check common data types
        if (HKHealthStore.isHealthDataAvailable()) {
            available.addAll(listOf(
                HealthDataType.Steps,
                HealthDataType.Distance,
                HealthDataType.Calories,
                HealthDataType.ActiveCalories,
                HealthDataType.BasalCalories,
                HealthDataType.Floors,
                HealthDataType.HeartRate,
                HealthDataType.HeartRateVariability,
                HealthDataType.BloodPressure,
                HealthDataType.OxygenSaturation,
                HealthDataType.RespiratoryRate,
                HealthDataType.BodyTemperature,
                HealthDataType.Weight,
                HealthDataType.Height,
                HealthDataType.BodyFat,
                HealthDataType.BMI,
                HealthDataType.Workout,
                HealthDataType.Sleep,
                HealthDataType.Water,
                HealthDataType.BloodGlucose,
                HealthDataType.VO2Max
            ))
            
            if (isIOS14OrLater()) {
                available.addAll(listOf(
                    HealthDataType.WalkingAsymmetry,
                    HealthDataType.WalkingDoubleSupportPercentage,
                    HealthDataType.WalkingSpeed,
                    HealthDataType.WalkingStepLength,
                    HealthDataType.StairAscentSpeed,
                    HealthDataType.StairDescentSpeed,
                    HealthDataType.SixMinuteWalkTestDistance,
                    HealthDataType.NumberOfTimesFallen,
                    HealthDataType.StandHours
                ))
            }
            
            available.addAll(listOf(
                HealthDataType.EnvironmentalAudioExposure,
                HealthDataType.HeadphoneAudioExposure,
                HealthDataType.UVExposure
            ))
            
            // Advanced Workout Metrics
            if (isIOS16OrLater()) {
                available.addAll(listOf(
                    HealthDataType.RunningStrideLength,
                    HealthDataType.RunningVerticalOscillation,
                    HealthDataType.RunningGroundContactTime
                ))
            }
            
            if (isIOS17OrLater()) {
                available.addAll(listOf(
                    HealthDataType.CyclingCadence,
                    HealthDataType.CyclingPower,
                    HealthDataType.CyclingFunctionalThresholdPower
                ))
            }
            
            // Clinical types not yet implemented:
            // - Electrocardiogram (requires Swift interop)
            // - IrregularHeartRhythmEvent
            // - PeripheralPerfusionIndex
            
            // Mindfulness
            available.add(HealthDataType.Mindfulness)
        }
        
        return available
    }
    
    override suspend fun getPlatformCapabilities(): HealthCapabilities {
        return HealthCapabilities(
            platformName = "iOS HealthKit",
            supportsBackgroundDelivery = true,
            supportsWearableIntegration = true,
            supportsLiveWorkoutMetrics = true,
            availableDataTypes = getAvailableDataTypes(),
            unavailableDataTypes = emptySet(),
            supportedWorkoutTypes = WorkoutType.entries.toSet(),
            platformSpecificCapabilities = mapOf(
                "supportsRouteData" to true,
                "supportsWheelchairUse" to true,
                "supportsAppleWatch" to true
            )
        )
    }
    
    override suspend fun requestPermissions(
        permissions: Set<HealthPermission>
    ): Result<PermissionResult> = suspendCoroutine { continuation ->
        val readTypes = mutableSetOf<HKSampleType>()
        val writeTypes = mutableSetOf<HKSampleType>()
        
        permissions.forEach { permission ->
            val hkType = permission.dataType.toHKSampleType() ?: return@forEach
            
            when (permission.accessType) {
                HealthPermission.AccessType.READ -> readTypes.add(hkType)
                HealthPermission.AccessType.WRITE -> writeTypes.add(hkType)
            }
        }
        
        healthStore.requestAuthorizationToShareTypes(
            writeTypes,
            readTypes
        ) { success, error ->
            if (success) {
                // We assume all were handled according to user choice
                continuation.resume(Result.success(
                    PermissionResult(
                        granted = permissions,
                        denied = emptySet()
                    )
                ))
            } else {
                val exception = error?.let { nsError ->
                    createHealthKitException(nsError, "requestPermissions")
                } ?: HealthKitException.AuthorizationException(
                    deniedPermissions = permissions,
                    errorCode = null,
                    errorDomain = "HKErrorDomain"
                )
                continuation.resume(Result.failure(exception))
            }
        }
    }
    
    override suspend fun checkPermissions(permissions: Set<HealthPermission>): PermissionStatus {
        var hasAskedForAll = true
        
        permissions.forEach { permission ->
            val hkType = permission.dataType.toHKSampleType() ?: return@forEach
            
            val authStatus = when (permission.accessType) {
                HealthPermission.AccessType.READ -> 
                    healthStore.authorizationStatusForType(hkType)
                HealthPermission.AccessType.WRITE -> 
                    healthStore.authorizationStatusForType(hkType)
            }
            
            if (authStatus == HKAuthorizationStatusNotDetermined) {
                hasAskedForAll = false
            }
        }
        
        return if (hasAskedForAll) {
            PermissionStatus.Granted // We assume granted if we've asked
        } else {
            PermissionStatus.NotDetermined
        }
    }
    
    /**
     * Generic reader for latest quantity sample
     */
    private suspend fun <T> readLatestQuantitySample(
        quantityTypeIdentifier: String?,
        dataType: HealthDataType,
        converter: (HKQuantitySample) -> T
    ): Result<T?> = suspendCoroutine { continuation ->
        val quantityType = quantityTypeIdentifier?.let { HKQuantityType.quantityTypeForIdentifier(it) }
            ?: return@suspendCoroutine continuation.resume(Result.failure(
                HealthKitException.DataUnavailableException(
                    dataType = dataType
                )
            ))
        
        val sortDescriptor = NSSortDescriptor.sortDescriptorWithKey("startDate", ascending = false)
        val query = HKSampleQuery(
            quantityType,
            null,
            1u,
            listOf(sortDescriptor)
        ) { _, samples, error ->
            if (error != null) {
                continuation.resume(Result.failure(Exception(error.localizedDescription)))
                return@HKSampleQuery
            }
            
            val sample = (samples?.firstOrNull() as? HKQuantitySample)
            continuation.resume(Result.success(sample?.let(converter)))
        }
        
        healthStore.executeQuery(query)
    }
    
    override suspend fun readLatestHeartRate(): Result<HeartRateData?> = 
        readLatestQuantitySample(
            HKQuantityTypeIdentifierHeartRate,
            HealthDataType.HeartRate
        ) { it.toHeartRateData() }
    
    override suspend fun readStepsToday(): Result<StepsData> = suspendCoroutine { continuation ->
        val stepsType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount)
            ?: return@suspendCoroutine continuation.resume(Result.failure(
                HealthKitException.DataUnavailableException(
                    dataType = HealthDataType.Steps
                )
            ))
        
        val calendar = NSCalendar.currentCalendar
        val now = NSDate()
        val startOfDay = calendar.startOfDayForDate(now)
        val predicate = HKQuery.predicateForSamplesWithStartDate(startOfDay, endDate = now, options = HKQueryOptionNone)
        
        val query = HKStatisticsQuery(
            quantityType = stepsType,
            quantitySamplePredicate = predicate,
            options = HKStatisticsOptionCumulativeSum
        ) { _, statistics, error ->
            if (error != null) {
                continuation.resume(Result.failure(Exception(error.localizedDescription)))
                return@HKStatisticsQuery
            }
            
            val sum = statistics?.sumQuantity()?.doubleValueForUnit(HKUnit.countUnit()) ?: 0.0
            
            continuation.resume(Result.success(
                StepsData(
                    timestamp = Clock.System.now(),
                    count = sum.toInt(),
                    startTime = startOfDay.toKotlinInstant(),
                    endTime = Clock.System.now()
                )
            ))
        }
        
        healthStore.executeQuery(query)
    }
    
    override suspend fun readCaloriesToday(): Result<CalorieData> = suspendCoroutine { continuation ->
        val activeType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned)
        val basalType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBasalEnergyBurned)
        
        if (activeType == null || basalType == null) {
            return@suspendCoroutine continuation.resume(Result.failure(
                HealthConnectorException.DataNotAvailable(HealthDataType.Calories)
            ))
        }
        
        val calendar = NSCalendar.currentCalendar
        val now = NSDate()
        val startOfDay = calendar.startOfDayForDate(now)
        val predicate = HKQuery.predicateForSamplesWithStartDate(startOfDay, endDate = now, options = HKQueryOptionNone)
        
        var activeCalories = 0.0
        var basalCalories = 0.0
        var queriesCompleted = 0
        
        fun checkCompletion() {
            queriesCompleted++
            if (queriesCompleted == 2) {
                continuation.resume(Result.success(
                    CalorieData(
                        timestamp = Clock.System.now(),
                        activeCalories = activeCalories,
                        basalCalories = basalCalories
                    )
                ))
            }
        }
        
        // Query active calories
        val activeQuery = HKStatisticsQuery(
            quantityType = activeType,
            quantitySamplePredicate = predicate,
            options = HKStatisticsOptionCumulativeSum
        ) { _, statistics, _ ->
            activeCalories = statistics?.sumQuantity()?.doubleValueForUnit(HKUnit.kilocalorieUnit()) ?: 0.0
            checkCompletion()
        }
        
        // Query basal calories
        val basalQuery = HKStatisticsQuery(
            quantityType = basalType,
            quantitySamplePredicate = predicate,
            options = HKStatisticsOptionCumulativeSum
        ) { _, statistics, _ ->
            basalCalories = statistics?.sumQuantity()?.doubleValueForUnit(HKUnit.kilocalorieUnit()) ?: 0.0
            checkCompletion()
        }
        
        healthStore.executeQuery(activeQuery)
        healthStore.executeQuery(basalQuery)
    }
    
    override suspend fun readLatestWeight(): Result<BodyMeasurements?> = 
        readLatestQuantitySample(
            HKQuantityTypeIdentifierBodyMass,
            HealthDataType.Weight
        ) { sample ->
            val weight = sample.quantity.doubleValueForUnit(HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixKilo))
            BodyMeasurements(
                timestamp = sample.startDate.toKotlinInstant(),
                weight = weight,
                weightUnit = WeightUnit.KILOGRAMS
            )
        }
    
    // New read methods for mobility data
    suspend fun readLatestWalkingAsymmetry(): Result<WalkingAsymmetryData?> = 
        readLatestQuantitySample(
            HKQuantityTypeIdentifierWalkingAsymmetryPercentage,
            HealthDataType.WalkingAsymmetry
        ) { sample ->
            sample.toWalkingAsymmetryData()
        }
    
    suspend fun readLatestWalkingSpeed(): Result<WalkingSpeedData?> = 
        readLatestQuantitySample(
            HKQuantityTypeIdentifierWalkingSpeed,
            HealthDataType.WalkingSpeed
        ) { sample ->
            sample.toWalkingSpeedData()
        }
    
    suspend fun readWalkingStepLength(
        startDate: Instant,
        endDate: Instant
    ): Result<List<WalkingStepLengthData>> = suspendCoroutine { continuation ->
        val quantityType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingStepLength)
            ?: return@suspendCoroutine continuation.resume(Result.failure(
                HealthConnectorException.DataNotAvailable(HealthDataType.WalkingStepLength)
            ))
        
        val predicate = HKQuery.predicateForSamplesWithStartDate(
            startDate.toNSDate(),
            endDate = endDate.toNSDate(),
            options = HKQueryOptionNone
        )
        
        val query = HKSampleQuery(
            quantityType,
            predicate,
            HKObjectQueryNoLimit,
            listOf(NSSortDescriptor.sortDescriptorWithKey("startDate", ascending = true))
        ) { _, samples, error ->
            if (error != null) {
                continuation.resume(Result.failure(Exception(error.localizedDescription)))
                return@HKSampleQuery
            }
            
            val dataPoints = samples?.mapNotNull { sample ->
                (sample as? HKQuantitySample)?.let {
                    WalkingStepLengthData(
                        timestamp = it.startDate.toKotlinInstant(),
                        stepLength = it.quantity.doubleValueForUnit(HKUnit.meterUnit()),
                        source = createDataSource(it),
                        metadata = createSampleMetadata(it)
                    )
                }
            } ?: emptyList()
            
            continuation.resume(Result.success(dataPoints))
        }
        
        healthStore.executeQuery(query)
    }
    
    // Audio exposure read methods
    suspend fun readEnvironmentalAudioExposure(
        startDate: Instant,
        endDate: Instant
    ): Result<List<EnvironmentalAudioExposureData>> = suspendCoroutine { continuation ->
        val quantityType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierEnvironmentalAudioExposure)
            ?: return@suspendCoroutine continuation.resume(Result.failure(
                HealthConnectorException.DataNotAvailable(HealthDataType.EnvironmentalAudioExposure)
            ))
        
        val predicate = HKQuery.predicateForSamplesWithStartDate(
            startDate.toNSDate(),
            endDate = endDate.toNSDate(),
            options = HKQueryOptionNone
        )
        
        val query = HKSampleQuery(
            quantityType,
            predicate,
            HKObjectQueryNoLimit,
            listOf(NSSortDescriptor.sortDescriptorWithKey("startDate", ascending = true))
        ) { _, samples, error ->
            if (error != null) {
                continuation.resume(Result.failure(Exception(error.localizedDescription)))
                return@HKSampleQuery
            }
            
            val dataPoints = samples?.mapNotNull { sample ->
                (sample as? HKQuantitySample)?.toEnvironmentalAudioExposureData()
            } ?: emptyList()
            
            continuation.resume(Result.success(dataPoints))
        }
        
        healthStore.executeQuery(query)
    }
    
    // Advanced workout metrics
    suspend fun readRunningStrideLength(
        startDate: Instant,
        endDate: Instant
    ): Result<List<RunningStrideLengthData>> = if (isIOS16OrLater()) {
        suspendCoroutine { continuation ->
            val quantityType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningStrideLength)
                ?: return@suspendCoroutine continuation.resume(Result.failure(
                    HealthConnectorException.DataNotAvailable(HealthDataType.RunningStrideLength)
                ))
            
            val predicate = HKQuery.predicateForSamplesWithStartDate(
                startDate.toNSDate(),
                endDate = endDate.toNSDate(),
                options = HKQueryOptionNone
            )
            
            val query = HKSampleQuery(
                quantityType,
                predicate,
                HKObjectQueryNoLimit,
                listOf(NSSortDescriptor.sortDescriptorWithKey("startDate", ascending = true))
            ) { _, samples, error ->
                if (error != null) {
                    continuation.resume(Result.failure(Exception(error.localizedDescription)))
                    return@HKSampleQuery
                }
                
                val dataPoints = samples?.mapNotNull { sample ->
                    (sample as? HKQuantitySample)?.toRunningStrideLengthData()
                } ?: emptyList()
                
                continuation.resume(Result.success(dataPoints))
            }
            
            healthStore.executeQuery(query)
        }
    } else {
        Result.failure(HealthKitException.VersionException(
            requiredVersion = "16.0",
            currentVersion = getCurrentOSVersion(),
            feature = "Running stride length"
        ))
    }
    
    suspend fun readCyclingPower(
        startDate: Instant,
        endDate: Instant
    ): Result<List<PowerData>> = if (isIOS17OrLater()) {
        suspendCoroutine { continuation ->
            val quantityType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingPower)
                ?: return@suspendCoroutine continuation.resume(Result.failure(
                    HealthConnectorException.DataNotAvailable(HealthDataType.CyclingPower)
                ))
            
            val predicate = HKQuery.predicateForSamplesWithStartDate(
                startDate.toNSDate(),
                endDate = endDate.toNSDate(),
                options = HKQueryOptionNone
            )
            
            val query = HKSampleQuery(
                quantityType,
                predicate,
                HKObjectQueryNoLimit,
                listOf(NSSortDescriptor.sortDescriptorWithKey("startDate", ascending = true))
            ) { _, samples, error ->
                if (error != null) {
                    continuation.resume(Result.failure(Exception(error.localizedDescription)))
                    return@HKSampleQuery
                }
                
                val dataPoints = samples?.mapNotNull { sample ->
                    (sample as? HKQuantitySample)?.toPowerData()
                } ?: emptyList()
                
                continuation.resume(Result.success(dataPoints))
            }
            
            healthStore.executeQuery(query)
        }
    } else {
        Result.failure(HealthKitException.VersionException(
            requiredVersion = "17.0",
            currentVersion = getCurrentOSVersion(),
            feature = "Cycling power"
        ))
    }
    
    // Mindfulness read method
    suspend fun readMindfulnessSessions(
        startDate: Instant,
        endDate: Instant
    ): Result<List<MindfulnessSessionData>> = suspendCoroutine { continuation ->
        val categoryType = HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierMindfulSession)
            ?: return@suspendCoroutine continuation.resume(Result.failure(
                HealthConnectorException.DataNotAvailable(HealthDataType.Mindfulness)
            ))
        
        val predicate = HKQuery.predicateForSamplesWithStartDate(
            startDate.toNSDate(),
            endDate = endDate.toNSDate(),
            options = HKQueryOptionNone
        )
        
        val query = HKSampleQuery(
            categoryType,
            predicate,
            HKObjectQueryNoLimit,
            listOf(NSSortDescriptor.sortDescriptorWithKey("startDate", ascending = true))
        ) { _, samples, error ->
            if (error != null) {
                continuation.resume(Result.failure(Exception(error.localizedDescription)))
                return@HKSampleQuery
            }
            
            val dataPoints = samples?.mapNotNull { sample ->
                (sample as? HKCategorySample)?.toMindfulnessSessionData()
            } ?: emptyList()
            
            continuation.resume(Result.success(dataPoints))
        }
        
        healthStore.executeQuery(query)
    }
    
    // Background delivery setup
    suspend fun enableBackgroundDelivery(
        dataTypes: Set<HealthDataType>,
        updateFrequency: BackgroundDeliveryFrequency = BackgroundDeliveryFrequency.HOURLY
    ): Result<Unit> = suspendCoroutine { continuation ->
        var successCount = 0
        var errorCount = 0
        val totalTypes = dataTypes.size
        
        dataTypes.forEach { dataType ->
            val sampleType = dataType.toHKSampleType()
            if (sampleType != null) {
                val frequency = when (updateFrequency) {
                    BackgroundDeliveryFrequency.IMMEDIATE -> HKUpdateFrequencyImmediate
                    BackgroundDeliveryFrequency.HOURLY -> HKUpdateFrequencyHourly
                    BackgroundDeliveryFrequency.DAILY -> HKUpdateFrequencyDaily
                    BackgroundDeliveryFrequency.WEEKLY -> HKUpdateFrequencyWeekly
                }
                
                healthStore.enableBackgroundDeliveryForType(
                    sampleType,
                    frequency = frequency
                ) { success, error ->
                    if (success) {
                        successCount++
                    } else {
                        errorCount++
                        println("Failed to enable background delivery for ${dataType}: ${error?.localizedDescription}")
                    }
                    
                    // Check if all types have been processed
                    if (successCount + errorCount == totalTypes) {
                        if (errorCount == 0) {
                            continuation.resume(Result.success(Unit))
                        } else {
                            continuation.resume(Result.failure(
                                HealthConnectorException.DataSyncError(
                                    "Failed to enable background delivery for $errorCount out of $totalTypes data types"
                                )
                            ))
                        }
                    }
                }
            } else {
                errorCount++
                if (successCount + errorCount == totalTypes) {
                    continuation.resume(Result.failure(
                        HealthConnectorException.DataSyncError(
                            "Failed to enable background delivery for $errorCount out of $totalTypes data types"
                        )
                    ))
                }
            }
        }
    }
    
    suspend fun disableBackgroundDelivery(
        dataTypes: Set<HealthDataType>
    ): Result<Unit> = suspendCoroutine { continuation ->
        var successCount = 0
        var errorCount = 0
        val totalTypes = dataTypes.size
        
        dataTypes.forEach { dataType ->
            val sampleType = dataType.toHKSampleType()
            if (sampleType != null) {
                healthStore.disableBackgroundDeliveryForType(sampleType) { success, error ->
                    if (success) {
                        successCount++
                    } else {
                        errorCount++
                    }
                    
                    if (successCount + errorCount == totalTypes) {
                        if (errorCount == 0) {
                            continuation.resume(Result.success(Unit))
                        } else {
                            continuation.resume(Result.failure(
                                HealthConnectorException.DataSyncError(
                                    "Failed to disable background delivery for $errorCount out of $totalTypes data types"
                                )
                            ))
                        }
                    }
                }
            } else {
                errorCount++
            }
        }
    }
    
    override suspend fun readWorkouts(
        startDate: Instant,
        endDate: Instant
    ): Result<List<WorkoutData>> = suspendCoroutine { continuation ->
        val predicate = HKQuery.predicateForSamplesWithStartDate(
            startDate.toNSDate(),
            endDate = endDate.toNSDate(),
            options = HKQueryOptionNone
        )
        
        val sortDescriptor = NSSortDescriptor.sortDescriptorWithKey("startDate", ascending = false)
        val query = HKSampleQuery(
            HKWorkoutType.workoutType(),
            predicate,
            HKObjectQueryNoLimit,
            listOf(sortDescriptor)
        ) { query, samples, error ->
            if (error != null) {
                continuation.resume(Result.failure(Exception(error.localizedDescription)))
                return@HKSampleQuery
            }
            
            val workouts = samples?.mapNotNull { sample ->
                (sample as? HKWorkout)?.toWorkoutData()
            } ?: emptyList()
            
            continuation.resume(Result.success(workouts))
        }
        
        healthStore.executeQuery(query)
    }
    
    override suspend fun readHealthData(
        dataType: HealthDataType,
        startDate: Instant,
        endDate: Instant
    ): Result<List<HealthDataPoint>> = suspendCoroutine { continuation ->
        val sampleType = dataType.toHKSampleType()
        if (sampleType == null) {
            continuation.resume(Result.failure(
                HealthKitException.DataUnavailableException(
                    dataType = dataType,
                )
            ))
            return@suspendCoroutine
        }
        
        val predicate = HKQuery.predicateForSamplesWithStartDate(
            startDate.toNSDate(),
            endDate = endDate.toNSDate(),
            options = HKQueryOptionNone
        )
        
        val sortDescriptor = NSSortDescriptor.sortDescriptorWithKey("startDate", ascending = true)
        val query = HKSampleQuery(
            sampleType,
            predicate,
            HKObjectQueryNoLimit,
            listOf(sortDescriptor)
        ) { query, samples, error ->
            if (error != null) {
                continuation.resume(Result.failure(Exception(error.localizedDescription)))
                return@HKSampleQuery
            }
            
            val dataPoints = samples?.mapNotNull { sample ->
                when (dataType) {
                    HealthDataType.HeartRate -> {
                        (sample as? HKQuantitySample)?.let {
                            HeartRateData(
                                timestamp = it.startDate.toKotlinInstant(),
                                bpm = it.quantity.doubleValueForUnit(HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit())).toInt(),
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    HealthDataType.Steps -> {
                        (sample as? HKQuantitySample)?.let {
                            StepsData(
                                timestamp = it.startDate.toKotlinInstant(),
                                count = it.quantity.doubleValueForUnit(HKUnit.countUnit()).toInt(),
                                startTime = it.startDate.toKotlinInstant(),
                                endTime = it.endDate.toKotlinInstant(),
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    HealthDataType.Distance -> {
                        (sample as? HKQuantitySample)?.let {
                            DistanceData(
                                timestamp = it.startDate.toKotlinInstant(),
                                distance = it.quantity.doubleValueForUnit(HKUnit.meterUnit()),
                                unit = DistanceUnit.METERS,
                                activityType = DistanceActivityType.OTHER,
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    HealthDataType.ActiveCalories, HealthDataType.Calories -> {
                        (sample as? HKQuantitySample)?.let {
                            val calories = it.quantity.doubleValueForUnit(HKUnit.kilocalorieUnit())
                            CalorieData(
                                timestamp = it.startDate.toKotlinInstant(),
                                activeCalories = if (dataType == HealthDataType.ActiveCalories) calories else 0.0,
                                basalCalories = if (dataType == HealthDataType.BasalCalories) calories else 0.0,
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    HealthDataType.Weight -> {
                        (sample as? HKQuantitySample)?.let {
                            val weight = it.quantity.doubleValueForUnit(HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixKilo))
                            BodyMeasurements(
                                timestamp = it.startDate.toKotlinInstant(),
                                weight = weight,
                                weightUnit = WeightUnit.KILOGRAMS,
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    HealthDataType.BloodPressure -> {
                        (sample as? HKCorrelation)?.let { correlation ->
                            val systolicType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBloodPressureSystolic)
                            val diastolicType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBloodPressureDiastolic)
                            
                            val systolicSample = systolicType?.let { 
                                correlation.objectsForType(it).firstOrNull() as? HKQuantitySample
                            }
                            val diastolicSample = diastolicType?.let { 
                                correlation.objectsForType(it).firstOrNull() as? HKQuantitySample
                            }
                            
                            if (systolicSample != null && diastolicSample != null) {
                                BloodPressureData(
                                    timestamp = correlation.startDate.toKotlinInstant(),
                                    systolic = systolicSample.quantity.doubleValueForUnit(HKUnit.millimeterOfMercuryUnit()).toInt(),
                                    diastolic = diastolicSample.quantity.doubleValueForUnit(HKUnit.millimeterOfMercuryUnit()).toInt(),
                                    source = createDataSource(correlation),
                                    metadata = createSampleMetadata(correlation)
                                )
                            } else null
                        }
                    }
                    // Mobility data types
                    HealthDataType.WalkingAsymmetry -> {
                        (sample as? HKQuantitySample)?.toWalkingAsymmetryData()
                    }
                    HealthDataType.WalkingSpeed -> {
                        (sample as? HKQuantitySample)?.toWalkingSpeedData()
                    }
                    HealthDataType.WalkingStepLength -> {
                        (sample as? HKQuantitySample)?.let {
                            WalkingStepLengthData(
                                timestamp = it.startDate.toKotlinInstant(),
                                stepLength = it.quantity.doubleValueForUnit(HKUnit.meterUnit()),
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    // Audio exposure
                    HealthDataType.EnvironmentalAudioExposure -> {
                        (sample as? HKQuantitySample)?.toEnvironmentalAudioExposureData()
                    }
                    HealthDataType.HeadphoneAudioExposure -> {
                        (sample as? HKQuantitySample)?.let {
                            HeadphoneAudioExposureData(
                                timestamp = it.startDate.toKotlinInstant(),
                                level = it.quantity.doubleValueForUnit(HKUnit.decibelAWeightedSoundPressureLevelUnit()),
                                duration = it.endDate.timeIntervalSinceDate(it.startDate).toDuration(kotlin.time.DurationUnit.SECONDS),
                                startTime = it.startDate.toKotlinInstant(),
                                endTime = it.endDate.toKotlinInstant(),
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    // Advanced workout metrics
                    HealthDataType.RunningStrideLength -> {
                        (sample as? HKQuantitySample)?.toRunningStrideLengthData()
                    }
                    HealthDataType.CyclingPower -> {
                        (sample as? HKQuantitySample)?.toPowerData()
                    }
                    // Mindfulness
                    HealthDataType.Mindfulness -> {
                        (sample as? HKCategorySample)?.toMindfulnessSessionData()
                    }
                    
                    // Reproductive Health types
                    HealthDataType.MenstruationFlow -> {
                        (sample as? HKCategorySample)?.toMenstruationFlowData()
                    }
                    HealthDataType.IntermenstrualBleeding -> {
                        (sample as? HKCategorySample)?.toIntermenstrualBleedingData()
                    }
                    HealthDataType.CervicalMucus -> {
                        (sample as? HKCategorySample)?.toCervicalMucusData()
                    }
                    HealthDataType.OvulationTest -> {
                        (sample as? HKCategorySample)?.toOvulationTestData()
                    }
                    HealthDataType.SexualActivity -> {
                        (sample as? HKCategorySample)?.toSexualActivityData()
                    }
                    
                    // Nutrition types - each returns individual nutrition data
                    HealthDataType.Water -> {
                        (sample as? HKQuantitySample)?.let {
                            HydrationData(
                                timestamp = it.startDate.toKotlinInstant(),
                                volume = it.quantity.doubleValueForUnit(HKUnit.literUnit()) * 1000, // Convert to milliliters
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    HealthDataType.Protein -> {
                        (sample as? HKQuantitySample)?.let {
                            NutritionData(
                                timestamp = it.startDate.toKotlinInstant(),
                                protein = it.quantity.doubleValueForUnit(HKUnit.gramUnit()),
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    HealthDataType.Carbohydrates -> {
                        (sample as? HKQuantitySample)?.let {
                            NutritionData(
                                timestamp = it.startDate.toKotlinInstant(),
                                carbohydrates = it.quantity.doubleValueForUnit(HKUnit.gramUnit()),
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    HealthDataType.Fat -> {
                        (sample as? HKQuantitySample)?.let {
                            NutritionData(
                                timestamp = it.startDate.toKotlinInstant(),
                                fat = it.quantity.doubleValueForUnit(HKUnit.gramUnit()),
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    HealthDataType.Fiber -> {
                        (sample as? HKQuantitySample)?.let {
                            NutritionData(
                                timestamp = it.startDate.toKotlinInstant(),
                                fiber = it.quantity.doubleValueForUnit(HKUnit.gramUnit()),
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    HealthDataType.Sugar -> {
                        (sample as? HKQuantitySample)?.let {
                            NutritionData(
                                timestamp = it.startDate.toKotlinInstant(),
                                sugar = it.quantity.doubleValueForUnit(HKUnit.gramUnit()),
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    HealthDataType.Caffeine -> {
                        (sample as? HKQuantitySample)?.let {
                            NutritionData(
                                timestamp = it.startDate.toKotlinInstant(),
                                caffeine = it.quantity.doubleValueForUnit(HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixMilli)), // milligrams
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    
                    // Resting heart rate
                    HealthDataType.RestingHeartRate -> {
                        (sample as? HKQuantitySample)?.let {
                            RestingHeartRateData(
                                timestamp = it.startDate.toKotlinInstant(),
                                bpm = it.quantity.doubleValueForUnit(HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit())).toInt(),
                                source = createDataSource(it),
                                metadata = createSampleMetadata(it)
                            )
                        }
                    }
                    
                    else -> null
                }
            } ?: emptyList()
            
            continuation.resume(Result.success(dataPoints))
        }
        
        healthStore.executeQuery(query)
    }
    
    /**
     * Generic observer for quantity types that returns raw samples
     */
    private fun <T> observeQuantityType(
        quantityTypeIdentifier: String?,
        lookbackDuration: Duration = 1.minutes,
        unitConverter: (HKQuantitySample) -> T
    ): Flow<T> = callbackFlow {
        val quantityType = quantityTypeIdentifier?.let { HKQuantityType.quantityTypeForIdentifier(it) }
            ?: return@callbackFlow
        
        val observerQuery = HKObserverQuery(
            sampleType = quantityType,
            predicate = null
        ) { _, completionHandler, error ->
            if (error == null) {
                val now = NSDate()
                val startTime = now.dateByAddingTimeInterval(-lookbackDuration.inWholeSeconds.toDouble())
                val predicate = HKQuery.predicateForSamplesWithStartDate(
                    startTime,
                    endDate = now,
                    options = HKQueryOptionNone
                )
                
                val sortDescriptor = NSSortDescriptor.sortDescriptorWithKey("startDate", ascending = true)
                val sampleQuery = HKSampleQuery(
                    quantityType,
                    predicate,
                    HKObjectQueryNoLimit,
                    listOf(sortDescriptor)
                ) { _, samples, _ ->
                    samples?.forEach { sample ->
                        (sample as? HKQuantitySample)?.let {
                            trySend(unitConverter(it))
                        }
                    }
                }
                
                healthStore.executeQuery(sampleQuery)
            }
            
            completionHandler?.let { it() }
        }
        
        healthStore.executeQuery(observerQuery)
        
        awaitClose {
            healthStore.stopQuery(observerQuery)
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    override fun <T> observe(
        dataType: HealthDataType,
        samplingInterval: Duration
    ): Flow<T> = when (dataType) {
        // Quantity types
        HealthDataType.HeartRate -> observeQuantityType(
            quantityTypeIdentifier = HKQuantityTypeIdentifierHeartRate,
            lookbackDuration = 1.minutes
        ) { sample -> sample.toHeartRateData() } as Flow<T>
        
        HealthDataType.Steps -> observeQuantityType(
            quantityTypeIdentifier = HKQuantityTypeIdentifierStepCount,
            lookbackDuration = 5.minutes
        ) { sample ->
            val steps = sample.quantity.doubleValueForUnit(HKUnit.countUnit()).toInt()
            StepsData(
                timestamp = sample.startDate.toKotlinInstant(),
                count = steps,
                startTime = sample.startDate.toKotlinInstant(),
                endTime = sample.endDate.toKotlinInstant()
            )
        } as Flow<T>
        
        HealthDataType.Distance -> observeQuantityType(
            quantityTypeIdentifier = HKQuantityTypeIdentifierDistanceWalkingRunning,
            lookbackDuration = 5.minutes
        ) { sample ->
            val distance = sample.quantity.doubleValueForUnit(HKUnit.meterUnit())
            DistanceData(
                timestamp = sample.startDate.toKotlinInstant(),
                distance = distance,
                unit = DistanceUnit.METERS,
                activityType = DistanceActivityType.UNKNOWN
            )
        } as Flow<T>
        
        HealthDataType.Weight -> observeQuantityType(
            quantityTypeIdentifier = HKQuantityTypeIdentifierBodyMass,
            lookbackDuration = 1.days
        ) { sample ->
            val weight = sample.quantity.doubleValueForUnit(HKUnit.gramUnit()) / 1000.0  // Convert to kg
            BodyMeasurements(
                weight = weight,
                weightUnit = WeightUnit.KILOGRAMS,
                timestamp = sample.startDate.toKotlinInstant()
            )
        } as Flow<T>
        
        HealthDataType.BloodGlucose -> observeQuantityType(
            quantityTypeIdentifier = HKQuantityTypeIdentifierBloodGlucose,
            lookbackDuration = 1.hours
        ) { sample ->
            val mgDL = sample.quantity.doubleValueForUnit(
                HKUnit.gramUnit().unitDividedByUnit(HKUnit.literUnit()).unitMultipliedByUnit(
                    HKUnit.unitFromString("1000000")
                )
            )
            GlucoseData(
                timestamp = sample.startDate.toKotlinInstant(),
                glucoseLevel = mgDL,
                source = createDataSource(sample),
                metadata = createSampleMetadata(sample)
            )
        } as Flow<T>
        
        HealthDataType.OxygenSaturation -> observeQuantityType(
            quantityTypeIdentifier = HKQuantityTypeIdentifierOxygenSaturation,
            lookbackDuration = 5.minutes
        ) { sample ->
            val percentage = sample.quantity.doubleValueForUnit(HKUnit.percentUnit()) * 100
            OxygenSaturationData(
                timestamp = sample.startDate.toKotlinInstant(),
                percentage = percentage,
                source = createDataSource(sample),
                metadata = createSampleMetadata(sample)
            )
        } as Flow<T>
        
        HealthDataType.RespiratoryRate -> observeQuantityType(
            quantityTypeIdentifier = HKQuantityTypeIdentifierRespiratoryRate,
            lookbackDuration = 5.minutes
        ) { sample ->
            val breathsPerMinute = sample.quantity.doubleValueForUnit(
                HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit())
            )
            RespiratoryRateData(
                timestamp = sample.startDate.toKotlinInstant(),
                breathsPerMinute = breathsPerMinute,
                source = createDataSource(sample),
                metadata = createSampleMetadata(sample)
            )
        } as Flow<T>
        
        HealthDataType.BodyTemperature -> observeQuantityType(
            quantityTypeIdentifier = HKQuantityTypeIdentifierBodyTemperature,
            lookbackDuration = 1.hours
        ) { sample ->
            val celsius = sample.quantity.doubleValueForUnit(HKUnit.degreeCelsiusUnit())
            BodyTemperatureData(
                timestamp = sample.startDate.toKotlinInstant(),
                temperature = celsius,
                unit = TemperatureUnit.CELSIUS,
                source = createDataSource(sample),
                metadata = createSampleMetadata(sample)
            )
        } as Flow<T>
        
        // Calories need special handling (combines active and basal)
        HealthDataType.Calories -> observeCalories() as Flow<T>
        
        // Category types
        HealthDataType.Sleep -> observeSleepAnalysis() as Flow<T>
        HealthDataType.Mindfulness -> observeMindfulnessSessions() as Flow<T>
        
        // Heart Rate Variability
        HealthDataType.HeartRateVariability -> observeQuantityType(
            quantityTypeIdentifier = HKQuantityTypeIdentifierHeartRateVariabilitySDNN,
            lookbackDuration = 1.minutes
        ) { sample ->
            HeartRateVariabilityData(
                timestamp = sample.startDate.toKotlinInstant(),
                sdnn = sample.quantity.doubleValueForUnit(HKUnit.secondUnitWithMetricPrefix(HKMetricPrefixMilli)),
                source = createDataSource(sample),
                metadata = createSampleMetadata(sample)
            )
        } as Flow<T>
        
        // Mobility metrics (iOS 14+)
        HealthDataType.WalkingAsymmetry -> if (isIOS14OrLater()) {
            observeWalkingAsymmetry() as Flow<T>
        } else {
            callbackFlow { awaitClose { } }
        }
        
        HealthDataType.WalkingSpeed -> if (isIOS14OrLater()) {
            observeWalkingSpeed() as Flow<T>
        } else {
            callbackFlow { awaitClose { } }
        }
        
        // Audio exposure
        HealthDataType.EnvironmentalAudioExposure -> observeEnvironmentalAudioExposure() as Flow<T>
        HealthDataType.HeadphoneAudioExposure -> observeHeadphoneAudioExposure() as Flow<T>
        
        // Advanced workout metrics (iOS 16+)
        HealthDataType.RunningStrideLength -> if (isIOS16OrLater()) {
            observeRunningStrideLength() as Flow<T>
        } else {
            callbackFlow { awaitClose { } }
        }
        
        HealthDataType.CyclingPower -> if (isIOS17OrLater()) {
            observeCyclingPower() as Flow<T>
        } else {
            callbackFlow { awaitClose { } }
        }
        
        // Blood Pressure
        HealthDataType.BloodPressure -> observeBloodPressure() as Flow<T>
        
        // Nutrition types
        HealthDataType.Water -> observeWater() as Flow<T>
        HealthDataType.Protein -> observeProtein() as Flow<T>
        HealthDataType.Carbohydrates -> observeCarbohydrates() as Flow<T>
        HealthDataType.Fat -> observeFat() as Flow<T>
        HealthDataType.Fiber -> observeFiber() as Flow<T>
        HealthDataType.Sugar -> observeSugar() as Flow<T>
        HealthDataType.Caffeine -> observeCaffeine() as Flow<T>
        
        // Resting heart rate
        HealthDataType.RestingHeartRate -> observeRestingHeartRate() as Flow<T>
        
        // Body measurements
        HealthDataType.BodyFat -> observeBodyFat() as Flow<T>
        HealthDataType.BMI -> observeBMI() as Flow<T>
        HealthDataType.LeanBodyMass -> observeLeanBodyMass() as Flow<T>
        
        // Unsupported or not yet implemented
        else -> throw NotImplementedError("Observation of $dataType is not yet implemented")
    }
    
    // Sleep observer helper
    private fun observeSleepAnalysis(): Flow<SleepData> = 
        observeCategoryType(
            categoryTypeIdentifier = HKCategoryTypeIdentifierSleepAnalysis,
            lookbackDuration = 1.days
        ) { categorySample ->
            val sleepStageType = when (categorySample.value) {
                HKCategoryValueSleepAnalysisInBed -> SleepStageType.AWAKE
                HKCategoryValueSleepAnalysisAsleep -> SleepStageType.UNKNOWN
                HKCategoryValueSleepAnalysisAsleepCore -> SleepStageType.LIGHT
                HKCategoryValueSleepAnalysisAsleepDeep -> SleepStageType.DEEP
                HKCategoryValueSleepAnalysisAsleepREM -> SleepStageType.REM
                HKCategoryValueSleepAnalysisAwake -> SleepStageType.AWAKE
                else -> SleepStageType.UNKNOWN
            }
            SleepData(
                timestamp = categorySample.startDate.toKotlinInstant(),
                startTime = categorySample.startDate.toKotlinInstant(),
                endTime = categorySample.endDate.toKotlinInstant(),
                duration = categorySample.endDate.timeIntervalSinceDate(categorySample.startDate)
                    .toDuration(kotlin.time.DurationUnit.SECONDS),
                stages = listOf(SleepStage(
                    stage = sleepStageType,
                    startTime = categorySample.startDate.toKotlinInstant(),
                    endTime = categorySample.endDate.toKotlinInstant(),
                    duration = categorySample.endDate.timeIntervalSinceDate(categorySample.startDate)
                        .toDuration(kotlin.time.DurationUnit.SECONDS)
                )),
                source = createDataSource(categorySample),
                metadata = createSampleMetadata(categorySample)
            )
        }
    
    // Implementation of observe calories using statistics queries
    private fun observeCalories(): Flow<CalorieData> = callbackFlow {
        val activeType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned)
        val basalType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBasalEnergyBurned)
        
        if (activeType == null || basalType == null) return@callbackFlow
        
        fun fetchLatestCalories() {
            val now = NSDate()
            val fiveMinutesAgo = now.dateByAddingTimeInterval(-300.0)
            val predicate = HKQuery.predicateForSamplesWithStartDate(fiveMinutesAgo, endDate = now, options = HKQueryOptionNone)
            
            var activeCalories = 0.0
            var basalCalories = 0.0
            var queriesCompleted = 0
            
            fun checkCompletion() {
                queriesCompleted++
                if (queriesCompleted == 2 && (activeCalories > 0 || basalCalories > 0)) {
                    trySend(CalorieData(
                        timestamp = Clock.System.now(),
                        activeCalories = activeCalories,
                        basalCalories = basalCalories
                    ))
                }
            }
            
            val activeQuery = HKStatisticsQuery(
                quantityType = activeType,
                quantitySamplePredicate = predicate,
                options = HKStatisticsOptionCumulativeSum
            ) { _, statistics, _ ->
                activeCalories = statistics?.sumQuantity()?.doubleValueForUnit(HKUnit.kilocalorieUnit()) ?: 0.0
                checkCompletion()
            }
            
            val basalQuery = HKStatisticsQuery(
                quantityType = basalType,
                quantitySamplePredicate = predicate,
                options = HKStatisticsOptionCumulativeSum
            ) { _, statistics, _ ->
                basalCalories = statistics?.sumQuantity()?.doubleValueForUnit(HKUnit.kilocalorieUnit()) ?: 0.0
                checkCompletion()
            }
            
            healthStore.executeQuery(activeQuery)
            healthStore.executeQuery(basalQuery)
        }
        
        // Create observer queries for real-time updates
        val activeObserver = HKObserverQuery(
            sampleType = activeType,
            predicate = null
        ) { query, completionHandler, error ->
            if (error == null) {
                fetchLatestCalories()
            }
            completionHandler?.let { it() }
        }
        
        val basalObserver = HKObserverQuery(
            sampleType = basalType,
            predicate = null
        ) { _, completionHandler, error ->
            if (error == null) {
                fetchLatestCalories()
            }
            completionHandler?.let { it() }
        }
        
        healthStore.executeQuery(activeObserver)
        healthStore.executeQuery(basalObserver)
        
        awaitClose {
            healthStore.stopQuery(activeObserver)
            healthStore.stopQuery(basalObserver)
        }
    }
    
    // Helper observe methods for specific data types (can be made public if needed)
    
    
    // Private observe methods for mobility data (used internally by the generic observe method)
    private fun observeWalkingAsymmetry(): Flow<WalkingAsymmetryData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierWalkingAsymmetryPercentage,
        lookbackDuration = 5.minutes
    ) { sample ->
        sample.toWalkingAsymmetryData()
    }
    
    private fun observeWalkingSpeed(): Flow<WalkingSpeedData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierWalkingSpeed,
        lookbackDuration = 5.minutes
    ) { sample ->
        sample.toWalkingSpeedData()
    }
    
    // Audio exposure observers
    private fun observeEnvironmentalAudioExposure(): Flow<EnvironmentalAudioExposureData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierEnvironmentalAudioExposure,
        lookbackDuration = 1.hours
    ) { sample ->
        sample.toEnvironmentalAudioExposureData()
    }
    
    private fun observeHeadphoneAudioExposure(): Flow<HeadphoneAudioExposureData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierHeadphoneAudioExposure,
        lookbackDuration = 1.hours
    ) { sample ->
        HeadphoneAudioExposureData(
            timestamp = sample.startDate.toKotlinInstant(),
            level = sample.quantity.doubleValueForUnit(HKUnit.decibelAWeightedSoundPressureLevelUnit()),
            duration = sample.endDate.timeIntervalSinceDate(sample.startDate).toDuration(kotlin.time.DurationUnit.SECONDS),
            startTime = sample.startDate.toKotlinInstant(),
            endTime = sample.endDate.toKotlinInstant(),
            source = createDataSource(sample),
            metadata = createSampleMetadata(sample)
        )
    }
    
    // Advanced workout metric observers - iOS 16+ only
    private fun observeRunningStrideLength(): Flow<RunningStrideLengthData> = if (isIOS16OrLater()) {
        observeQuantityType(
            quantityTypeIdentifier = HKQuantityTypeIdentifierRunningStrideLength,
            lookbackDuration = 5.minutes
        ) { sample ->
            sample.toRunningStrideLengthData()
        }
    } else {
        callbackFlow { 
            // Return empty flow for unsupported iOS versions
            awaitClose { }
        }
    }
    
    private fun observeCyclingPower(): Flow<PowerData> = if (isIOS17OrLater()) {
        observeQuantityType(
            quantityTypeIdentifier = HKQuantityTypeIdentifierCyclingPower,
            lookbackDuration = 5.minutes
        ) { sample ->
            sample.toPowerData()
        }
    } else {
        callbackFlow { 
            // Return empty flow for unsupported iOS versions
            awaitClose { }
        }
    }
    
    // Mindfulness session observer
    private fun observeMindfulnessSessions(): Flow<MindfulnessSessionData> = 
        observeCategoryType(
            categoryTypeIdentifier = HKCategoryTypeIdentifierMindfulSession,
            lookbackDuration = 1.hours
        ) { categorySample ->
            categorySample.toMindfulnessSessionData()
        }
    
    // Blood Pressure observer
    private fun observeBloodPressure(): Flow<BloodPressureData> = callbackFlow {
        val systolicType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBloodPressureSystolic)
            ?: return@callbackFlow
        val diastolicType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBloodPressureDiastolic)
            ?: return@callbackFlow
        
        val correlationType = HKCorrelationType.correlationTypeForIdentifier(HKCorrelationTypeIdentifierBloodPressure)
            ?: return@callbackFlow
        
        val observerQuery = HKObserverQuery(
            sampleType = correlationType,
            predicate = null
        ) { _, completionHandler, error ->
            if (error == null) {
                val now = NSDate()
                val startTime = now.dateByAddingTimeInterval(-1.hours.inWholeSeconds.toDouble())
                val predicate = HKQuery.predicateForSamplesWithStartDate(
                    startTime,
                    endDate = now,
                    options = HKQueryOptionNone
                )
                
                val sampleQuery = HKSampleQuery(
                    correlationType,
                    predicate,
                    HKObjectQueryNoLimit,
                    listOf(NSSortDescriptor.sortDescriptorWithKey("startDate", ascending = true))
                ) { _, samples, _ ->
                    samples?.forEach { sample ->
                        (sample as? HKCorrelation)?.let { correlation ->
                            val systolicSamples = correlation.objectsForType(systolicType)
                            val diastolicSamples = correlation.objectsForType(diastolicType)
                            
                            val systolic = (systolicSamples?.firstOrNull() as? HKQuantitySample)?.quantity?.doubleValueForUnit(
                                HKUnit.millimeterOfMercuryUnit()
                            )
                            val diastolic = (diastolicSamples?.firstOrNull() as? HKQuantitySample)?.quantity?.doubleValueForUnit(
                                HKUnit.millimeterOfMercuryUnit()
                            )
                            
                            if (systolic != null && diastolic != null) {
                                trySend(BloodPressureData(
                                    timestamp = correlation.startDate.toKotlinInstant(),
                                    systolic = systolic.toInt(),
                                    diastolic = diastolic.toInt()
                                ))
                            }
                        }
                    }
                }
                
                healthStore.executeQuery(sampleQuery)
            }
            completionHandler?.invoke()
        }
        
        healthStore.executeQuery(observerQuery)
        
        awaitClose {
            healthStore.stopQuery(observerQuery)
        }
    }
    
    // Nutrition observers
    private fun observeWater(): Flow<HydrationData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierDietaryWater,
        lookbackDuration = 1.hours
    ) { sample ->
        HydrationData(
            timestamp = sample.startDate.toKotlinInstant(),
            volume = sample.quantity.doubleValueForUnit(HKUnit.literUnit())
        )
    }
    
    private fun observeProtein(): Flow<NutritionData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierDietaryProtein,
        lookbackDuration = 1.hours
    ) { sample ->
        NutritionData(
            timestamp = sample.startDate.toKotlinInstant(),
            protein = sample.quantity.doubleValueForUnit(HKUnit.gramUnit()),
            source = createDataSource(sample),
            metadata = createSampleMetadata(sample)
        )
    }
    
    private fun observeCarbohydrates(): Flow<NutritionData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierDietaryCarbohydrates,
        lookbackDuration = 1.hours
    ) { sample ->
        NutritionData(
            timestamp = sample.startDate.toKotlinInstant(),
            carbohydrates = sample.quantity.doubleValueForUnit(HKUnit.gramUnit()),
            source = createDataSource(sample),
            metadata = createSampleMetadata(sample)
        )
    }
    
    private fun observeFat(): Flow<NutritionData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierDietaryFatTotal,
        lookbackDuration = 1.hours
    ) { sample ->
        NutritionData(
            timestamp = sample.startDate.toKotlinInstant(),
            fat = sample.quantity.doubleValueForUnit(HKUnit.gramUnit()),
            source = createDataSource(sample),
            metadata = createSampleMetadata(sample)
        )
    }
    
    private fun observeFiber(): Flow<NutritionData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierDietaryFiber,
        lookbackDuration = 1.hours
    ) { sample ->
        NutritionData(
            timestamp = sample.startDate.toKotlinInstant(),
            fiber = sample.quantity.doubleValueForUnit(HKUnit.gramUnit()),
            source = createDataSource(sample),
            metadata = createSampleMetadata(sample)
        )
    }
    
    private fun observeSugar(): Flow<NutritionData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierDietarySugar,
        lookbackDuration = 1.hours
    ) { sample ->
        NutritionData(
            timestamp = sample.startDate.toKotlinInstant(),
            sugar = sample.quantity.doubleValueForUnit(HKUnit.gramUnit()),
            source = createDataSource(sample),
            metadata = createSampleMetadata(sample)
        )
    }
    
    private fun observeCaffeine(): Flow<NutritionData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierDietaryCaffeine,
        lookbackDuration = 1.hours
    ) { sample ->
        NutritionData(
            timestamp = sample.startDate.toKotlinInstant(),
            caffeine = sample.quantity.doubleValueForUnit(HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixMilli)),
            source = createDataSource(sample),
            metadata = createSampleMetadata(sample)
        )
    }
    
    // Resting heart rate observer
    private fun observeRestingHeartRate(): Flow<RestingHeartRateData> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierRestingHeartRate,
        lookbackDuration = 24.hours
    ) { sample ->
        RestingHeartRateData(
            timestamp = sample.startDate.toKotlinInstant(),
            bpm = sample.quantity.doubleValueForUnit(HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit())).toInt(),
            source = createDataSource(sample),
            metadata = createSampleMetadata(sample)
        )
    }
    
    // Body measurements observers
    private fun observeBodyFat(): Flow<BodyMeasurements> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierBodyFatPercentage,
        lookbackDuration = 24.hours
    ) { sample ->
        BodyMeasurements(
            bodyFatPercentage = sample.quantity.doubleValueForUnit(HKUnit.percentUnit()) * 100,
            timestamp = sample.startDate.toKotlinInstant()
        )
    }
    
    private fun observeBMI(): Flow<BodyMeasurements> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierBodyMassIndex,
        lookbackDuration = 24.hours
    ) { sample ->
        BodyMeasurements(
            bmi = sample.quantity.doubleValueForUnit(HKUnit.countUnit()),
            timestamp = sample.startDate.toKotlinInstant()
        )
    }
    
    private fun observeLeanBodyMass(): Flow<BodyMeasurements> = observeQuantityType(
        quantityTypeIdentifier = HKQuantityTypeIdentifierLeanBodyMass,
        lookbackDuration = 24.hours
    ) { sample ->
        BodyMeasurements(
            leanBodyMass = sample.quantity.doubleValueForUnit(HKUnit.gramUnit()) / 1000.0,
            timestamp = sample.startDate.toKotlinInstant()
        )
    }
    
    override fun observeActiveWorkout(): Flow<WorkoutData> = callbackFlow {
        // Check if there's an active workout session
        val activeSession = workoutSessions.values.firstOrNull { it.isActive }
        
        if (activeSession != null) {
            // If we have an active session, observe its live data
            activeSession.observeLiveData()
                .collect { workoutData ->
                    trySend(workoutData)
                }
        } else {
            // No active workout session - return empty flow
            // In a real implementation, we might want to monitor for new sessions
        }
        
        awaitClose {
            // Cleanup if needed
        }
    }
    
    
    override suspend fun startWorkoutSession(
        workoutType: WorkoutType
    ): Result<WorkoutSession> = suspendCoroutine { continuation ->
        try {
            val sessionId = NSUUID().UUIDString
            val session = HealthKitWorkoutSession(
                id = sessionId,
                type = workoutType,
                startTime = Clock.System.now(),
                healthStore = healthStore,
                configuration = null
            )
            
            // Start the session
            session.start()
            
            // Store the session
            workoutSessions[sessionId] = session
            
            continuation.resume(Result.success(session))
        } catch (e: Exception) {
            val exception = HealthKitException.WorkoutSessionException(
                    sessionId = null,
                    details = "Failed to start workout session: ${e.message}",
                    errorCode = null,
                    errorDomain = null
            )
            continuation.resume(Result.failure(exception))
        }
    }
    
    override suspend fun startWorkoutSession(
        configuration: WorkoutConfiguration
    ): Result<WorkoutSession> = suspendCoroutine { continuation ->
        try {
            val sessionId = NSUUID().UUIDString
            val session = HealthKitWorkoutSession(
                id = sessionId,
                type = configuration.type,
                startTime = Clock.System.now(),
                healthStore = healthStore,
                configuration = configuration
            )
            
            // Start the session
            session.start()
            
            // Store the session
            workoutSessions[sessionId] = session
            
            continuation.resume(Result.success(session))
        } catch (e: Exception) {
            val exception = HealthKitException.WorkoutSessionException(
                sessionId = null,
                details = "Failed to start workout session: ${e.message}",
                errorCode = null,
                errorDomain = null
            )
            continuation.resume(Result.failure(exception))
        }
    }
    
    override suspend fun pauseWorkoutSession(sessionId: String): Result<Unit> {
        return workoutSessions[sessionId]?.pause()
            ?: Result.failure(HealthConnectorException.WorkoutSessionError("Session not found"))
    }
    
    override suspend fun resumeWorkoutSession(sessionId: String): Result<Unit> {
        return workoutSessions[sessionId]?.resume()
            ?: Result.failure(HealthConnectorException.WorkoutSessionError("Session not found"))
    }
    
    override suspend fun endWorkoutSession(sessionId: String): Result<Unit> {
        return workoutSessions[sessionId]?.let { session ->
            val result = session.end()
            workoutSessions.remove(sessionId)
            result
        } ?: Result.failure(HealthConnectorException.WorkoutSessionError("Session not found"))
    }
    
    override suspend fun discardWorkoutSession(sessionId: String): Result<Unit> {
        workoutSessions.remove(sessionId)
        return Result.success(Unit)
    }
    
    override suspend fun writeWeight(
        weight: Double,
        unit: WeightUnit,
        timestamp: Instant
    ): Result<Unit> = suspendCoroutine { continuation ->
        val weightType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMass)
            ?: return@suspendCoroutine continuation.resume(Result.failure(
                HealthConnectorException.DataNotAvailable(HealthDataType.Weight)
            ))
        
        val weightInKg = when (unit) {
            WeightUnit.KILOGRAMS -> weight
            WeightUnit.POUNDS -> weight * 0.453592
            WeightUnit.STONES -> weight * 6.35029
        }
        
        val quantity = HKQuantity.quantityWithUnit(
            HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixKilo),
            doubleValue = weightInKg
        )
        
        val sample = HKQuantitySample.quantitySampleWithType(
            quantityType = weightType,
            quantity = quantity,
            startDate = timestamp.toNSDate(),
            endDate = timestamp.toNSDate()
        )
        
        healthStore.saveObject(sample) { success, error ->
            if (success) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(
                    Exception(error?.localizedDescription ?: "Failed to save weight")
                ))
            }
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    override suspend fun writeWorkout(workoutData: WorkoutData): Result<Unit> = suspendCoroutine { continuation ->
        val workoutActivityType = workoutData.type.toHKWorkoutActivityType()
        
        // Calculate total energy burned
        val totalEnergyBurned = workoutData.totalCalories?.let { calories ->
            HKQuantity.quantityWithUnit(
                HKUnit.kilocalorieUnit(),
                doubleValue = calories
            )
        }
        
        // Calculate total distance
        val totalDistance = workoutData.totalDistance?.let { distance ->
            HKQuantity.quantityWithUnit(
                HKUnit.meterUnit(),
                doubleValue = distance
            )
        }
        
        // Create metadata dictionary
        val metadataDict = NSMutableDictionary()
        workoutData.metadata.forEach { (key, value) ->
            metadataDict.setObject(value.toString() as NSString, forKey = key as NSString)
        }
        
        // Add indoor/outdoor information if available
        workoutData.isIndoor?.let { isIndoor ->
            val locationType = if (isIndoor) {
                HKWorkoutSessionLocationTypeIndoor
            } else {
                HKWorkoutSessionLocationTypeOutdoor
            }
            metadataDict.setObject(locationType, forKey = HKMetadataKeyIndoorWorkout as NSString)
        }
        
        // Create the workout
        val endTime = workoutData.endTime ?: workoutData.timestamp
        val duration = workoutData.duration?.inWholeSeconds?.toDouble() 
            ?: (endTime.epochSeconds - workoutData.startTime.epochSeconds).toDouble()
        
        val workout = HKWorkout.workoutWithActivityType(
            workoutActivityType = workoutActivityType,
            startDate = workoutData.startTime.toNSDate(),
            endDate = endTime.toNSDate(),
            duration = duration,
            totalEnergyBurned = totalEnergyBurned,
            totalDistance = totalDistance,
            metadata = if (metadataDict.count > 0u) {
                metadataDict as Map<Any?, *>
            } else {
                null
            }
        )
        
        // Save the workout
        healthStore.saveObject(workout) { success, error ->
            if (success) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(
                    Exception(error?.localizedDescription ?: "Failed to save workout")
                ))
            }
        }
    }
    
    // Workout route support
    suspend fun addWorkoutRoute(
        workout: HKWorkout,
        routeData: List<LocationData>
    ): Result<Unit> = suspendCoroutine { continuation ->
        if (routeData.isEmpty()) {
            continuation.resume(Result.success(Unit))
            return@suspendCoroutine
        }
        
        val routeBuilder = HKWorkoutRouteBuilder(healthStore = healthStore, device = null)
        
        // Convert location data to CLLocation objects and add to route
        routeData.forEach { locationData ->
            val coordinate = CLLocationCoordinate2DMake(
                locationData.latitude,
                locationData.longitude
            )
            val location = CLLocation(
                coordinate = coordinate,
                altitude = locationData.altitude ?: 0.0,
                horizontalAccuracy = locationData.accuracy ?: 5.0,
                verticalAccuracy = locationData.altitudeAccuracy ?: 5.0,
                timestamp = locationData.timestamp.toNSDate()
            )
            
            routeBuilder.insertRouteData(listOf(location)) { success, error ->
                if (!success) {
                    println("Failed to add location to route: ${error?.localizedDescription}")
                }
            }
        }
        
        // Finish the route and associate with workout
        routeBuilder.finishRouteWithWorkout(workout, metadata = null) { route, error ->
            if (route != null) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(
                    Exception(error?.localizedDescription ?: "Failed to save workout route")
                ))
            }
        }
    }
    
    
    // Enhanced metadata handling using typed models
    fun createWorkoutMetadata(
        workoutData: WorkoutData,
        workoutMetadata: WorkoutMetadata? = null,
        additionalMetadata: Map<String, Any> = emptyMap()
    ): NSMutableDictionary {
        val metadata = NSMutableDictionary()
        
        // Add title and notes
        val title = workoutMetadata?.title ?: workoutData.title
        title?.let { 
            metadata.setValue(it, forKey = "WorkoutTitle")
        }
        
        workoutMetadata?.notes?.let {
            metadata.setValue(it, forKey = "WorkoutNotes")
        }
        
        // Add indoor/outdoor info
        val isIndoor = workoutMetadata?.isIndoor ?: workoutData.isIndoor
        isIndoor?.let { indoor ->
            metadata.setValue(
                if (indoor) HKWorkoutSessionLocationTypeIndoor else HKWorkoutSessionLocationTypeOutdoor,
                forKey = HKMetadataKeyIndoorWorkout
            )
        }
        
        workoutData.elevationGained?.let {
            metadata.setValue(
                HKQuantity.quantityWithUnit(HKUnit.meterUnit(), doubleValue = it),
                forKey = HKMetadataKeyElevationAscended
            )
        }
        workoutData.elevationLost?.let {
            metadata.setValue(
                HKQuantity.quantityWithUnit(HKUnit.meterUnit(), doubleValue = it),
                forKey = HKMetadataKeyElevationDescended
            )
        }
        
        workoutMetadata?.deviceName?.let {
            metadata.setValue(it, forKey = HKMetadataKeyDeviceName)
        }
        workoutMetadata?.deviceManufacturer?.let {
            metadata.setValue(it, forKey = HKMetadataKeyDeviceManufacturerName)
        }
        
        workoutMetadata?.customData?.forEach { (key, value) ->
            when (value) {
                is String -> metadata.setValue(value, forKey = key)
                is Number -> metadata.setValue(value, forKey = key)
                is Boolean -> metadata.setValue(value, forKey = key)
                else -> metadata.setValue(value.toString(), forKey = key)
            }
        }
        
        // Add any additional metadata
        additionalMetadata.forEach { (key, value) ->
            metadata.setValue(value.toString(), forKey = key)
        }
        
        return metadata
    }
    
    override suspend fun writeHealthData(dataPoint: HealthDataPoint): Result<Unit> = suspendCoroutine { continuation ->
        val sample = when (dataPoint) {
            is HeartRateData -> {
                val quantityType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRate)
                    ?: return@suspendCoroutine continuation.resume(Result.failure(
                        HealthConnectorException.DataNotAvailable(HealthDataType.HeartRate)
                    ))
                
                val quantity = HKQuantity.quantityWithUnit(
                    HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit()),
                    doubleValue = dataPoint.bpm.toDouble()
                )
                
                HKQuantitySample.quantitySampleWithType(
                    quantityType = quantityType,
                    quantity = quantity,
                    startDate = dataPoint.timestamp.toNSDate(),
                    endDate = dataPoint.timestamp.toNSDate(),
                    metadata = dataPoint.metadata.mapValues { it.value.toString() }
                )
            }
            is StepsData -> {
                val quantityType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount)
                    ?: return@suspendCoroutine continuation.resume(Result.failure(
                        HealthKitException.DataUnavailableException(
                    dataType = HealthDataType.Steps
                )))
                
                val quantity = HKQuantity.quantityWithUnit(
                    HKUnit.countUnit(),
                    doubleValue = dataPoint.count.toDouble()
                )
                
                HKQuantitySample.quantitySampleWithType(
                    quantityType = quantityType,
                    quantity = quantity,
                    startDate = dataPoint.startTime?.toNSDate() ?: dataPoint.timestamp.toNSDate(),
                    endDate = dataPoint.endTime?.toNSDate() ?: dataPoint.timestamp.toNSDate(),
                    metadata = dataPoint.metadata.mapValues { it.value.toString() }
                )
            }
            is DistanceData -> {
                val distanceType = when (dataPoint.activityType) {
                    DistanceActivityType.CYCLING -> HKQuantityTypeIdentifierDistanceCycling
                    DistanceActivityType.SWIMMING -> HKQuantityTypeIdentifierDistanceSwimming
                    DistanceActivityType.WHEELCHAIR -> HKQuantityTypeIdentifierDistanceWheelchair
                    else -> HKQuantityTypeIdentifierDistanceWalkingRunning
                }
                
                val quantityType = HKQuantityType.quantityTypeForIdentifier(distanceType)
                    ?: return@suspendCoroutine continuation.resume(Result.failure(
                        HealthConnectorException.DataNotAvailable(HealthDataType.Distance)
                    ))
                
                val distanceInMeters = when (dataPoint.unit) {
                    DistanceUnit.METERS -> dataPoint.distance
                    DistanceUnit.KILOMETERS -> dataPoint.distance * 1000
                    DistanceUnit.MILES -> dataPoint.distance * 1609.34
                    DistanceUnit.FEET -> dataPoint.distance * 0.3048
                }
                
                val quantity = HKQuantity.quantityWithUnit(
                    HKUnit.meterUnit(),
                    doubleValue = distanceInMeters
                )
                
                HKQuantitySample.quantitySampleWithType(
                    quantityType = quantityType,
                    quantity = quantity,
                    startDate = dataPoint.timestamp.toNSDate(),
                    endDate = dataPoint.timestamp.toNSDate(),
                    metadata = dataPoint.metadata.mapValues { it.value.toString() }
                )
            }
            is CalorieData -> {
                val quantityType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned)
                    ?: return@suspendCoroutine continuation.resume(Result.failure(
                        HealthConnectorException.DataNotAvailable(HealthDataType.ActiveCalories)
                    ))
                
                val quantity = HKQuantity.quantityWithUnit(
                    HKUnit.kilocalorieUnit(),
                    doubleValue = dataPoint.activeCalories
                )
                
                HKQuantitySample.quantitySampleWithType(
                    quantityType = quantityType,
                    quantity = quantity,
                    startDate = dataPoint.timestamp.toNSDate(),
                    endDate = dataPoint.timestamp.toNSDate(),
                    metadata = dataPoint.metadata.mapValues { it.value.toString() }
                )
            }
            is BodyMeasurements -> {
                if (dataPoint.weight == null) {
                    return@suspendCoroutine continuation.resume(Result.failure(
                        Exception("Weight value is required")
                    ))
                }
                
                val quantityType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMass)
                    ?: return@suspendCoroutine continuation.resume(Result.failure(
                        HealthConnectorException.DataNotAvailable(HealthDataType.Weight)
                    ))
                
                val weightInKg = when (dataPoint.weightUnit) {
                    WeightUnit.KILOGRAMS -> dataPoint.weight
                    WeightUnit.POUNDS -> dataPoint.weight * 0.453592
                    WeightUnit.STONES -> dataPoint.weight * 6.35029
                    null -> dataPoint.weight // Assume kg if not specified
                }
                
                val quantity = HKQuantity.quantityWithUnit(
                    HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixKilo),
                    doubleValue = weightInKg
                )
                
                HKQuantitySample.quantitySampleWithType(
                    quantityType = quantityType,
                    quantity = quantity,
                    startDate = dataPoint.timestamp.toNSDate(),
                    endDate = dataPoint.timestamp.toNSDate(),
                    metadata = dataPoint.metadata.mapValues { it.value.toString() }
                )
            }
            is BloodPressureData -> {
                val systolicType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBloodPressureSystolic)
                val diastolicType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBloodPressureDiastolic)
                
                if (systolicType == null || diastolicType == null) {
                    return@suspendCoroutine continuation.resume(Result.failure(
                        HealthConnectorException.DataNotAvailable(HealthDataType.BloodPressure)
                    ))
                }
                
                val systolicQuantity = HKQuantity.quantityWithUnit(
                    HKUnit.millimeterOfMercuryUnit(),
                    doubleValue = dataPoint.systolic.toDouble()
                )
                
                val diastolicQuantity = HKQuantity.quantityWithUnit(
                    HKUnit.millimeterOfMercuryUnit(),
                    doubleValue = dataPoint.diastolic.toDouble()
                )
                
                val systolicSample = HKQuantitySample.quantitySampleWithType(
                    quantityType = systolicType,
                    quantity = systolicQuantity,
                    startDate = dataPoint.timestamp.toNSDate(),
                    endDate = dataPoint.timestamp.toNSDate()
                )
                
                val diastolicSample = HKQuantitySample.quantitySampleWithType(
                    quantityType = diastolicType,
                    quantity = diastolicQuantity,
                    startDate = dataPoint.timestamp.toNSDate(),
                    endDate = dataPoint.timestamp.toNSDate()
                )
                
                val correlationType = HKCorrelationType.correlationTypeForIdentifier(HKCorrelationTypeIdentifierBloodPressure)
                    ?: return@suspendCoroutine continuation.resume(Result.failure(
                        HealthConnectorException.DataNotAvailable(HealthDataType.BloodPressure)
                    ))
                
                HKCorrelation.correlationWithType(
                    correlationType = correlationType,
                    startDate = dataPoint.timestamp.toNSDate(),
                    endDate = dataPoint.timestamp.toNSDate(),
                    objects = setOf(systolicSample, diastolicSample),
                    metadata = dataPoint.metadata.mapValues { it.value.toString() }
                )
            }
            else -> null
        }
        
        if (sample == null) {
            continuation.resume(Result.failure(
                HealthConnectorException.PlatformNotSupported("Data type not supported for writing")
            ))
            return@suspendCoroutine
        }
        
        healthStore.saveObject(sample) { success, error ->
            if (success) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(
                    Exception(error?.localizedDescription ?: "Failed to save health data")
                ))
            }
        }
    }
    
    /**
     * Write workout data with native support for segments and routes
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun writeWorkoutWithSegments(
        workoutData: WorkoutData
    ): Result<Unit> = suspendCoroutine { continuation ->
        val workoutActivityType = workoutData.type.toHKWorkoutActivityType()
        
        // Build metadata
        val metadata = mutableMapOf<String, Any>()
        
        // Add elevation data
        workoutData.elevationGained?.let { metadata[HKMetadataKeyElevationAscended] = it }
        workoutData.elevationLost?.let { metadata[HKMetadataKeyElevationDescended] = it }
        
        // Add segment info if multi-sport
        workoutData.segments?.let { segments ->
            val isMultiSport = segments.size > 1 && segments.map { it.type }.distinct().size > 1
            if (isMultiSport) {
                metadata["isMultiSport"] = true
                metadata["segmentCount"] = segments.size
            }
        }
        
        // Add all custom metadata
        metadata.putAll(workoutData.metadata)
        
        // Create the main workout
        val totalEnergyBurned = workoutData.totalCalories?.let { calories ->
            HKQuantity.quantityWithUnit(HKUnit.kilocalorieUnit(), doubleValue = calories)
        }
        
        val totalDistance = workoutData.totalDistance?.let { distance ->
            HKQuantity.quantityWithUnit(HKUnit.meterUnit(), doubleValue = distance)
        }
        
        val workout = HKWorkout.workoutWithActivityType(
            workoutActivityType = workoutActivityType,
            startDate = workoutData.startTime.toNSDate(),
            endDate = workoutData.endTime?.toNSDate() ?: Clock.System.now().toNSDate(),
            duration = workoutData.duration?.inWholeSeconds?.toDouble() ?: 0.0,
            totalEnergyBurned = totalEnergyBurned,
            totalDistance = totalDistance,
            metadata = metadata as Map<Any?, *>
        )
        
        // Save the workout
        healthStore.saveObject(workout) { success, error ->
            if (success) {
                // Add workout route if available
                if (workoutData.routeData != null) {
                    scope.launch {
                        addWorkoutRoute(workout, workoutData.routeData)
                    }
                }
                
                // Add workout segments as events if available
                workoutData.segments?.forEach { segment ->
                    val segmentEvent = HKWorkoutEvent.workoutEventWithType(
                        HKWorkoutEventTypeSegment,
                        date = segment.startTime.toNSDate(),
                        metadata = mapOf(
                            "segmentType" to segment.type.name,
                            "segmentName" to (segment.name ?: "Segment"),
                            "duration" to segment.duration.inWholeSeconds
                        ) as Map<Any?, *>
                    )
                    
                    // with the workout using HKWorkoutBuilder
                }
                
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(
                    Exception(error?.localizedDescription ?: "Failed to save workout with segments")
                ))
            }
        }
    }
    
    // MARK: - Clinical Records (FHIR)
    
    private val clinicalRecordReader = ClinicalRecordReader()
    
    override suspend fun readImmunizations(
        startDate: Instant?,
        endDate: Instant?
    ): Result<List<FHIRImmunization>> = suspendCoroutine { continuation ->
        clinicalRecordReader.readImmunizationsWithStartDate(
            startDate = startDate?.toNSDate(),
            endDate = endDate?.toNSDate()
        ) { fhirJsonStrings, error ->
            if (error != null) {
                continuation.resume(Result.failure(
                    HealthConnectorException.DataAccessError("Failed to read immunizations: $error")
                ))
            } else {
                val immunizations = fhirJsonStrings?.mapNotNull { json ->
                    (json as? String)?.let { FHIRParser.parseImmunization(it) }
                } ?: emptyList()
                continuation.resume(Result.success(immunizations))
            }
        }
    }
    
    override suspend fun readMedications(
        startDate: Instant?,
        endDate: Instant?
    ): Result<List<FHIRResource>> = suspendCoroutine { continuation ->
        clinicalRecordReader.readMedicationsWithStartDate(
            startDate = startDate?.toNSDate(),
            endDate = endDate?.toNSDate()
        ) { fhirJsonStrings, error ->
            if (error != null) {
                continuation.resume(Result.failure(
                    HealthConnectorException.DataAccessError("Failed to read medications: $error")
                ))
            } else {
                val medications = fhirJsonStrings?.mapNotNull { json ->
                    (json as? String)?.let { jsonString ->
                        // Try parsing as MedicationStatement first, then MedicationRequest
                        FHIRParser.parseMedicationStatement(jsonString) 
                            ?: FHIRParser.parseMedicationRequest(jsonString)
                    }
                } ?: emptyList()
                continuation.resume(Result.success(medications))
            }
        }
    }
    
    /**
     * Read ECG (Electrocardiogram) data
     */
    suspend fun readECGData(
        startTime: Instant? = null,
        endTime: Instant? = null,
        limit: Int = 100
    ): Result<List<ElectrocardiogramData>> = suspendCoroutine { continuation ->
        if (!isIOS14OrLater()) {
            continuation.resume(Result.failure(
                HealthKitException.DataUnavailableException(
                    dataType = HealthDataType.Electrocardiogram,
                    errorCode = null,
                    errorDomain = "HealthKitConnector"
                )
            ))
            return@suspendCoroutine
        }
        
        val ecgReader = ECGReader()
        
        // If no time range specified, read latest ECG
        if (startTime == null && endTime == null) {
            ecgReader.readLatestECGWithCompletion { ecgData, error ->
                if (error != null) {
                    continuation.resume(Result.failure(
                        HealthConnectorException.DataAccessError("Failed to read ECG: ${error.localizedDescription}")
                    ))
                } else if (ecgData != null) {
                    continuation.resume(Result.success(listOf(ecgData.toElectrocardiogramData())))
                } else {
                    continuation.resume(Result.success(emptyList()))
                }
            }
        } else {
            // For time-based queries, we'd need to implement a more complex query
            // Return the latest ECG if it falls within the time range
            ecgReader.readLatestECGWithCompletion { ecgData, error ->
                if (error != null) {
                    continuation.resume(Result.failure(
                        HealthConnectorException.DataAccessError("Failed to read ECG: ${error.localizedDescription}")
                    ))
                } else if (ecgData != null) {
                    val ecgInstant = ecgData.startDate().toKotlinInstant()
                    if ((startTime == null || ecgInstant >= startTime) && 
                        (endTime == null || ecgInstant <= endTime)) {
                        continuation.resume(Result.success(listOf(ecgData.toElectrocardiogramData())))
                    } else {
                        continuation.resume(Result.success(emptyList()))
                    }
                } else {
                    continuation.resume(Result.success(emptyList()))
                }
            }
        }
    }
    
    override suspend fun readAllergies(
        includeInactive: Boolean
    ): Result<List<FHIRAllergyIntolerance>> = suspendCoroutine { continuation ->
        clinicalRecordReader.readAllergiesWithIncludeInactive(
            includeInactive = includeInactive
        ) { fhirJsonStrings, error ->
            if (error != null) {
                continuation.resume(Result.failure(
                    HealthConnectorException.DataAccessError("Failed to read allergies: $error")
                ))
            } else {
                val allergies = fhirJsonStrings?.mapNotNull { json ->
                    (json as? String)?.let { FHIRParser.parseAllergyIntolerance(it) }
                } ?: emptyList()
                
                continuation.resume(Result.success(allergies))
            }
        }
    }
    
    override suspend fun readConditions(
        includeResolved: Boolean
    ): Result<List<FHIRCondition>> = suspendCoroutine { continuation ->
        clinicalRecordReader.readConditionsWithIncludeResolved(
            includeResolved = includeResolved
        ) { fhirJsonStrings, error ->
            if (error != null) {
                continuation.resume(Result.failure(
                    HealthConnectorException.DataAccessError("Failed to read conditions: $error")
                ))
            } else {
                var conditions = fhirJsonStrings?.mapNotNull { json ->
                    (json as? String)?.let { FHIRParser.parseCondition(it) }
                } ?: emptyList()
                
                // Filter out resolved conditions if requested
                if (!includeResolved) {
                    conditions = conditions.filter { !it.isResolved() }
                }
                
                continuation.resume(Result.success(conditions))
            }
        }
    }
    
    override suspend fun readLabResults(
        startDate: Instant?,
        endDate: Instant?,
        category: String?
    ): Result<List<FHIRObservation>> = suspendCoroutine { continuation ->
        clinicalRecordReader.readLabResultsWithStartDate(
            startDate = startDate?.toNSDate(),
            endDate = endDate?.toNSDate()
        ) { fhirJsonStrings, error ->
            if (error != null) {
                continuation.resume(Result.failure(
                    HealthConnectorException.DataAccessError("Failed to read lab results: $error")
                ))
            } else {
                var observations = fhirJsonStrings?.mapNotNull { json ->
                    (json as? String)?.let { FHIRParser.parseObservation(it) }
                } ?: emptyList()
                
                // Filter by category if provided
                if (category != null) {
                    observations = observations.filter { obs ->
                        obs.category?.any { cat ->
                            cat.coding?.any { it.code == category } ?: false
                        } ?: false
                    }
                }
                
                continuation.resume(Result.success(observations))
            }
        }
    }
    
    override suspend fun readProcedures(
        startDate: Instant?,
        endDate: Instant?
    ): Result<List<FHIRProcedure>> = suspendCoroutine { continuation ->
        clinicalRecordReader.readProceduresWithStartDate(
            startDate = startDate?.toNSDate(),
            endDate = endDate?.toNSDate()
        ) { fhirJsonStrings, error ->
            if (error != null) {
                continuation.resume(Result.failure(
                    HealthConnectorException.DataAccessError("Failed to read procedures: $error")
                ))
            } else {
                val procedures = fhirJsonStrings?.mapNotNull { json ->
                    (json as? String)?.let { FHIRParser.parseProcedure(it) }
                } ?: emptyList()
                continuation.resume(Result.success(procedures))
            }
        }
    }
    
    override suspend fun areClinicalRecordsAvailable(): Boolean {
        return clinicalRecordReader.areClinicalRecordsAvailable()
    }
    
    // Statistical queries
    override suspend fun readStatistics(
        dataType: HealthDataType,
        startDate: Instant,
        endDate: Instant,
        statisticOptions: Set<StatisticOption>,
        bucketDuration: Duration?
    ): Result<HealthStatistics> = suspendCoroutine { continuation ->
        val quantityType = dataType.toHKQuantityType()
        if (quantityType == null) {
            continuation.resume(Result.failure(
                HealthConnectorException.PlatformNotSupported("Statistics not supported for $dataType")
            ))
            return@suspendCoroutine
        }
        
        // Create predicate for time range
        val predicate = HKQuery.predicateForSamplesWithStartDate(
            startDate.toNSDate(),
            endDate.toNSDate(),
            options = HKQueryOptionNone
        )
        
        // Map StatisticOption to HKStatisticsOptions
        var statisticsOptions: HKStatisticsOptions = HKStatisticsOptionNone
        statisticOptions.forEach { option ->
            when (option) {
                StatisticOption.MINIMUM -> statisticsOptions = statisticsOptions or HKStatisticsOptionDiscreteMin
                StatisticOption.MAXIMUM -> statisticsOptions = statisticsOptions or HKStatisticsOptionDiscreteMax
                StatisticOption.AVERAGE -> statisticsOptions = statisticsOptions or HKStatisticsOptionDiscreteAverage
                StatisticOption.SUM -> {
                    // For sum, always try to use cumulative sum
                    statisticsOptions = statisticsOptions or HKStatisticsOptionCumulativeSum
                }
                StatisticOption.COUNT -> {} // Count is not directly supported by HKStatistics
            }
        }
        
        if (bucketDuration != null) {
            // Time-bucketed query using HKStatisticsCollectionQuery
            val interval = NSDateComponents().apply {
                if (bucketDuration.inWholeHours > 0) {
                    hour = bucketDuration.inWholeHours
                } else if (bucketDuration.inWholeMinutes > 0) {
                    minute = bucketDuration.inWholeMinutes
                } else {
                    second = bucketDuration.inWholeSeconds
                }
            }
            
            val query = HKStatisticsCollectionQuery(
                quantityType = quantityType,
                quantitySamplePredicate = predicate,
                options = statisticsOptions,
                anchorDate = startDate.toNSDate(),
                intervalComponents = interval
            )
            
            query.setInitialResultsHandler { _, results, error ->
                if (error != null) {
                    continuation.resume(Result.failure(
                        Exception(error.localizedDescription)
                    ))
                    return@setInitialResultsHandler
                }
                
                results?.let { collection ->
                    val buckets = mutableListOf<StatisticBucket>()
                    
                    collection.enumerateStatisticsFromDate(
                        startDate.toNSDate(),
                        toDate = endDate.toNSDate()
                    ) { statistics, _ ->
                        statistics?.let { stat ->
                            val bucketStats = extractStatistics(stat, statisticsOptions)
                            buckets.add(StatisticBucket(
                                startTime = stat.startDate.toKotlinInstant(),
                                endTime = stat.endDate.toKotlinInstant(),
                                statistics = bucketStats
                            ))
                        }
                    }
                    
                    continuation.resume(Result.success(HealthStatistics(
                        dataType = dataType,
                        startTime = startDate,
                        endTime = endDate,
                        buckets = buckets
                    )))
                } ?: continuation.resume(Result.failure(
                    Exception("No statistics collection returned")
                ))
            }
            
            healthStore.executeQuery(query)
        } else {
            // Single statistics query
            val query = HKStatisticsQuery(
                quantityType = quantityType,
                quantitySamplePredicate = predicate,
                options = statisticsOptions
            ) { _, statistics, error ->
                if (error != null) {
                    continuation.resume(Result.failure(
                        Exception(error.localizedDescription)
                    ))
                    return@HKStatisticsQuery
                }
                
                statistics?.let { stat ->
                    continuation.resume(Result.success(HealthStatistics(
                        dataType = dataType,
                        startTime = startDate,
                        endTime = endDate
                    )))
                } ?: continuation.resume(Result.failure(
                    Exception("No statistics returned")
                ))
            }
            
            healthStore.executeQuery(query)
        }
    }
    
    private fun extractStatistics(
        statistics: HKStatistics,
        options: HKStatisticsOptions
    ): Map<StatisticOption, Double> {
        val result = mutableMapOf<StatisticOption, Double>()
        
        if (options and HKStatisticsOptionDiscreteMin != 0uL) {
            statistics.minimumQuantity()?.let { quantity ->
                val unit = getPreferredUnit(statistics.quantityType)
                result[StatisticOption.MINIMUM] = quantity.doubleValueForUnit(unit)
            }
        }
        
        if (options and HKStatisticsOptionDiscreteMax != 0uL) {
            statistics.maximumQuantity()?.let { quantity ->
                val unit = getPreferredUnit(statistics.quantityType)
                result[StatisticOption.MAXIMUM] = quantity.doubleValueForUnit(unit)
            }
        }
        
        if (options and HKStatisticsOptionDiscreteAverage != 0uL) {
            statistics.averageQuantity()?.let { quantity ->
                val unit = getPreferredUnit(statistics.quantityType)
                result[StatisticOption.AVERAGE] = quantity.doubleValueForUnit(unit)
            }
        }
        
        if (options and HKStatisticsOptionCumulativeSum != 0uL) {
            statistics.sumQuantity()?.let { quantity ->
                val unit = getPreferredUnit(statistics.quantityType)
                result[StatisticOption.SUM] = quantity.doubleValueForUnit(unit)
            }
        }
        
        return result
    }
    
    private fun getPreferredUnit(quantityType: HKQuantityType): HKUnit {
        return when (quantityType.identifier) {
            HKQuantityTypeIdentifierStepCount -> HKUnit.countUnit()
            HKQuantityTypeIdentifierDistanceWalkingRunning,
            HKQuantityTypeIdentifierDistanceCycling -> HKUnit.meterUnit()
            HKQuantityTypeIdentifierActiveEnergyBurned,
            HKQuantityTypeIdentifierBasalEnergyBurned -> HKUnit.kilocalorieUnit()
            HKQuantityTypeIdentifierHeartRate -> HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit())
            HKQuantityTypeIdentifierBodyMass -> HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixKilo)
            HKQuantityTypeIdentifierHeight -> HKUnit.meterUnitWithMetricPrefix(HKMetricPrefixCenti)
            HKQuantityTypeIdentifierBodyFatPercentage -> HKUnit.percentUnit()
            HKQuantityTypeIdentifierBloodPressureSystolic,
            HKQuantityTypeIdentifierBloodPressureDiastolic -> HKUnit.millimeterOfMercuryUnit()
            HKQuantityTypeIdentifierOxygenSaturation -> HKUnit.percentUnit()
            HKQuantityTypeIdentifierBodyTemperature -> HKUnit.degreeCelsiusUnit()
            HKQuantityTypeIdentifierRespiratoryRate -> HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit())
            HKQuantityTypeIdentifierBloodGlucose -> HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixMilli).unitDividedByUnit(HKUnit.literUnitWithMetricPrefix(HKMetricPrefixDeci))
            else -> HKUnit.countUnit()
        }
    }
    
}

private fun HKWorkout.toWorkoutData(): WorkoutData {
    return WorkoutData(
        timestamp = startDate.toKotlinInstant(),
        id = UUID.UUIDString,
        type = workoutActivityType.toWorkoutType(),
        startTime = startDate.toKotlinInstant(),
        endTime = endDate.toKotlinInstant(),
        duration = duration.seconds,
        totalCalories = totalEnergyBurned?.doubleValueForUnit(HKUnit.kilocalorieUnit()),
        totalDistance = totalDistance?.doubleValueForUnit(HKUnit.meterUnit()),
        distanceUnit = DistanceUnit.METERS
    )
}

private fun HKWorkoutActivityType.toWorkoutType(): WorkoutType {
    return when (this) {
        HKWorkoutActivityTypeRunning -> WorkoutType.RUNNING
        HKWorkoutActivityTypeWalking -> WorkoutType.WALKING
        HKWorkoutActivityTypeCycling -> WorkoutType.CYCLING
        HKWorkoutActivityTypeSwimming -> WorkoutType.SWIMMING
        HKWorkoutActivityTypeYoga -> WorkoutType.YOGA
        HKWorkoutActivityTypeDanceInspiredTraining -> WorkoutType.DANCE
        HKWorkoutActivityTypeHiking -> WorkoutType.HIKING
        else -> WorkoutType.OTHER
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@Suppress("UNCHECKED_CAST")
private fun createDataSource(sample: HKSample): DataSource? {
    return try {
        // Use Swift helper to extract metadata
        val metadata = HealthKitMetadataExtractor.extractDataSourceFrom(sample) as NSDictionary
        
        val name = metadata.objectForKey("name") as? String ?: "Unknown"
        val bundleIdentifier = metadata.objectForKey("bundleIdentifier") as? String
        val sourceType = when (metadata.objectForKey("type") as? String) {
            "device" -> SourceType.DEVICE
            "application" -> SourceType.APPLICATION
            else -> SourceType.APPLICATION
        }
        
        // Create DeviceInfo if device details are available
        val deviceDict = metadata.objectForKey("device") as? NSDictionary
        val deviceInfo: vitality.models.DeviceInfo? = if (deviceDict != null) {
            val manufacturerStr = deviceDict.objectForKey("manufacturer") as? String
            val modelStr = deviceDict.objectForKey("model") as? String
            val typeStr = deviceDict.objectForKey("type") as? String
            val softwareVersionStr = deviceDict.objectForKey("softwareVersion") as? String
            
            val deviceType = when (typeStr) {
                "watch" -> vitality.models.DataSourceDeviceType.WATCH
                "phone" -> vitality.models.DataSourceDeviceType.PHONE
                else -> vitality.models.DataSourceDeviceType.OTHER
            }
            
            vitality.models.DeviceInfo(manufacturerStr, modelStr, deviceType, softwareVersionStr)
        } else {
            null
        }
        
        vitality.models.DataSource(
            name = name,
            type = sourceType,
            bundleIdentifier = bundleIdentifier,
            device = deviceInfo
        )
    } catch (_: Exception) {
        null
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@Suppress("UNCHECKED_CAST")
private fun createSampleMetadata(sample: HKSample): Map<String, Any> {
    return try {
        // Use Swift helper to extract metadata
        val metadataDict = HealthKitMetadataExtractor.extractMetadataFrom(sample) as NSDictionary
        val result = mutableMapOf<String, Any>()
        
        // Convert NSDictionary to Kotlin Map
        val allKeys = metadataDict.allKeys
        for (i in 0 until allKeys.size) {
            val key = allKeys[i] as? String
            if (key != null) {
                val value = metadataDict.objectForKey(key)
                if (value != null) {
                    result[key] = value
                }
            }
        }
        
        result
    } catch (_: Exception) {
        emptyMap()
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun HealthKitConnector.getCurrentOSVersion(): String {
    val device = platform.UIKit.UIDevice.currentDevice
    return device.systemVersion
}