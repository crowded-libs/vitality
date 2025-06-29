package vitality

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.*
import vitality.exceptions.HealthConnectorException
import vitality.extensions.toHealthConnectPermission
import vitality.extensions.toHeartRateData
import vitality.extensions.toKotlinInstant
import vitality.extensions.toWorkoutData
import vitality.models.*
import vitality.models.fhir.*
import vitality.helpers.observeRecords
import vitality.helpers.observeAggregatedRecords
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Instant
import androidx.health.connect.client.records.metadata.Metadata
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaInstant

/**
 * Android Health Connect implementation of HealthConnector
 * 
 * @param activity The ComponentActivity required for permission requests
 * @param pollingInterval Default polling interval for real-time data monitoring.
 *                        Defaults to 30 seconds.
 */
class HealthConnectConnector(
    private val activity: ComponentActivity,
    private val pollingInterval: Duration = 30.seconds
) : HealthConnector {
    private val context = activity.applicationContext
    private lateinit var healthConnectClient: HealthConnectClient
    private val workoutSessions = mutableMapOf<String, HealthConnectWorkoutSession>()
    private var medicalRecordsConnector: HealthConnectMedicalRecords? = null
    private lateinit var permissionLauncher: ActivityResultLauncher<Set<String>>
    private var permissionCallback: ((Set<String>) -> Unit)? = null

    private fun createMetadata(): Metadata {
        return Metadata.manualEntry()
    }
    
    private suspend fun requestPermissionsWithUI(permissions: Set<String>): Set<String> {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            permissionCallback = { grantedPermissions ->
                continuation.resume(grantedPermissions)
            }
            permissionLauncher.launch(permissions)
        }
    }

    override suspend fun initialize(): Result<Unit> {
        return try {
            val availability = HealthConnectClient.getSdkStatus(context)
            if (availability != HealthConnectClient.SDK_AVAILABLE) {
                return Result.failure(
                    HealthConnectorException.InitializationError(
                        "Health Connect is not available. Status: $availability"
                    )
                )
            }

            healthConnectClient = HealthConnectClient.getOrCreate(context)
            
            // Initialize permission launcher after healthConnectClient is created
            val requestPermissionActivityContract = androidx.health.connect.client.PermissionController
                .createRequestPermissionResultContract()
            
            permissionLauncher = activity.registerForActivityResult(requestPermissionActivityContract) { grantedPermissions ->
                permissionCallback?.invoke(grantedPermissions)
                permissionCallback = null
            }

            // Initialize medical records connector if supported
            if (Build.VERSION.SDK_INT >= 35) {
                medicalRecordsConnector = HealthConnectMedicalRecords(healthConnectClient)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(HealthConnectorException.InitializationError(
                "Failed to initialize Health Connect: ${e.message}"
            ))
        }
    }

    override suspend fun getAvailableDataTypes(): Set<HealthDataType> {
        val basicTypes = setOf(
            HealthDataType.Steps,
            HealthDataType.Distance,
            HealthDataType.Calories,
            HealthDataType.ActiveCalories,
            HealthDataType.BasalCalories,
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
            HealthDataType.LeanBodyMass,
            HealthDataType.Workout,
            HealthDataType.Sleep,
            HealthDataType.Water,
            HealthDataType.Protein,
            HealthDataType.Carbohydrates,
            HealthDataType.Fat,
            HealthDataType.BloodGlucose,
            HealthDataType.VO2Max,
            HealthDataType.Mindfulness
        )

        return buildSet {
            addAll(basicTypes)

            // Add body composition types (Android 8+)

            // Add reproductive health types (Android 9+)
            if (supportsReproductiveHealth()) {
                add(HealthDataType.MenstruationFlow)
                add(HealthDataType.MenstruationPeriod)
                add(HealthDataType.OvulationTest)
                add(HealthDataType.SexualActivity)
                add(HealthDataType.CervicalMucus)
                add(HealthDataType.IntermenstrualBleeding)
            }

            // Add advanced metrics (Android 11+)
            if (supportsAdvancedMetrics()) {
                add(HealthDataType.RestingHeartRate)
                add(HealthDataType.WheelchairPushes)
            }

            // Add clinical records (Android 16+)
            if (supportsMedicalRecords()) {
                add(HealthDataType.ClinicalAllergies)
                add(HealthDataType.ClinicalConditions)
                add(HealthDataType.ClinicalImmunizations)
                add(HealthDataType.ClinicalLabResults)
                add(HealthDataType.ClinicalMedications)
                add(HealthDataType.ClinicalProcedures)
                add(HealthDataType.ClinicalVitalSigns)
            }
        }
    }

    override suspend fun getPlatformCapabilities(): HealthCapabilities {
        return HealthCapabilities(
            platformName = "Android Health Connect (API ${Build.VERSION.SDK_INT})",
            supportsBackgroundDelivery = true, // Supported via WorkManager periodic sync
            supportsWearableIntegration = true, // Via Wear OS
            supportsLiveWorkoutMetrics = true,
            availableDataTypes = getAvailableDataTypes(),
            unavailableDataTypes = buildSet {
                add(HealthDataType.Floors)

                // Add iOS-specific types
                add(HealthDataType.StairAscentSpeed)
                add(HealthDataType.StairDescentSpeed)
                add(HealthDataType.WalkingAsymmetry)
                add(HealthDataType.WalkingDoubleSupportPercentage)
                add(HealthDataType.WalkingSpeed)
                add(HealthDataType.WalkingStepLength)
                add(HealthDataType.SixMinuteWalkTestDistance)
                add(HealthDataType.NumberOfTimesFallen)
                add(HealthDataType.StandHours)
                add(HealthDataType.EnvironmentalAudioExposure)
                add(HealthDataType.HeadphoneAudioExposure)
                // UVExposure and TimeInDaylight are not supported by Health Connect
                add(HealthDataType.RunningStrideLength)
                add(HealthDataType.RunningVerticalOscillation)
                add(HealthDataType.RunningGroundContactTime)
                add(HealthDataType.CyclingFunctionalThresholdPower)
            },
            supportedWorkoutTypes = WorkoutType.entries.toSet(),
            platformSpecificCapabilities = mapOf(
                "supportsRouteData" to false,
                "requiresPollingForRealtime" to true,
                "supportsWearOS" to true,
                "apiLevel" to Build.VERSION.SDK_INT,
                "supportsBodyComposition" to true,
                "supportsReproductiveHealth" to supportsReproductiveHealth(),
                "supportsAdvancedMetrics" to supportsAdvancedMetrics(),
                "supportsMedicalRecords" to supportsMedicalRecords()
            )
        )
    }

    override suspend fun requestPermissions(
        permissions: Set<HealthPermission>
    ): Result<PermissionResult> {
        val healthPermissions = permissions.mapNotNull { it.toHealthConnectPermission() }.toSet()

        return try {
            val grantedHealthConnectPermissions = healthConnectClient.permissionController.getGrantedPermissions()
            val requiredButNotGranted = healthPermissions - grantedHealthConnectPermissions

            if (requiredButNotGranted.isEmpty()) {
                // All permissions already granted
                Result.success(PermissionResult(
                    granted = permissions,
                    denied = emptySet()
                ))
            } else {
                // Launch permission request UI and wait for result
                val grantedPermissions = requestPermissionsWithUI(healthPermissions)
                
                Result.success(PermissionResult(
                    granted = permissions.filter { permission ->
                        val hcPerm = permission.toHealthConnectPermission()
                        hcPerm != null && grantedPermissions.contains(hcPerm)
                    }.toSet(),
                    denied = permissions.filter { permission ->
                        val hcPerm = permission.toHealthConnectPermission()
                        hcPerm == null || !grantedPermissions.contains(hcPerm)
                    }.toSet()
                ))
            }
        } catch (e: Exception) {
            Result.failure(HealthConnectorException.PermissionDenied(permissions, e))
        }
    }

    override suspend fun checkPermissions(
        permissions: Set<HealthPermission>
    ): PermissionStatus {
        return try {
            val grantedHealthConnectPermissions = healthConnectClient.permissionController.getGrantedPermissions()
            val healthPermissions = permissions.mapNotNull { it.toHealthConnectPermission() }.toSet()

            when {
                healthPermissions.all { hcPerm -> grantedHealthConnectPermissions.contains(hcPerm) } -> PermissionStatus.Granted
                healthPermissions.none { hcPerm -> grantedHealthConnectPermissions.contains(hcPerm) } -> PermissionStatus.Denied
                else -> PermissionStatus.PartiallyGranted(
                    granted = permissions.filter { permission ->
                        val hcPerm = permission.toHealthConnectPermission()
                        hcPerm != null && grantedHealthConnectPermissions.contains(hcPerm)
                    }.toSet(),
                    denied = permissions.filter { permission ->
                        val hcPerm = permission.toHealthConnectPermission()
                        hcPerm == null || !grantedHealthConnectPermissions.contains(hcPerm)
                    }.toSet()
                )
            }
        } catch (_: Exception) {
            PermissionStatus.NotDetermined
        }
    }

    override suspend fun readLatestHeartRate(): Result<HeartRateData?> {
        return try {
            val endTime = Clock.System.now()
            val startTime = endTime - 1.hours

            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startTime.toJavaInstant(),
                        endTime.toJavaInstant()
                    )
                )
            )

            val latest = response.records.maxByOrNull { it.startTime }
            Result.success(latest?.toHeartRateData())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readStepsToday(): Result<StepsData> {
        return try {
            val endTime = Clock.System.now()
            val startTime = endTime - 24.hours // Simple 24-hour period

            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        startTime.toJavaInstant(),
                        endTime.toJavaInstant()
                    )
                )
            )

            val totalSteps = response[StepsRecord.COUNT_TOTAL] ?: 0L

            Result.success(StepsData(
                timestamp = endTime,
                count = totalSteps.toInt(),
                startTime = startTime,
                endTime = endTime
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readCaloriesToday(): Result<CalorieData> {
        return try {
            val endTime = Clock.System.now()
            val startTime = endTime - 24.hours // Simple 24-hour period

            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(
                        startTime.toJavaInstant(),
                        endTime.toJavaInstant()
                    )
                )
            )

            val activeCalories = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
            val basalCalories = response[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]?.inKilocalories ?: 0.0

            Result.success(CalorieData(
                timestamp = endTime,
                activeCalories = activeCalories,
                basalCalories = basalCalories
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readLatestWeight(): Result<BodyMeasurements?> {
        return try {
            val endTime = Clock.System.now()
            val startTime = endTime - 30.days

            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startTime.toJavaInstant(),
                        endTime.toJavaInstant()
                    )
                )
            )

            val latest = response.records.maxByOrNull { it.time }
            Result.success(latest?.let {
                BodyMeasurements(
                    timestamp = it.time.toKotlinInstant(),
                    weight = it.weight.inKilograms,
                    weightUnit = WeightUnit.KILOGRAMS
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readWorkouts(
        startDate: Instant,
        endDate: Instant
    ): Result<List<WorkoutData>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )

            Result.success(response.records.map { it.toWorkoutData() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readHealthData(
        dataType: HealthDataType,
        startDate: Instant,
        endDate: Instant
    ): Result<List<HealthDataPoint>> {
        return try {
            when (dataType) {
                // Basic types already implemented in specific methods
                HealthDataType.Steps -> readStepsData(startDate, endDate)
                HealthDataType.HeartRate -> readHeartRateData(startDate, endDate)
                HealthDataType.Weight -> readWeightData(startDate, endDate)
                HealthDataType.Sleep -> readSleepData(startDate, endDate)

                // Body composition
                HealthDataType.BodyFat -> readBodyFatData(startDate, endDate)
                HealthDataType.BMI -> readBMIData(startDate, endDate)
                HealthDataType.LeanBodyMass -> readLeanBodyMassData(startDate, endDate)

                // Vitals
                HealthDataType.BloodPressure -> readBloodPressureData(startDate, endDate)
                HealthDataType.OxygenSaturation -> readOxygenSaturationData(startDate, endDate)
                HealthDataType.RespiratoryRate -> readRespiratoryRateData(startDate, endDate)
                HealthDataType.BodyTemperature -> readBodyTemperatureData(startDate, endDate)
                HealthDataType.RestingHeartRate -> readRestingHeartRateData(startDate, endDate)

                // Activity metrics
                HealthDataType.WheelchairPushes -> readWheelchairPushesData(startDate, endDate)

                // Reproductive health
                HealthDataType.MenstruationFlow -> readMenstruationFlowData(startDate, endDate)
                HealthDataType.MenstruationPeriod -> readMenstruationPeriodData(startDate, endDate)
                HealthDataType.OvulationTest -> readOvulationTestData(startDate, endDate)
                HealthDataType.SexualActivity -> readSexualActivityData(startDate, endDate)
                HealthDataType.CervicalMucus -> readCervicalMucusData(startDate, endDate)
                HealthDataType.IntermenstrualBleeding -> readIntermenstrualBleedingData(startDate, endDate)

                // Nutrition
                HealthDataType.Water -> readHydrationData(startDate, endDate)
                HealthDataType.Protein -> readNutritionData(startDate, endDate, "protein")
                HealthDataType.Carbohydrates -> readNutritionData(startDate, endDate, "carbohydrates")
                HealthDataType.Fat -> readNutritionData(startDate, endDate, "fat")

                // Other
                HealthDataType.BloodGlucose -> readBloodGlucoseData(startDate, endDate)
                HealthDataType.VO2Max -> readVO2MaxData(startDate, endDate)
                HealthDataType.Mindfulness -> readMindfulnessData(startDate, endDate)

                else -> Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(HealthConnectorException.DataAccessError(
                "Failed to read $dataType data: ${e.message}"
            ))
        }
    }

    // Flow-based implementations use polling
    @Suppress("UNCHECKED_CAST")
    override fun <T> observe(
        dataType: HealthDataType,
        samplingInterval: Duration
    ): Flow<T> = when (dataType) {
        HealthDataType.HeartRate -> observeHeartRateData(samplingInterval) as Flow<T>
        HealthDataType.Steps -> observeStepsData(samplingInterval) as Flow<T>
        HealthDataType.Distance -> observeDistanceData(samplingInterval) as Flow<T>
        HealthDataType.Calories -> observeCaloriesData(samplingInterval) as Flow<T>
        HealthDataType.Weight -> observeWeightData(samplingInterval) as Flow<T>
        HealthDataType.BloodGlucose -> observeBloodGlucoseData(samplingInterval) as Flow<T>
        HealthDataType.OxygenSaturation -> observeOxygenSaturationData(samplingInterval) as Flow<T>
        HealthDataType.RespiratoryRate -> observeRespiratoryRateData(samplingInterval) as Flow<T>
        HealthDataType.BodyTemperature -> observeBodyTemperatureData(samplingInterval) as Flow<T>
        HealthDataType.Sleep -> observeSleepData(samplingInterval) as Flow<T>
        HealthDataType.BloodPressure -> observeBloodPressureData(samplingInterval) as Flow<T>
        HealthDataType.HeartRateVariability -> observeHeartRateVariabilityData(samplingInterval) as Flow<T>
        HealthDataType.Water -> observeHydrationData(samplingInterval) as Flow<T>
        HealthDataType.RestingHeartRate -> observeRestingHeartRateData(samplingInterval) as Flow<T>
        HealthDataType.BodyFat -> observeBodyFatData(samplingInterval) as Flow<T>
        HealthDataType.LeanBodyMass -> observeLeanBodyMassData(samplingInterval) as Flow<T>
        HealthDataType.Protein -> observeNutritionData(samplingInterval, "protein") as Flow<T>
        HealthDataType.Carbohydrates -> observeNutritionData(samplingInterval, "carbohydrates") as Flow<T>
        HealthDataType.Fat -> observeNutritionData(samplingInterval, "fat") as Flow<T>
        // Add more types as needed
        else -> throw NotImplementedError("Observation of $dataType is not yet implemented")
    }
    
    private fun observeHeartRateData(samplingInterval: Duration): Flow<HeartRateData> = 
        healthConnectClient.observeRecords<HeartRateRecord, HeartRateData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 1.minutes
        ) { record ->
            record.toHeartRateData()
        }

    private fun observeCaloriesData(samplingInterval: Duration): Flow<CalorieData> = 
        healthConnectClient.observeAggregatedRecords(
            samplingInterval = samplingInterval,
            lookbackDuration = 5.minutes,
            recordTypes = listOf(
                ActiveCaloriesBurnedRecord::class,
                BasalMetabolicRateRecord::class
            )
        ) { _, _, records ->
            val caloriesByTime = mutableMapOf<Instant, CalorieData>()
            
            // Process active calories
            @Suppress("UNCHECKED_CAST")
            (records[ActiveCaloriesBurnedRecord::class] as? List<ActiveCaloriesBurnedRecord>)?.forEach { record ->
                val time = record.startTime.toKotlinInstant()
                val existing = caloriesByTime[time] ?: CalorieData(
                    timestamp = time,
                    activeCalories = 0.0,
                    basalCalories = 0.0
                )
                caloriesByTime[time] = existing.copy(
                    activeCalories = record.energy.inKilocalories
                )
            }
            
            // Process basal calories
            @Suppress("UNCHECKED_CAST")
            (records[BasalMetabolicRateRecord::class] as? List<BasalMetabolicRateRecord>)?.forEach { record ->
                val time = record.time.toKotlinInstant()
                val existing = caloriesByTime[time] ?: CalorieData(
                    timestamp = time,
                    activeCalories = 0.0,
                    basalCalories = 0.0
                )
                val basalRate = record.basalMetabolicRate.inKilocaloriesPerDay / 24 / 60 * 5
                caloriesByTime[time] = existing.copy(
                    basalCalories = basalRate
                )
            }
            
            caloriesByTime.values.toList()
        }

    private fun observeStepsData(samplingInterval: Duration): Flow<StepsData> = 
        healthConnectClient.observeRecords<StepsRecord, StepsData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 5.minutes
        ) { record ->
            StepsData(
                timestamp = record.startTime.toKotlinInstant(),
                count = record.count.toInt(),
                startTime = record.startTime.toKotlinInstant(),
                endTime = record.endTime.toKotlinInstant()
            )
        }

    private fun observeDistanceData(samplingInterval: Duration): Flow<DistanceData> = 
        healthConnectClient.observeRecords<DistanceRecord, DistanceData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 5.minutes
        ) { record ->
            DistanceData(
                timestamp = record.startTime.toKotlinInstant(),
                distance = record.distance.inMeters,
                unit = DistanceUnit.METERS,
                activityType = DistanceActivityType.UNKNOWN
            )
        }

    override fun observeActiveWorkout(): Flow<WorkoutData> = 
        healthConnectClient.observeRecords<ExerciseSessionRecord, WorkoutData>(
            samplingInterval = pollingInterval,
            lookbackDuration = 30.minutes
        ) { record ->
            record.toWorkoutData()
        }

    // Additional observe methods for other data types
    private fun observeWeightData(samplingInterval: Duration): Flow<BodyMeasurements> = 
        healthConnectClient.observeRecords<WeightRecord, BodyMeasurements>(
            samplingInterval = samplingInterval,
            lookbackDuration = 24.hours
        ) { record ->
            BodyMeasurements(
                weight = record.weight.inKilograms,
                weightUnit = WeightUnit.KILOGRAMS,
                timestamp = record.time.toKotlinInstant()
            )
        }

    private fun observeBloodGlucoseData(samplingInterval: Duration): Flow<GlucoseData> = 
        healthConnectClient.observeRecords<BloodGlucoseRecord, GlucoseData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 1.hours
        ) { record ->
            GlucoseData(
                timestamp = record.time.toKotlinInstant(),
                glucoseLevel = record.level.inMilligramsPerDeciliter / 18.0182, // Convert mg/dL to mmol/L
                mealRelation = when (record.relationToMeal) {
                    BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> MealRelation.BEFORE_MEAL
                    BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> MealRelation.AFTER_MEAL
                    BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> MealRelation.FASTING
                    else -> null
                },
                source = null,
                metadata = emptyMap()
            )
        }

    // Stub implementations for other types - to be implemented as needed
    private fun observeOxygenSaturationData(samplingInterval: Duration): Flow<OxygenSaturationData> = 
        healthConnectClient.observeRecords<OxygenSaturationRecord, OxygenSaturationData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 5.minutes
        ) { record ->
            OxygenSaturationData(
                timestamp = record.time.toKotlinInstant(),
                percentage = record.percentage.value,
                source = null,
                metadata = emptyMap()
            )
        }
    
    private fun observeRespiratoryRateData(samplingInterval: Duration): Flow<RespiratoryRateData> = 
        healthConnectClient.observeRecords<RespiratoryRateRecord, RespiratoryRateData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 5.minutes
        ) { record ->
            RespiratoryRateData(
                timestamp = record.time.toKotlinInstant(),
                breathsPerMinute = record.rate,
                source = null,
                metadata = emptyMap()
            )
        }
    
    private fun observeBodyTemperatureData(samplingInterval: Duration): Flow<BodyTemperatureData> = 
        healthConnectClient.observeRecords<BodyTemperatureRecord, BodyTemperatureData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 1.hours
        ) { record ->
            BodyTemperatureData(
                timestamp = record.time.toKotlinInstant(),
                temperature = record.temperature.inCelsius,
                unit = TemperatureUnit.CELSIUS,
                source = null,
                metadata = emptyMap()
            )
        }
    
    private fun observeSleepData(samplingInterval: Duration): Flow<SleepData> = 
        healthConnectClient.observeRecords<SleepSessionRecord, SleepData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 24.hours
        ) { record ->
            // Map stages if available
            val stages = record.stages.map { stage ->
                SleepStage(
                    stage = when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_AWAKE -> SleepStageType.AWAKE
                        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStageType.UNKNOWN
                        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.LIGHT
                        SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
                        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStageType.AWAKE
                        else -> SleepStageType.UNKNOWN
                    },
                    startTime = stage.startTime.toKotlinInstant(),
                    endTime = stage.endTime.toKotlinInstant(),
                    duration = (stage.endTime.toKotlinInstant() - stage.startTime.toKotlinInstant())
                )
            }
            
            SleepData(
                timestamp = record.startTime.toKotlinInstant(),
                startTime = record.startTime.toKotlinInstant(),
                endTime = record.endTime.toKotlinInstant(),
                duration = (record.endTime.toKotlinInstant() - record.startTime.toKotlinInstant()),
                stages = stages
            )
        }
    
    private fun observeBloodPressureData(samplingInterval: Duration): Flow<BloodPressureData> = 
        healthConnectClient.observeRecords<BloodPressureRecord, BloodPressureData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 1.hours
        ) { record ->
            BloodPressureData(
                timestamp = record.time.toKotlinInstant(),
                systolic = record.systolic.inMillimetersOfMercury.toInt(),
                diastolic = record.diastolic.inMillimetersOfMercury.toInt()
            )
        }
    
    private fun observeHeartRateVariabilityData(samplingInterval: Duration): Flow<HeartRateVariabilityData> = 
        healthConnectClient.observeRecords<HeartRateVariabilityRmssdRecord, HeartRateVariabilityData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 1.hours
        ) { record ->
            HeartRateVariabilityData(
                timestamp = record.time.toKotlinInstant(),
                sdnn = record.heartRateVariabilityMillis,
                source = null,
                metadata = emptyMap()
            )
        }
    
    private fun observeHydrationData(samplingInterval: Duration): Flow<HydrationData> = 
        healthConnectClient.observeRecords<HydrationRecord, HydrationData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 1.hours
        ) { record ->
            HydrationData(
                timestamp = record.startTime.toKotlinInstant(),
                volume = record.volume.inLiters
            )
        }
    
    private fun observeRestingHeartRateData(samplingInterval: Duration): Flow<RestingHeartRateData> = 
        healthConnectClient.observeRecords<RestingHeartRateRecord, RestingHeartRateData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 24.hours
        ) { record ->
            RestingHeartRateData(
                timestamp = record.time.toKotlinInstant(),
                bpm = record.beatsPerMinute.toInt(),
                source = null,
                metadata = emptyMap()
            )
        }
    
    private fun observeBodyFatData(samplingInterval: Duration): Flow<BodyMeasurements> = 
        healthConnectClient.observeRecords<BodyFatRecord, BodyMeasurements>(
            samplingInterval = samplingInterval,
            lookbackDuration = 24.hours
        ) { record ->
            BodyMeasurements(
                bodyFatPercentage = record.percentage.value,
                timestamp = record.time.toKotlinInstant()
            )
        }
    
    
    private fun observeLeanBodyMassData(samplingInterval: Duration): Flow<BodyMeasurements> = 
        healthConnectClient.observeRecords<LeanBodyMassRecord, BodyMeasurements>(
            samplingInterval = samplingInterval,
            lookbackDuration = 24.hours
        ) { record ->
            BodyMeasurements(
                leanBodyMass = record.mass.inKilograms,
                timestamp = record.time.toKotlinInstant()
            )
        }
    
    
    private fun observeNutritionData(samplingInterval: Duration, nutrientType: String): Flow<NutritionData> = 
        healthConnectClient.observeRecords<NutritionRecord, NutritionData>(
            samplingInterval = samplingInterval,
            lookbackDuration = 1.hours
        ) { record ->
            when (nutrientType) {
                "protein" -> NutritionData(
                    timestamp = record.startTime.toKotlinInstant(),
                    protein = record.protein?.inGrams
                )
                "carbohydrates" -> NutritionData(
                    timestamp = record.startTime.toKotlinInstant(),
                    carbohydrates = record.totalCarbohydrate?.inGrams
                )
                "fat" -> NutritionData(
                    timestamp = record.startTime.toKotlinInstant(),
                    fat = record.totalFat?.inGrams
                )
                else -> NutritionData(timestamp = record.startTime.toKotlinInstant())
            }
        }


    // Workout session management
    override suspend fun startWorkoutSession(
        workoutType: WorkoutType
    ): Result<WorkoutSession> {
        return try {
            val sessionId = java.util.UUID.randomUUID().toString()
            val session = HealthConnectWorkoutSession(
                id = sessionId,
                type = workoutType,
                startTime = Clock.System.now(),
                healthConnectClient = healthConnectClient
            )

            workoutSessions[sessionId] = session
            session.start()

            Result.success(session)
        } catch (e: Exception) {
            Result.failure(HealthConnectorException.WorkoutSessionError(
                "Failed to start workout session: ${e.message}"
            ))
        }
    }

    override suspend fun startWorkoutSession(
        configuration: WorkoutConfiguration
    ): Result<WorkoutSession> {
        return try {
            val sessionId = java.util.UUID.randomUUID().toString()
            val session = HealthConnectWorkoutSession(
                id = sessionId,
                type = configuration.type,
                startTime = Clock.System.now(),
                healthConnectClient = healthConnectClient,
                configuration = configuration
            )

            workoutSessions[sessionId] = session
            session.start()

            Result.success(session)
        } catch (e: Exception) {
            Result.failure(HealthConnectorException.WorkoutSessionError(
                "Failed to start workout session: ${e.message}"
            ))
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

    // Data writing
    override suspend fun writeWeight(
        weight: Double,
        unit: WeightUnit,
        timestamp: Instant
    ): Result<Unit> {
        return try {
            val weightInKg = when (unit) {
                WeightUnit.KILOGRAMS -> weight
                WeightUnit.POUNDS -> weight * 0.453592
                WeightUnit.STONES -> weight * 6.35029
            }

            healthConnectClient.insertRecords(
                listOf(
                    WeightRecord(
                        time = timestamp.toJavaInstant(),
                        weight = Mass.kilograms(weightInKg),
                        zoneOffset = null,
                        metadata = createMetadata()
                    )
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("RestrictedApi")
    override suspend fun writeWorkout(workoutData: WorkoutData): Result<Unit> {
        return try {
            if (!validateWorkoutData(workoutData)) {
                return Result.failure(
                    HealthConnectorException.InitializationError("Invalid workout data: duration must be positive")
                )
            }
            
            val recordsToInsert = mutableListOf<Record>()
            
            // Create Exercise Session Record
            val startInstant = workoutData.startTime.toJavaInstant()
            val endInstant = workoutData.endTime?.toJavaInstant() ?: (workoutData.startTime + (workoutData.duration ?: Duration.ZERO)).toJavaInstant()
            val startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(startInstant)
            val endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(endInstant)
            
            // Build segments list if available
            val segments = workoutData.segments?.map { segment ->
                ExerciseSegment(
                    startTime = segment.startTime.toJavaInstant(),
                    endTime = segment.endTime.toJavaInstant(),
                    segmentType = segment.type.toHealthConnectSegmentType()
                )
            } ?: emptyList()
            
            val sessionRecord = ExerciseSessionRecord(
                startTime = startInstant,
                endTime = endInstant,
                startZoneOffset = startZoneOffset,
                endZoneOffset = endZoneOffset,
                exerciseType = workoutData.type.toHealthConnectExerciseType(),
                title = workoutData.title,
                notes = workoutData.metadata["notes"] as? String,
                segments = segments,
                metadata = createMetadata()
            )
            recordsToInsert.add(sessionRecord)
            
            // Add associated records for comprehensive data
            val middleInstant = startInstant.plusMillis((endInstant.toEpochMilli() - startInstant.toEpochMilli()) / 2)
            
            // Active calories burned during workout
            workoutData.activeCalories?.let { calories ->
                if (calories > 0) {
                    recordsToInsert.add(
                        ActiveCaloriesBurnedRecord(
                            startTime = startInstant,
                            endTime = endInstant,
                            startZoneOffset = startZoneOffset,
                            endZoneOffset = endZoneOffset,
                            energy = Energy.kilocalories(calories),
                            metadata = createMetadata()
                        )
                    )
                }
            }
            
            workoutData.totalCalories?.let { calories ->
                if (calories > 0 && calories != workoutData.activeCalories) {
                    recordsToInsert.add(
                        TotalCaloriesBurnedRecord(
                            startTime = startInstant,
                            endTime = endInstant,
                            startZoneOffset = startZoneOffset,
                            endZoneOffset = endZoneOffset,
                            energy = Energy.kilocalories(calories),
                            metadata = createMetadata()
                        )
                    )
                }
            }
            
            // Distance
            workoutData.totalDistance?.let { distance ->
                if (distance > 0) {
                    recordsToInsert.add(
                        DistanceRecord(
                            startTime = startInstant,
                            endTime = endInstant,
                            startZoneOffset = startZoneOffset,
                            endZoneOffset = endZoneOffset,
                            distance = when (workoutData.distanceUnit) {
                                DistanceUnit.MILES -> Length.miles(distance)
                                DistanceUnit.KILOMETERS -> Length.kilometers(distance)
                                DistanceUnit.METERS -> Length.meters(distance)
                                else -> Length.meters(distance)
                            },
                            metadata = createMetadata()
                        )
                    )
                }
            }
            
            // Steps
            workoutData.stepCount?.let { steps ->
                if (steps > 0) {
                    recordsToInsert.add(
                        StepsRecord(
                            startTime = startInstant,
                            endTime = endInstant,
                            startZoneOffset = startZoneOffset,
                            endZoneOffset = endZoneOffset,
                            count = steps.toLong(),
                            metadata = createMetadata()
                        )
                    )
                }
            }
            
            if (workoutData.averageHeartRate != null || workoutData.maxHeartRate != null || workoutData.minHeartRate != null) {
                val heartRateSamples = mutableListOf<HeartRateRecord.Sample>()
                
                // Add samples at start, middle, and end to represent the workout
                workoutData.minHeartRate?.let {
                    if (it > 0) heartRateSamples.add(HeartRateRecord.Sample(startInstant, it.toLong()))
                }
                
                workoutData.averageHeartRate?.let {
                    if (it > 0) heartRateSamples.add(HeartRateRecord.Sample(middleInstant, it.toLong()))
                }
                
                workoutData.maxHeartRate?.let {
                    if (it > 0) heartRateSamples.add(HeartRateRecord.Sample(endInstant, it.toLong()))
                }
                
                if (heartRateSamples.isNotEmpty()) {
                    recordsToInsert.add(
                        HeartRateRecord(
                            startTime = startInstant,
                            endTime = endInstant,
                            startZoneOffset = startZoneOffset,
                            endZoneOffset = endZoneOffset,
                            samples = heartRateSamples,
                            metadata = createMetadata()
                        )
                    )
                }
            }
            
            // Elevation gained
            workoutData.elevationGained?.let { elevation ->
                if (elevation > 0) {
                    recordsToInsert.add(
                        ElevationGainedRecord(
                            startTime = startInstant,
                            endTime = endInstant,
                            startZoneOffset = startZoneOffset,
                            endZoneOffset = endZoneOffset,
                            elevation = Length.meters(elevation),
                            metadata = createMetadata()
                        )
                    )
                }
            }
            
            
            // Insert all records
            healthConnectClient.insertRecords(recordsToInsert)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                HealthConnectorException.DataAccessError("Failed to write workout: ${e.message}")
            )
        }
    }

    override suspend fun writeHealthData(dataPoint: HealthDataPoint): Result<Unit> {
        return try {
            when (dataPoint) {
                is HeartRateData -> writeHeartRateData(dataPoint)
                is StepsData -> writeStepsData(dataPoint)
                is CalorieData -> writeCalorieData(dataPoint)
                is SleepData -> writeSleepData(dataPoint)
                is BodyMeasurements -> writeBodyMeasurements(dataPoint)
                is BloodPressureData -> writeBloodPressureData(dataPoint)
                is GlucoseData -> writeGlucoseData(dataPoint)
                is RespiratoryRateData -> writeRespiratoryRateData(dataPoint)
                is OxygenSaturationData -> writeOxygenSaturationData(dataPoint)
                is BodyTemperatureData -> writeBodyTemperatureData(dataPoint)
                is HydrationData -> writeHydrationData(dataPoint)
                is NutritionData -> writeNutritionData(dataPoint)
                is RestingHeartRateData -> writeRestingHeartRateData(dataPoint)
                is SpeedData -> writeSpeedData(dataPoint)
                is PowerData -> writePowerData(dataPoint)
                is CyclingCadenceData -> writeCadenceData(dataPoint)
                is WheelchairPushesData -> writeWheelchairPushData(dataPoint)
                is MenstruationFlowData -> writeMenstruationFlowData(dataPoint)
                is OvulationTestData -> writeOvulationTestData(dataPoint)
                is SexualActivityData -> writeSexualActivityData(dataPoint)
                is CervicalMucusData -> writeCervicalMucusData(dataPoint)
                is IntermenstrualBleedingData -> writeIntermenstrualBleedingData(dataPoint)
                else -> Result.failure(
                    HealthConnectorException.PlatformNotSupported(
                        "Data type ${dataPoint::class.simpleName} not supported for writing"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                HealthConnectorException.DataAccessError("Failed to write health data: ${e.message}")
            )
        }
    }

    // Clinical Records (FHIR) - Android 16+ with Health Connect

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override suspend fun readImmunizations(
        startDate: Instant?,
        endDate: Instant?
    ): Result<List<FHIRImmunization>> {
        return if (areClinicalRecordsAvailable()) {
            medicalRecordsConnector?.readImmunizations(startDate ?: Instant.DISTANT_PAST, endDate ?: Clock.System.now())
                ?: Result.failure(
                    HealthConnectorException.InitializationError("Medical records connector not initialized")
                )
        } else {
            Result.success(emptyList())
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Suppress("UNUSED_PARAMETER") // Date parameters reserved for future Health Connect API updates
    override suspend fun readMedications(
        startDate: Instant?,
        endDate: Instant?
    ): Result<List<FHIRResource>> {
        return if (areClinicalRecordsAvailable()) {
            medicalRecordsConnector?.readMedications()
                ?: Result.failure(
                    HealthConnectorException.InitializationError("Medical records connector not initialized")
                )
        } else {
            Result.success(emptyList())
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override suspend fun readAllergies(
        includeInactive: Boolean
    ): Result<List<FHIRAllergyIntolerance>> {
        return if (areClinicalRecordsAvailable()) {
            val result = medicalRecordsConnector?.readAllergies()
                ?: return Result.failure(
                    HealthConnectorException.InitializationError("Medical records connector not initialized")
                )
            
            // Filter by status if not including inactive
            if (!includeInactive) {
                result.map { allergies ->
                    allergies.filter { allergy ->
                        allergy.clinicalStatus?.coding?.any { coding ->
                            coding.code == "active"
                        } == true
                    }
                }
            } else {
                result
            }
        } else {
            Result.success(emptyList())
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override suspend fun readConditions(
        includeResolved: Boolean
    ): Result<List<FHIRCondition>> {
        return if (areClinicalRecordsAvailable()) {
            val result = medicalRecordsConnector?.readConditions()
                ?: return Result.failure(
                    HealthConnectorException.InitializationError("Medical records connector not initialized")
                )
            
            // Filter out resolved conditions if not including them
            if (!includeResolved) {
                result.map { conditions ->
                    conditions.filter { condition ->
                        condition.clinicalStatus?.coding?.any { coding ->
                            coding.code != "resolved" && coding.code != "inactive"
                        } == true
                    }
                }
            } else {
                result
            }
        } else {
            Result.success(emptyList())
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override suspend fun readLabResults(
        startDate: Instant?,
        endDate: Instant?,
        category: String?
    ): Result<List<FHIRObservation>> {
        return if (areClinicalRecordsAvailable()) {
            val result = medicalRecordsConnector?.readLabResults(startDate ?: Instant.DISTANT_PAST, endDate ?: Clock.System.now())
                ?: return Result.failure(
                    HealthConnectorException.InitializationError("Medical records connector not initialized")
                )
            
            // Filter by category if provided
            if (category != null) {
                result.map { observations ->
                    observations.filter { observation ->
                        observation.category?.any { cat ->
                            cat.coding?.any { coding ->
                                coding.code == category || coding.display?.contains(category, ignoreCase = true) == true
                            } == true
                        } == true
                    }
                }
            } else {
                result
            }
        } else {
            Result.success(emptyList())
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override suspend fun readProcedures(
        startDate: Instant?,
        endDate: Instant?
    ): Result<List<FHIRProcedure>> {
        return if (areClinicalRecordsAvailable()) {
            medicalRecordsConnector?.readProcedures(startDate ?: Instant.DISTANT_PAST, endDate ?: Clock.System.now())
                ?: Result.failure(
                    HealthConnectorException.InitializationError("Medical records connector not initialized")
                )
        } else {
            Result.success(emptyList())
        }
    }

    override suspend fun areClinicalRecordsAvailable(): Boolean {
        // Clinical records are available on Android 16+ with Health Connect 1.1.0+
        return try {
            // Check Android version
            val androidVersion = Build.VERSION.SDK_INT
            // Android 16 corresponds to API level 35 (estimated)
            androidVersion >= 35
        } catch (_: Exception) {
            false
        }
    }

    private fun supportsAdvancedMetrics(): Boolean = Build.VERSION.SDK_INT >= 30 // Android 11+
    private fun supportsMedicalRecords(): Boolean = Build.VERSION.SDK_INT >= 35 // Android 16+
    private fun supportsReproductiveHealth(): Boolean = Build.VERSION.SDK_INT >= 28 // Android 9+

    private suspend fun readStepsData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                StepsData(
                    timestamp = record.startTime.toKotlinInstant(),
                    count = record.count.toInt(),
                    startTime = record.startTime.toKotlinInstant(),
                    endTime = record.endTime.toKotlinInstant()
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readHeartRateData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { it.toHeartRateData() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readWeightData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                BodyMeasurements(
                    timestamp = record.time.toKotlinInstant(),
                    weight = record.weight.inKilograms,
                    weightUnit = WeightUnit.KILOGRAMS
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readSleepData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                val stages = record.stages.map { stage ->
                    SleepStage(
                        stage = mapSleepStageType(stage.stage),
                        startTime = stage.startTime.toKotlinInstant(),
                        endTime = stage.endTime.toKotlinInstant(),
                        duration = (stage.endTime.toKotlinInstant() - stage.startTime.toKotlinInstant())
                    )
                }

                SleepData(
                    timestamp = record.startTime.toKotlinInstant(),
                    startTime = record.startTime.toKotlinInstant(),
                    endTime = record.endTime.toKotlinInstant(),
                    duration = (record.endTime.toKotlinInstant() - record.startTime.toKotlinInstant()),
                    stages = stages,
                    source = DataSource(
                        name = record.metadata.dataOrigin.packageName,
                        type = SourceType.APPLICATION
                    ),
                    metadata = buildMap {
                        record.title?.let { put("title", it) }
                        record.notes?.let { put("notes", it) }
                        record.metadata.dataOrigin.packageName.let { put("sourceApp", it) }
                        record.metadata.id.let { put("recordId", it) }
                    }
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readBodyFatData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BodyFatRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                BodyMeasurements(
                    timestamp = record.time.toKotlinInstant(),
                    bodyFatPercentage = record.percentage.value
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readBloodPressureData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BloodPressureRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                BloodPressureData(
                    timestamp = record.time.toKotlinInstant(),
                    systolic = record.systolic.inMillimetersOfMercury.toInt(),
                    diastolic = record.diastolic.inMillimetersOfMercury.toInt()
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Batch query support
    suspend fun readMultipleDataTypes(
        dataTypes: Set<HealthDataType>,
        startDate: Instant,
        endDate: Instant
    ): Map<HealthDataType, Result<List<HealthDataPoint>>> = coroutineScope {
        // Execute queries in parallel for better performance
        dataTypes.associateWith { dataType ->
            async {
                readHealthData(dataType, startDate, endDate)
            }
        }.mapValues { it.value.await() }
    }

    private suspend fun readBMIData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        // BMI calculation based on height and weight if direct record not available
        return try {
            // Read weight and height data
            val weightResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            
            val heightResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            
            // If we have both height and weight data, calculate BMI
            val bmiDataPoints = mutableListOf<HealthDataPoint>()
            
            for (weightRecord in weightResponse.records) {
                val closestHeight = heightResponse.records
                    .filter { it.time <= weightRecord.time }
                    .maxByOrNull { it.time }
                
                if (closestHeight != null) {
                    val weightKg = weightRecord.weight.inKilograms
                    val heightM = closestHeight.height.inMeters
                    val bmi = weightKg / (heightM * heightM)
                    
                    bmiDataPoints.add(
                        BodyMeasurements(
                            timestamp = weightRecord.time.toKotlinInstant(),
                            bmi = bmi
                        )
                    )
                }
            }
            
            Result.success(bmiDataPoints)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readLeanBodyMassData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = LeanBodyMassRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                BodyMeasurements(
                    timestamp = record.time.toKotlinInstant(),
                    leanBodyMass = record.mass.inKilograms
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    private suspend fun readOxygenSaturationData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                OxygenSaturationData(
                    timestamp = record.time.toKotlinInstant(),
                    percentage = record.percentage.value
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readRespiratoryRateData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = RespiratoryRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                RespiratoryRateData(
                    timestamp = record.time.toKotlinInstant(),
                    breathsPerMinute = record.rate
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readBodyTemperatureData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BodyTemperatureRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                BodyTemperatureData(
                    timestamp = record.time.toKotlinInstant(),
                    temperature = record.temperature.inCelsius,
                    unit = TemperatureUnit.CELSIUS
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readRestingHeartRateData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                RestingHeartRateData(
                    timestamp = record.time.toKotlinInstant(),
                    bpm = record.beatsPerMinute.toInt()
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readSpeedData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = SpeedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.flatMap { record ->
                record.samples.map { sample ->
                    SpeedData(
                        timestamp = sample.time.toKotlinInstant(),
                        speedMetersPerSecond = sample.speed.inMetersPerSecond
                    )
                }
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readPowerData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = PowerRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.flatMap { record ->
                record.samples.map { sample ->
                    PowerData(
                        timestamp = sample.time.toKotlinInstant(),
                        watts = sample.power.inWatts
                    )
                }
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readCyclingCadenceData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = CyclingPedalingCadenceRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.flatMap { record ->
                record.samples.map { sample ->
                    CyclingCadenceData(
                        timestamp = sample.time.toKotlinInstant(),
                        rpm = sample.revolutionsPerMinute.toInt()
                    )
                }
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readWheelchairPushesData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = WheelchairPushesRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                WheelchairPushesData(
                    timestamp = record.startTime.toKotlinInstant(),
                    pushCount = record.count.toInt(),
                    duration = record.endTime.toKotlinInstant() - record.startTime.toKotlinInstant()
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readMenstruationFlowData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = MenstruationFlowRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                MenstruationFlowData(
                    timestamp = record.time.toKotlinInstant(),
                    flow = when (record.flow) {
                        MenstruationFlowRecord.FLOW_UNKNOWN -> FlowLevel.UNSPECIFIED
                        MenstruationFlowRecord.FLOW_LIGHT -> FlowLevel.LIGHT
                        MenstruationFlowRecord.FLOW_MEDIUM -> FlowLevel.MEDIUM
                        MenstruationFlowRecord.FLOW_HEAVY -> FlowLevel.HEAVY
                        else -> FlowLevel.UNSPECIFIED
                    }
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readMenstruationPeriodData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = MenstruationPeriodRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                // Return as metadata since period is a time range
                object : HealthDataPoint() {
                    override val timestamp = record.startTime.toKotlinInstant()
                    override val source: DataSource? = null
                    override val metadata = mapOf(
                        "startTime" to record.startTime.toKotlinInstant().toString(),
                        "endTime" to record.endTime.toKotlinInstant().toString()
                    )
                }
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readOvulationTestData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = OvulationTestRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                OvulationTestData(
                    timestamp = record.time.toKotlinInstant(),
                    result = when (record.result) {
                        OvulationTestRecord.RESULT_POSITIVE -> OvulationTestResult.POSITIVE
                        OvulationTestRecord.RESULT_NEGATIVE -> OvulationTestResult.NEGATIVE
                        OvulationTestRecord.RESULT_INCONCLUSIVE -> OvulationTestResult.INDETERMINATE
                        else -> OvulationTestResult.INDETERMINATE
                    }
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readSexualActivityData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = SexualActivityRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                SexualActivityData(
                    timestamp = record.time.toKotlinInstant(),
                    protectionUsed = when (record.protectionUsed) {
                        1 -> true   // PROTECTION_USED_PROTECTED
                        0 -> false  // PROTECTION_USED_UNPROTECTED
                        else -> null // PROTECTION_USED_UNKNOWN
                    }
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readCervicalMucusData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = CervicalMucusRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                CervicalMucusData(
                    timestamp = record.time.toKotlinInstant(),
                    quality = when (record.sensation) {
                        CervicalMucusRecord.SENSATION_LIGHT -> CervicalMucusQuality.DRY
                        CervicalMucusRecord.SENSATION_MEDIUM -> CervicalMucusQuality.STICKY
                        CervicalMucusRecord.SENSATION_HEAVY -> CervicalMucusQuality.WATERY
                        else -> CervicalMucusQuality.STICKY // Default to sticky as neutral option
                    }
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readIntermenstrualBleedingData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = IntermenstrualBleedingRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                IntermenstrualBleedingData(
                    timestamp = record.time.toKotlinInstant(),
                    isSpotting = true // Intermenstrual bleeding is always spotting
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readHydrationData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HydrationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                HydrationData(
                    timestamp = record.startTime.toKotlinInstant(),
                    volume = record.volume.inMilliliters
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readNutritionData(startDate: Instant, endDate: Instant, nutrientType: String): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = NutritionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                NutritionData(
                    timestamp = record.startTime.toKotlinInstant(),
                    calories = record.energy?.inKilocalories,
                    protein = when (nutrientType) {
                        "protein" -> record.protein?.inGrams
                        else -> null
                    },
                    carbohydrates = when (nutrientType) {
                        "carbohydrates" -> record.totalCarbohydrate?.inGrams
                        else -> null
                    },
                    fat = when (nutrientType) {
                        "fat" -> record.totalFat?.inGrams
                        else -> null
                    },
                    saturatedFat = record.saturatedFat?.inGrams,
                    fiber = record.dietaryFiber?.inGrams,
                    sugar = record.sugar?.inGrams,
                    sodium = record.sodium?.inMilligrams,
                    cholesterol = record.cholesterol?.inMilligrams,
                    metadata = buildMap {
                        record.name?.let { put("mealName", it) }
                    }
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readBloodGlucoseData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BloodGlucoseRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                GlucoseData(
                    timestamp = record.time.toKotlinInstant(),
                    glucoseLevel = record.level.inMilligramsPerDeciliter / 18.0182, // Convert mg/dL to mmol/L
                    specimenSource = when (record.specimenSource) {
                        BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID -> SpecimenSource.INTERSTITIAL_FLUID
                        BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD -> SpecimenSource.CAPILLARY_BLOOD
                        BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA -> SpecimenSource.PLASMA
                        BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM -> SpecimenSource.SERUM
                        BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD -> SpecimenSource.WHOLE_BLOOD
                        else -> SpecimenSource.UNKNOWN
                    },
                    mealRelation = when (record.relationToMeal) {
                        BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> MealRelation.BEFORE_MEAL
                        BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> MealRelation.AFTER_MEAL
                        BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> MealRelation.FASTING
                        else -> MealRelation.UNKNOWN
                    }
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readVO2MaxData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = Vo2MaxRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            Result.success(response.records.map { record ->
                // Return as generic HealthDataPoint with VO2 max value in metadata
                object : HealthDataPoint() {
                    override val timestamp = record.time.toKotlinInstant()
                    override val source: DataSource? = null
                    override val metadata = mapOf(
                        "vo2MaxMlPerKgMin" to record.vo2MillilitersPerMinuteKilogram,
                        "measurementMethod" to record.measurementMethod
                    )
                }
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun readMindfulnessData(startDate: Instant, endDate: Instant): Result<List<HealthDataPoint>> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    )
                )
            )
            
            // Filter for meditation/mindfulness exercise types
            // Health Connect doesn't have specific meditation/breathing types, so we use YOGA as a proxy
            val mindfulnessRecords = response.records.filter { record ->
                record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_YOGA
            }
            
            Result.success(mindfulnessRecords.map { record ->
                MindfulnessSessionData(
                    timestamp = record.startTime.toKotlinInstant(),
                    duration = record.endTime.toKotlinInstant() - record.startTime.toKotlinInstant(),
                    startTime = record.startTime.toKotlinInstant(),
                    endTime = record.endTime.toKotlinInstant(),
                    metadata = buildMap {
                        record.title?.let { put("title", it) }
                        record.notes?.let { put("notes", it) }
                    }
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Statistical queries implementation
    override suspend fun readStatistics(
        dataType: HealthDataType,
        startDate: Instant,
        endDate: Instant,
        statisticOptions: Set<StatisticOption>,
        bucketDuration: Duration?
    ): Result<HealthStatistics> {
        return try {
            val metrics = statisticOptions.mapNotNull { option ->
                when (option) {
                    StatisticOption.MINIMUM -> when (dataType) {
                        HealthDataType.Steps -> StepsRecord.COUNT_TOTAL
                        HealthDataType.HeartRate -> HeartRateRecord.BPM_MIN
                        HealthDataType.Distance -> DistanceRecord.DISTANCE_TOTAL
                        HealthDataType.Calories -> TotalCaloriesBurnedRecord.ENERGY_TOTAL
                        HealthDataType.ActiveCalories -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
                        else -> null
                    }
                    StatisticOption.MAXIMUM -> when (dataType) {
                        HealthDataType.Steps -> StepsRecord.COUNT_TOTAL
                        HealthDataType.HeartRate -> HeartRateRecord.BPM_MAX
                        HealthDataType.Distance -> DistanceRecord.DISTANCE_TOTAL
                        HealthDataType.Calories -> TotalCaloriesBurnedRecord.ENERGY_TOTAL
                        HealthDataType.ActiveCalories -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
                        else -> null
                    }
                    StatisticOption.AVERAGE -> when (dataType) {
                        HealthDataType.Steps -> StepsRecord.COUNT_TOTAL
                        HealthDataType.HeartRate -> HeartRateRecord.BPM_AVG
                        HealthDataType.Distance -> DistanceRecord.DISTANCE_TOTAL
                        HealthDataType.Calories -> TotalCaloriesBurnedRecord.ENERGY_TOTAL
                        HealthDataType.ActiveCalories -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
                        else -> null
                    }
                    StatisticOption.SUM -> when (dataType) {
                        HealthDataType.Steps -> StepsRecord.COUNT_TOTAL
                        HealthDataType.Distance -> DistanceRecord.DISTANCE_TOTAL
                        HealthDataType.Calories -> TotalCaloriesBurnedRecord.ENERGY_TOTAL
                        HealthDataType.ActiveCalories -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
                        else -> null
                    }
                    else -> null
                }
            }.toSet()

            if (metrics.isEmpty()) {
                return Result.success(HealthStatistics(
                    dataType = dataType,
                    startTime = startDate,
                    endTime = endDate
                ))
            }

            val aggregateResponse = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toJavaInstant(),
                        endDate.toJavaInstant()
                    ),
                    dataOriginFilter = emptySet()
                )
            )

            val statistics = mutableMapOf<StatisticOption, Double>()
            
            statisticOptions.forEach { option ->
                when (option) {
                    StatisticOption.MINIMUM -> {
                        when (dataType) {
                            HealthDataType.HeartRate -> {
                                aggregateResponse[HeartRateRecord.BPM_MIN]?.let {
                                    statistics[StatisticOption.MINIMUM] = it.toDouble()
                                }
                            }
                            else -> {}
                        }
                    }
                    StatisticOption.MAXIMUM -> {
                        when (dataType) {
                            HealthDataType.HeartRate -> {
                                aggregateResponse[HeartRateRecord.BPM_MAX]?.let {
                                    statistics[StatisticOption.MAXIMUM] = it.toDouble()
                                }
                            }
                            else -> {}
                        }
                    }
                    StatisticOption.AVERAGE -> {
                        when (dataType) {
                            HealthDataType.HeartRate -> {
                                aggregateResponse[HeartRateRecord.BPM_AVG]?.let {
                                    statistics[StatisticOption.AVERAGE] = it.toDouble()
                                }
                            }
                            else -> {}
                        }
                    }
                    StatisticOption.SUM -> {
                        when (dataType) {
                            HealthDataType.Steps -> {
                                aggregateResponse[StepsRecord.COUNT_TOTAL]?.let {
                                    statistics[StatisticOption.SUM] = it.toDouble()
                                }
                            }
                            HealthDataType.Distance -> {
                                aggregateResponse[DistanceRecord.DISTANCE_TOTAL]?.let {
                                    statistics[StatisticOption.SUM] = it.inMeters
                                }
                            }
                            HealthDataType.Calories -> {
                                aggregateResponse[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let {
                                    statistics[StatisticOption.SUM] = it.inKilocalories
                                }
                            }
                            HealthDataType.ActiveCalories -> {
                                aggregateResponse[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let {
                                    statistics[StatisticOption.SUM] = it.inKilocalories
                                }
                            }
                            else -> {}
                        }
                    }
                    StatisticOption.COUNT -> {
                        // COUNT is typically the same as SUM for countable types
                        when (dataType) {
                            HealthDataType.Steps -> {
                                aggregateResponse[StepsRecord.COUNT_TOTAL]?.let {
                                    statistics[StatisticOption.COUNT] = it.toDouble()
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            Result.success(HealthStatistics(
                dataType = dataType,
                startTime = startDate,
                endTime = endDate,
                buckets = if (statistics.isNotEmpty()) {
                    listOf(StatisticBucket(
                        startTime = startDate,
                        endTime = endDate,
                        statistics = statistics
                    ))
                } else {
                    emptyList()
                }
            ))
        } catch (e: Exception) {
            Result.failure(HealthConnectorException.DataAccessError(
                "Failed to read statistics for $dataType: ${e.message}"
            ))
        }
    }

    /**
     * Map Health Connect sleep stage to our common sleep stage type
     */
    private fun mapSleepStageType(stage: Int): SleepStageType {
        return when (stage) {
            SleepSessionRecord.STAGE_TYPE_AWAKE, 
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStageType.AWAKE
            SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.LIGHT
            SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
            SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
            SleepSessionRecord.STAGE_TYPE_SLEEPING,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
            SleepSessionRecord.STAGE_TYPE_UNKNOWN -> SleepStageType.UNKNOWN
            else -> SleepStageType.UNKNOWN
        }
    }


    private fun validateWorkoutData(workout: WorkoutData): Boolean {
        return (workout.duration?.isPositive() == true || workout.endTime != null) &&
               (workout.endTime == null || workout.startTime < workout.endTime)
    }

    private fun WorkoutType.toHealthConnectExerciseType(): Int {
        return when (this) {
            WorkoutType.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            WorkoutType.WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            WorkoutType.CYCLING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            WorkoutType.SWIMMING -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
            WorkoutType.STRENGTH_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
            WorkoutType.YOGA -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
            WorkoutType.DANCE -> ExerciseSessionRecord.EXERCISE_TYPE_DANCING
            WorkoutType.HIKING -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
            WorkoutType.ROWING -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
            WorkoutType.ELLIPTICAL -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
            WorkoutType.PILATES -> ExerciseSessionRecord.EXERCISE_TYPE_PILATES
            WorkoutType.HIGH_INTENSITY_INTERVAL_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
            WorkoutType.STAIR_CLIMBING -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING
            WorkoutType.MARTIAL_ARTS -> ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS
            WorkoutType.SKIING -> ExerciseSessionRecord.EXERCISE_TYPE_SKIING
            WorkoutType.SNOWBOARDING -> ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING
            WorkoutType.SKATING -> ExerciseSessionRecord.EXERCISE_TYPE_SKATING
            WorkoutType.TENNIS -> ExerciseSessionRecord.EXERCISE_TYPE_TENNIS
            WorkoutType.BASKETBALL -> ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL
            WorkoutType.SOCCER -> ExerciseSessionRecord.EXERCISE_TYPE_SOCCER
            WorkoutType.GOLF -> ExerciseSessionRecord.EXERCISE_TYPE_GOLF
            WorkoutType.OTHER -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
            else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }
    }
    
    /**
     * Convert workout type to Health Connect segment type
     */
    private fun WorkoutType.toHealthConnectSegmentType(): Int {
        // Map to the same exercise types for segments
        return this.toHealthConnectExerciseType()
    }
    
    // Individual write methods for each data type
    private suspend fun writeHeartRateData(data: HeartRateData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant)
            val record = HeartRateRecord(
                startTime = instant,
                endTime = instant,
                startZoneOffset = zoneOffset,
                endZoneOffset = zoneOffset,
                samples = listOf(
                    HeartRateRecord.Sample(
                        time = instant,
                        beatsPerMinute = data.bpm.toLong()
                    )
                ),
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeStepsData(data: StepsData): Result<Unit> {
        return try {
            // Use timestamp as fallback for start/end times if not provided
            val startInstant = data.startTime?.toJavaInstant() ?: data.timestamp.toJavaInstant()
            val endInstant = data.endTime?.toJavaInstant() ?: startInstant
            val record = StepsRecord(
                startTime = startInstant,
                endTime = endInstant,
                startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(startInstant),
                endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(endInstant),
                count = data.count.toLong(),
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeCalorieData(data: CalorieData): Result<Unit> {
        return try {
            val records = mutableListOf<Record>()

            val instant = data.timestamp.toJavaInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant)

            // Write active calories if available
            if (data.activeCalories > 0) {
                records.add(
                    ActiveCaloriesBurnedRecord(
                        startTime = instant,
                        endTime = instant,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        energy = androidx.health.connect.client.units.Energy.kilocalories(data.activeCalories),
                        metadata = createMetadata()
                    )
                )
            }

            // Write total calories if explicitly provided
            // Calculate total if both active and basal are available
            if (data.activeCalories > 0 && data.basalCalories != null && data.basalCalories > 0) {
                val total = data.activeCalories + data.basalCalories
                records.add(
                    TotalCaloriesBurnedRecord(
                        startTime = instant,
                        endTime = instant,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        energy = androidx.health.connect.client.units.Energy.kilocalories(total),
                        metadata = createMetadata()
                    )
                )
            }

            // Write basal calories if available
            data.basalCalories?.let { basal ->
                if (basal > 0) {
                    records.add(
                        BasalMetabolicRateRecord(
                            time = instant,
                            zoneOffset = zoneOffset,
                            basalMetabolicRate = androidx.health.connect.client.units.Power.kilocaloriesPerDay(basal * 24),
                            metadata = createMetadata()
                        )
                    )
                }
            }

            if (records.isNotEmpty()) {
                healthConnectClient.insertRecords(records)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeSleepData(data: SleepData): Result<Unit> {
        return try {
            val startInstant = data.startTime.toJavaInstant()
            val endInstant = data.endTime.toJavaInstant()

            // Map sleep stages to Health Connect stages
            val stages = data.stages.map { stage ->
                SleepSessionRecord.Stage(
                    startTime = stage.startTime.toJavaInstant(),
                    endTime = stage.endTime.toJavaInstant(),
                    stage = when (stage.stage) {
                        SleepStageType.AWAKE -> SleepSessionRecord.STAGE_TYPE_AWAKE
                        SleepStageType.LIGHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
                        SleepStageType.DEEP -> SleepSessionRecord.STAGE_TYPE_DEEP
                        SleepStageType.REM -> SleepSessionRecord.STAGE_TYPE_REM
                        SleepStageType.UNKNOWN -> SleepSessionRecord.STAGE_TYPE_UNKNOWN
                    }
                )
            }

            val record = SleepSessionRecord(
                startTime = startInstant,
                endTime = endInstant,
                startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(startInstant),
                endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(endInstant),
                stages = stages,
                title = data.metadata["title"] as? String,
                notes = data.metadata["notes"] as? String,
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeBodyMeasurements(data: BodyMeasurements): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant)
            val records = mutableListOf<Record>()

            // Write weight if available
            if (data.weight != null && data.weightUnit != null) {
                val weight = when (data.weightUnit) {
                    WeightUnit.KILOGRAMS -> androidx.health.connect.client.units.Mass.kilograms(data.weight)
                    WeightUnit.POUNDS -> androidx.health.connect.client.units.Mass.pounds(data.weight)
                    WeightUnit.STONES -> androidx.health.connect.client.units.Mass.pounds(data.weight * 14)
                }

                records.add(
                    WeightRecord(
                        time = instant,
                        zoneOffset = zoneOffset,
                        weight = weight,
                        metadata = createMetadata()
                    )
                )
            }

            // Write height if available
            data.height?.let { heightInMeters ->
                records.add(
                    HeightRecord(
                        time = instant,
                        zoneOffset = zoneOffset,
                        height = androidx.health.connect.client.units.Length.meters(heightInMeters),
                        metadata = createMetadata()
                    )
                )
            }

            // Write body fat percentage if available
            data.bodyFatPercentage?.let { percentage ->
                records.add(
                    BodyFatRecord(
                        time = instant,
                        zoneOffset = zoneOffset,
                        percentage = androidx.health.connect.client.units.Percentage(percentage),
                        metadata = createMetadata()
                    )
                )
            }

            // Write lean body mass if available
            if (data.leanBodyMass != null && data.leanBodyMassUnit != null) {
                val mass = when (data.leanBodyMassUnit) {
                    WeightUnit.KILOGRAMS -> androidx.health.connect.client.units.Mass.kilograms(data.leanBodyMass)
                    WeightUnit.POUNDS -> androidx.health.connect.client.units.Mass.pounds(data.leanBodyMass)
                    WeightUnit.STONES -> androidx.health.connect.client.units.Mass.pounds(data.leanBodyMass * 14)
                }

                records.add(
                    LeanBodyMassRecord(
                        time = instant,
                        zoneOffset = zoneOffset,
                        mass = mass,
                        metadata = createMetadata()
                    )
                )
            }

            if (records.isNotEmpty()) {
                healthConnectClient.insertRecords(records)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeBloodPressureData(data: BloodPressureData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val record = BloodPressureRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                systolic = androidx.health.connect.client.units.Pressure.millimetersOfMercury(data.systolic.toDouble()),
                diastolic = androidx.health.connect.client.units.Pressure.millimetersOfMercury(data.diastolic.toDouble()),
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeGlucoseData(data: GlucoseData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val record = BloodGlucoseRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                level = androidx.health.connect.client.units.BloodGlucose.milligramsPerDeciliter(data.toMgDl()),
                specimenSource = when (data.specimenSource) {
                    SpecimenSource.CAPILLARY_BLOOD -> BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD
                    SpecimenSource.PLASMA -> BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA
                    SpecimenSource.SERUM -> BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM
                    SpecimenSource.WHOLE_BLOOD -> BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD
                    SpecimenSource.INTERSTITIAL_FLUID -> BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID
                    else -> BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN
                },
                mealType = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                relationToMeal = when (data.mealRelation) {
                    MealRelation.BEFORE_MEAL -> BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
                    MealRelation.AFTER_MEAL -> BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
                    MealRelation.FASTING -> BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
                    else -> BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
                },
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    private suspend fun writeRespiratoryRateData(data: RespiratoryRateData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val record = RespiratoryRateRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                rate = data.breathsPerMinute,
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeOxygenSaturationData(data: OxygenSaturationData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val record = OxygenSaturationRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                percentage = androidx.health.connect.client.units.Percentage(data.percentage),
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeBodyTemperatureData(data: BodyTemperatureData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val temp = when (data.unit) {
                TemperatureUnit.CELSIUS -> androidx.health.connect.client.units.Temperature.celsius(data.temperature)
                TemperatureUnit.FAHRENHEIT -> androidx.health.connect.client.units.Temperature.fahrenheit(data.temperature)
                TemperatureUnit.KELVIN -> androidx.health.connect.client.units.Temperature.celsius(data.temperature - 273.15)
            }

            val record = BodyTemperatureRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                temperature = temp,
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    private suspend fun writeHydrationData(data: HydrationData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant)
            val volume = androidx.health.connect.client.units.Volume.milliliters(data.volume)

            val record = HydrationRecord(
                startTime = instant,
                endTime = instant,
                startZoneOffset = zoneOffset,
                endZoneOffset = zoneOffset,
                volume = volume,
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeNutritionData(data: NutritionData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant)
            val record = NutritionRecord(
                startTime = instant,
                endTime = instant,
                startZoneOffset = zoneOffset,
                endZoneOffset = zoneOffset,
                energy = data.calories?.let { androidx.health.connect.client.units.Energy.kilocalories(it) },
                protein = data.protein?.let { androidx.health.connect.client.units.Mass.grams(it) },
                totalCarbohydrate = data.carbohydrates?.let { androidx.health.connect.client.units.Mass.grams(it) },
                totalFat = data.fat?.let { androidx.health.connect.client.units.Mass.grams(it) },
                saturatedFat = data.saturatedFat?.let { androidx.health.connect.client.units.Mass.grams(it) },
                sugar = data.sugar?.let { androidx.health.connect.client.units.Mass.grams(it) },
                dietaryFiber = data.fiber?.let { androidx.health.connect.client.units.Mass.grams(it) },
                sodium = data.sodium?.let { androidx.health.connect.client.units.Mass.milligrams(it) },
                name = data.metadata["mealName"] as? String,
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeRestingHeartRateData(data: RestingHeartRateData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val record = RestingHeartRateRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                beatsPerMinute = data.bpm.toLong(),
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeSpeedData(data: SpeedData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant)
            val record = SpeedRecord(
                startTime = instant,
                endTime = instant,
                startZoneOffset = zoneOffset,
                endZoneOffset = zoneOffset,
                samples = listOf(
                    SpeedRecord.Sample(
                        time = instant,
                        speed = androidx.health.connect.client.units.Velocity.metersPerSecond(data.speedMetersPerSecond)
                    )
                ),
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writePowerData(data: PowerData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant)
            val record = PowerRecord(
                startTime = instant,
                endTime = instant,
                startZoneOffset = zoneOffset,
                endZoneOffset = zoneOffset,
                samples = listOf(
                    PowerRecord.Sample(
                        time = instant,
                        power = androidx.health.connect.client.units.Power.watts(data.watts)
                    )
                ),
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeCadenceData(data: CyclingCadenceData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant)
            val record = CyclingPedalingCadenceRecord(
                startTime = instant,
                endTime = instant,
                startZoneOffset = zoneOffset,
                endZoneOffset = zoneOffset,
                samples = listOf(
                    CyclingPedalingCadenceRecord.Sample(
                        time = instant,
                        revolutionsPerMinute = data.rpm.toDouble()
                    )
                ),
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeWheelchairPushData(data: WheelchairPushesData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val duration = data.duration ?: Duration.ZERO
            val endInstant = (data.timestamp + duration).toJavaInstant()
            val startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant)
            val endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(endInstant)

            val record = WheelchairPushesRecord(
                startTime = instant,
                endTime = endInstant,
                startZoneOffset = startZoneOffset,
                endZoneOffset = endZoneOffset,
                count = data.pushCount.toLong(),
                metadata = createMetadata()
            )

            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeMenstruationFlowData(data: MenstruationFlowData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val record = MenstruationFlowRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                flow = when (data.flow) {
                    FlowLevel.LIGHT -> MenstruationFlowRecord.FLOW_LIGHT
                    FlowLevel.MEDIUM -> MenstruationFlowRecord.FLOW_MEDIUM
                    FlowLevel.HEAVY -> MenstruationFlowRecord.FLOW_HEAVY
                    FlowLevel.SPOTTING -> MenstruationFlowRecord.FLOW_LIGHT
                    FlowLevel.UNSPECIFIED -> MenstruationFlowRecord.FLOW_LIGHT
                },
                metadata = createMetadata()
            )
            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeOvulationTestData(data: OvulationTestData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val record = OvulationTestRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                result = when (data.result) {
                    OvulationTestResult.POSITIVE -> OvulationTestRecord.RESULT_POSITIVE
                    OvulationTestResult.NEGATIVE -> OvulationTestRecord.RESULT_NEGATIVE
                    OvulationTestResult.INDETERMINATE -> OvulationTestRecord.RESULT_INCONCLUSIVE
                },
                metadata = createMetadata()
            )
            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeSexualActivityData(data: SexualActivityData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val record = SexualActivityRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                protectionUsed = when (data.protectionUsed) {
                    true -> 1  // PROTECTION_USED_PROTECTED
                    false -> 0 // PROTECTION_USED_UNPROTECTED  
                    null -> 2  // PROTECTION_USED_UNKNOWN
                },
                metadata = createMetadata()
            )
            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeCervicalMucusData(data: CervicalMucusData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val record = CervicalMucusRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                sensation = when (data.quality) {
                    CervicalMucusQuality.DRY -> CervicalMucusRecord.SENSATION_LIGHT
                    CervicalMucusQuality.STICKY -> CervicalMucusRecord.SENSATION_MEDIUM
                    CervicalMucusQuality.CREAMY -> CervicalMucusRecord.SENSATION_MEDIUM
                    CervicalMucusQuality.WATERY -> CervicalMucusRecord.SENSATION_HEAVY
                    CervicalMucusQuality.EGG_WHITE -> CervicalMucusRecord.SENSATION_HEAVY
                },
                metadata = createMetadata()
            )
            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeIntermenstrualBleedingData(data: IntermenstrualBleedingData): Result<Unit> {
        return try {
            val instant = data.timestamp.toJavaInstant()
            val record = IntermenstrualBleedingRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                metadata = createMetadata()
            )
            healthConnectClient.insertRecords(listOf(record))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get device capabilities for Health Connect
     */
    fun getCapabilities(): DeviceCapabilities {
        return HealthConnectCapabilities.getDeviceCapabilities(context)
    }

    /**
     * Check if a specific data type is supported on this device
     */
    fun isDataTypeSupported(dataType: HealthDataType): Boolean {
        return HealthConnectCapabilities.isDataTypeSupported(dataType)
    }

    /**
     * Check if a specific feature is available
     */
    fun isFeatureAvailable(feature: HealthConnectFeature): Boolean {
        return HealthConnectCapabilities.isFeatureAvailable(feature)
    }

    /**
     * Get enhanced metadata for records if available
     */
    private fun extractEnhancedMetadata(record: Record): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()

        // Basic metadata available for all records
        record.metadata.let { meta ->
            meta.id.let { metadata["recordId"] = it }
            meta.dataOrigin.packageName.let { metadata["sourceApp"] = it }
            meta.lastModifiedTime.let { metadata["lastModified"] = it.toKotlinInstant().toString() }
            meta.clientRecordId?.let { metadata["clientRecordId"] = it }
            meta.clientRecordVersion.let { metadata["clientRecordVersion"] = it }
            meta.recordingMethod.let { metadata["recordingMethod"] = it }
        }

        if (Build.VERSION.SDK_INT >= 30) {
            record.metadata.device?.let { device ->
                metadata["deviceManufacturer"] = device.manufacturer ?: "Unknown"
                metadata["deviceModel"] = device.model ?: "Unknown"
                device.type.let { metadata["deviceType"] = it }
            }
        }

        // Record-specific enhanced metadata
        when (record) {
            is ExerciseSessionRecord -> {
                // Exercise route functionality may require additional API
                if (Build.VERSION.SDK_INT >= 29) {
                    // Route data would be available through additional queries if supported
                    metadata["hasRoute"] = false
                }
                record.exerciseType.let { metadata["exerciseType"] = it }
                record.title?.let { metadata["title"] = it }
                record.notes?.let { metadata["notes"] = it }
            }
            is HeartRateRecord -> {
                metadata["sampleCount"] = record.samples.size
                if (Build.VERSION.SDK_INT >= 30) {
                    // Enhanced heart rate metadata
                    val minBpm = record.samples.minOfOrNull { it.beatsPerMinute }
                    val maxBpm = record.samples.maxOfOrNull { it.beatsPerMinute }
                    minBpm?.let { metadata["minBpm"] = it }
                    maxBpm?.let { metadata["maxBpm"] = it }
                }
            }
            is SleepSessionRecord -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    record.stages.size.let { metadata["stageCount"] = it }
                    record.title?.let { metadata["title"] = it }
                    record.notes?.let { metadata["notes"] = it }
                }
            }
        }

        return metadata
    }

    /**
     * Check if medical records are supported
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun isMedicalRecordsSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            medicalRecordsConnector?.isMedicalRecordsSupported() ?: false
        } else {
            false
        }
    }

    /**
     * Get medical records connector for advanced operations
     * 
     * @return The medical records connector if supported, null otherwise
     */
    fun getMedicalRecordsConnector(): HealthConnectMedicalRecords? {
        return medicalRecordsConnector
    }

    /**
     * Read clinical records with automatic FHIR mapping
     * 
     * This provides a unified interface for reading medical records.
     * Data is automatically mapped to FHIR resources when available.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    suspend fun readClinicalRecords(
        dataType: HealthDataType,
        startTime: Instant = Clock.System.now() - 365.days,
        endTime: Instant = Clock.System.now()
    ): Result<List<Any>> {
        if (!isMedicalRecordsSupported()) {
            return Result.failure(
                HealthConnectorException.UnsupportedFeature(
                    "Medical records require Android 16+ (API 35+). Current API: ${Build.VERSION.SDK_INT}"
                )
            )
        }

        val medicalConnector = medicalRecordsConnector ?: return Result.failure(
            HealthConnectorException.InitializationError("Medical records connector not initialized")
        )

        return when (dataType) {
            HealthDataType.ClinicalAllergies -> medicalConnector.readAllergies(startTime, endTime)
            HealthDataType.ClinicalConditions -> medicalConnector.readConditions(startTime, endTime)
            HealthDataType.ClinicalImmunizations -> medicalConnector.readImmunizations(startTime, endTime)
            HealthDataType.ClinicalMedications -> medicalConnector.readMedications()
            HealthDataType.ClinicalLabResults -> medicalConnector.readLabResults(startTime, endTime)
            HealthDataType.ClinicalProcedures -> medicalConnector.readProcedures(startTime, endTime)
            HealthDataType.ClinicalVitalSigns -> {
                // Map existing vital signs to FHIR Observations
                readVitalSignsAsFhir(startTime, endTime)
            }
            else -> Result.failure(
                HealthConnectorException.UnsupportedFeature(
                    "Data type $dataType is not a clinical record type"
                )
            )
        }
    }

    /**
     * Map existing vital signs data to FHIR Observations
     * 
     * This demonstrates how existing Health Connect data can be represented
     * as FHIR resources for compatibility with medical record systems.
     */
    private suspend fun readVitalSignsAsFhir(
        startTime: Instant,
        endTime: Instant
    ): Result<List<Any>> {
        return try {
            medicalRecordsConnector?.readLabResults(startTime, endTime)
                ?: Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(
                HealthConnectorException.DataAccessError(
                    "Failed to read vital signs as FHIR: ${e.message}"
                )
            )
        }
    }
}