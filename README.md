# Vitality

[![Build](https://github.com/crowded-libs/vitality/actions/workflows/build.yml/badge.svg)](https://github.com/crowded-libs/vitality/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.crowded-libs/vitality.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.crowded-libs%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

A Kotlin Multiplatform library providing unified health data access across Apple HealthKit (iOS) and Health Connect (Android). Vitality offers comprehensive health metrics, **real-time** monitoring via Kotlin flows, workout management, and medical records support through FHIR standards.

## Features

- 🏥 **66 Health Data Types** - Comprehensive coverage of fitness, vitals, body measurements, nutrition, sleep, and more
- 📱 **Cross-Platform** - Single API for both iOS HealthKit and Android Health Connect
- 🔄 **Real-time Monitoring** - Flow-based reactive streams for continuous health data updates
- 🏃 **Workout Sessions** - Live workout tracking with pause/resume, segments, laps, and route support
- 📊 **Statistical Queries** - Aggregate data with time-bucketed statistics
- 🏥 **FHIR Medical Records** - Standard-compliant medical data modeling
- 🔐 **Granular Permissions** - Fine-grained control over health data access

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.crowded-libs:vitality:0.1.0")
}
```

### Platform Requirements

- **iOS**: iOS 13.0+, HealthKit capability enabled
- **Android**: API 26+ (Health Connect support), API 34+ (full features)

## Quick Start

### 1. Platform Setup

#### Android
Add to your `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Required: Initialize the context provider
        HealthConnectorContextProvider.initialize(applicationContext)
    }
}
```

Add to `AndroidManifest.xml`:
```xml
<!-- Health Connect permissions -->
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
<uses-permission android:name="android.permission.health.WRITE_HEART_RATE" />
<!-- Add other permissions as needed -->

<!-- For real-time monitoring -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
```

#### iOS
Enable HealthKit capability in Xcode and add to `Info.plist`:
```xml
<key>NSHealthShareUsageDescription</key>
<string>This app reads your health data to provide insights</string>
<key>NSHealthUpdateUsageDescription</key>
<string>This app updates your health data from workouts</string>
```

### 2. Basic Usage

```kotlin
import vitality.*
import vitality.models.*
import kotlinx.coroutines.*

// Create the health connector
val healthConnector = createHealthConnector()

// Initialize and check availability
suspend fun setupHealthAccess() {
    val result = healthConnector.initialize()
    result.fold(
        onSuccess = {
            println("Health connector initialized successfully")
            
            // Check available data types
            val availableTypes = healthConnector.getAvailableDataTypes()
            println("Available data types: ${availableTypes.size}")
            
            // Check platform capabilities
            val capabilities = healthConnector.getPlatformCapabilities()
            println("Platform: ${capabilities.platformName}")
        },
        onFailure = { exception ->
            println("Initialization failed: $exception")
        }
    )
}

// Request permissions
suspend fun requestPermissions() {
    val permissions = setOf(
        HealthPermission(HealthDataType.HeartRate, HealthPermission.AccessType.READ),
        HealthPermission(HealthDataType.Steps, HealthPermission.AccessType.READ),
        HealthPermission(HealthDataType.Calories, HealthPermission.AccessType.WRITE)
    )
    
    val result = healthConnector.requestPermissions(permissions)
    result.fold(
        onSuccess = { permissionResult ->
            permissionResult.granted.forEach { 
                println("Granted: ${it.dataType} - ${it.accessType}")
            }
            permissionResult.denied.forEach {
                println("Denied: ${it.dataType} - ${it.accessType}")
            }
        },
        onFailure = { exception ->
            println("Permission request failed: $exception")
        }
    )
}

// Read health data - convenience methods
suspend fun readTodaysData() {
    // Read today's steps
    val stepsResult = healthConnector.readStepsToday()
    stepsResult.fold(
        onSuccess = { steps ->
            println("Steps today: ${steps.count}")
        },
        onFailure = { exception ->
            println("Failed to read steps: $exception")
        }
    )
    
    // Read latest heart rate
    val heartRateResult = healthConnector.readLatestHeartRate()
    heartRateResult.fold(
        onSuccess = { heartRate ->
            heartRate?.let {
                println("Latest heart rate: ${it.bpm} bpm")
            } ?: println("No heart rate data available")
        },
        onFailure = { exception ->
            println("Failed to read heart rate: $exception")
        }
    )
}

// Read historical data
suspend fun readHistoricalData() {
    val endTime = Clock.System.now()
    val startTime = endTime.minus(7.days)
    
    val result = healthConnector.readHealthData(
        dataType = HealthDataType.HeartRate,
        startDate = startTime,
        endDate = endTime
    )
    
    result.fold(
        onSuccess = { dataPoints ->
            dataPoints.forEach { dataPoint ->
                println("Health data: ${dataPoint.value} ${dataPoint.unit} at ${dataPoint.timestamp}")
            }
        },
        onFailure = { exception ->
            println("Failed to read health data: $exception")
        }
    )
}

// Real-time monitoring with Flow
fun monitorHealthData() {
    // Monitor steps with default update interval
    val stepsFlow: Flow<StepsData> = healthConnector.observe(HealthDataType.Steps)
    stepsFlow
        .onEach { steps ->
            println("Steps updated: ${steps.count}")
        }
        .launchIn(GlobalScope)
    
    // Monitor heart rate with custom sampling interval
    val heartRateFlow: Flow<HeartRateData> = healthConnector.observe(
        HealthDataType.HeartRate, 
        samplingInterval = 5.seconds
    )
    heartRateFlow
        .onEach { heartRate ->
            println("Heart rate: ${heartRate.bpm} bpm")
        }
        .launchIn(GlobalScope)
}
```

## HealthConnector Interface

The `HealthConnector` interface is the main entry point for all health data operations:

```kotlin
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
    fun <T> observe(
        dataType: HealthDataType,
        samplingInterval: Duration = 30.seconds
    ): Flow<T>
    fun observeActiveWorkout(): Flow<WorkoutData>
    
    // Workout session management
    suspend fun startWorkoutSession(
        workoutType: WorkoutType,
        title: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ): Result<WorkoutSession>
    suspend fun startWorkoutSession(
        configuration: WorkoutConfiguration,
        title: String? = null
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
    suspend fun readImmunizations(
        startDate: Instant? = null,
        endDate: Instant? = null
    ): Result<List<FHIRImmunization>>
    
    suspend fun readMedications(
        startDate: Instant? = null,
        endDate: Instant? = null
    ): Result<List<FHIRResource>>
    
    suspend fun readAllergies(
        includeInactive: Boolean = false
    ): Result<List<FHIRAllergyIntolerance>>
    
    suspend fun readConditions(
        includeResolved: Boolean = false
    ): Result<List<FHIRCondition>>
    
    suspend fun readLabResults(
        startDate: Instant? = null,
        endDate: Instant? = null,
        category: String? = null
    ): Result<List<FHIRObservation>>
    
    suspend fun readProcedures(
        startDate: Instant? = null,
        endDate: Instant? = null
    ): Result<List<FHIRProcedure>>
    
    suspend fun areClinicalRecordsAvailable(): Boolean
}
```

## Platform Implementations

### iOS - HealthKitConnector

Direct integration with Apple HealthKit:

```kotlin
val connector = createHealthConnector() // Automatically creates HealthKitConnector on iOS

// Check platform capabilities
val capabilities = connector.getPlatformCapabilities()
println("Running on: ${capabilities.platformName}")
println("Version: ${capabilities.platformVersion}")
println("Supports background delivery: ${capabilities.supportsBackgroundDelivery}")
println("Supports workout routes: ${capabilities.supportsWorkoutRoutes}")

// iOS-specific features - background delivery
val heartRateFlow: Flow<HeartRateData> = connector.observe(HealthDataType.HeartRate)
heartRateFlow
    .collect { heartRate ->
        // Will deliver updates even when app is in background on iOS
        println("Background HR update: ${heartRate.bpm}")
    }

// Check for Apple Watch
if (capabilities.hasWearableDevice) {
    println("Apple Watch connected")
}
```

### Android - HealthConnectConnector

Integration with Android Health Connect:

```kotlin
val connector = createHealthConnector() // Automatically creates HealthConnectConnector on Android

// Android requires initialization with context (done in Application.onCreate)
// Check platform capabilities
val capabilities = connector.getPlatformCapabilities()
println("Health Connect version: ${capabilities.platformVersion}")

// Real-time monitoring uses polling on Android
// Configure polling intervals via platform-specific configuration
val stepsFlow: Flow<StepsData> = connector.observe(HealthDataType.Steps)
stepsFlow
    .collect { steps ->
        // Updates based on polling interval (default: 30 seconds)
        println("Steps update: ${steps.count}")
    }

// For workout sessions with more frequent updates
connector.observeActiveWorkout()
    .collect { workout ->
        // More frequent polling during active workouts
        println("Workout distance: ${workout.totalDistance} meters")
    }
```

## Data Types Mapping Matrix

Below is a comprehensive mapping of all Vitality data types to their platform equivalents:

### Fitness Data

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `Steps` | `HKQuantityTypeIdentifierStepCount` | `StepsRecord` | Count of steps |
| `Distance` | `HKQuantityTypeIdentifierDistanceWalkingRunning` | `DistanceRecord` | Distance in meters |
| `Calories` | `HKQuantityTypeIdentifierActiveEnergyBurned` | `TotalCaloriesBurnedRecord` | Total calories |
| `ActiveCalories` | `HKQuantityTypeIdentifierActiveEnergyBurned` | `ActiveCaloriesBurnedRecord` | Active calories only |
| `BasalCalories` | `HKQuantityTypeIdentifierBasalEnergyBurned` | `BasalMetabolicRateRecord` | Resting calories |
| `Floors` | `HKQuantityTypeIdentifierFlightsClimbed` | ❌ Not supported | iOS only |
| `Elevation` | ❌ Not supported | `ElevationGainedRecord` | Android only |
| `Workout` | `HKWorkoutType` | `ExerciseSessionRecord` | Workout sessions |
| `VO2Max` | `HKQuantityTypeIdentifierVO2Max` | `Vo2MaxRecord` | Aerobic fitness |
| `WheelchairPushes` | ❌ Not supported | `WheelchairPushesRecord` | Android only |

### Vital Signs

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `HeartRate` | `HKQuantityTypeIdentifierHeartRate` | `HeartRateRecord` | Beats per minute |
| `HeartRateVariability` | `HKQuantityTypeIdentifierHeartRateVariabilitySDNN` | `HeartRateVariabilityRmssdRecord` | SDNN on iOS, RMSSD on Android |
| `RestingHeartRate` | `HKQuantityTypeIdentifierRestingHeartRate` | `RestingHeartRateRecord` | iOS 11+ |
| `BloodPressure` | `HKCorrelationTypeIdentifierBloodPressure` | `BloodPressureRecord` | Systolic/Diastolic |
| `RespiratoryRate` | `HKQuantityTypeIdentifierRespiratoryRate` | `RespiratoryRateRecord` | Breaths per minute |
| `OxygenSaturation` | `HKQuantityTypeIdentifierOxygenSaturation` | `OxygenSaturationRecord` | SpO2 percentage |
| `BodyTemperature` | `HKQuantityTypeIdentifierBodyTemperature` | `BodyTemperatureRecord` | Core temperature |
| `BloodGlucose` | `HKQuantityTypeIdentifierBloodGlucose` | `BloodGlucoseRecord` | mg/dL or mmol/L |

### Body Measurements

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `Weight` | `HKQuantityTypeIdentifierBodyMass` | `WeightRecord` | Kilograms |
| `Height` | `HKQuantityTypeIdentifierHeight` | `HeightRecord` | Meters |
| `BMI` | `HKQuantityTypeIdentifierBodyMassIndex` | ❌ Calculate from height/weight | kg/m² |
| `BodyFat` | `HKQuantityTypeIdentifierBodyFatPercentage` | `BodyFatRecord` | Percentage |
| `LeanBodyMass` | `HKQuantityTypeIdentifierLeanBodyMass` | `LeanBodyMassRecord` | Kilograms |

### Nutrition

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `Water` | `HKQuantityTypeIdentifierDietaryWater` | `HydrationRecord` | Milliliters |
| `Protein` | `HKQuantityTypeIdentifierDietaryProtein` | `NutritionRecord.protein` | Grams |
| `Carbohydrates` | `HKQuantityTypeIdentifierDietaryCarbohydrates` | `NutritionRecord.totalCarbohydrate` | Grams |
| `Fat` | `HKQuantityTypeIdentifierDietaryFatTotal` | `NutritionRecord.totalFat` | Grams |
| `Fiber` | `HKQuantityTypeIdentifierDietaryFiber` | `NutritionRecord.dietaryFiber` | Grams |
| `Sugar` | `HKQuantityTypeIdentifierDietarySugar` | `NutritionRecord.sugar` | Grams |
| `Caffeine` | `HKQuantityTypeIdentifierDietaryCaffeine` | `NutritionRecord.caffeine` | Milligrams |

### Sleep

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `Sleep` | `HKCategoryTypeIdentifierSleepAnalysis` | `SleepSessionRecord` | Sleep sessions |
| `SleepStages` | Within sleep analysis | `SleepSessionRecord.Stage` | REM, Deep, Light, Awake |

### Mobility & Movement

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `WalkingAsymmetry` | `HKQuantityTypeIdentifierWalkingAsymmetryPercentage` | ❌ Not supported | iOS 14+ only |
| `WalkingDoubleSupportPercentage` | `HKQuantityTypeIdentifierWalkingDoubleSupportPercentage` | ❌ Not supported | iOS 14+ only |
| `WalkingSpeed` | `HKQuantityTypeIdentifierWalkingSpeed` | ❌ Not supported | iOS only |
| `WalkingStepLength` | `HKQuantityTypeIdentifierWalkingStepLength` | ❌ Not supported | iOS only |
| `StairAscentSpeed` | `HKQuantityTypeIdentifierStairAscentSpeed` | ❌ Not supported | iOS 14+ only |
| `StairDescentSpeed` | `HKQuantityTypeIdentifierStairDescentSpeed` | ❌ Not supported | iOS 14+ only |
| `SixMinuteWalkTestDistance` | `HKQuantityTypeIdentifierSixMinuteWalkTestDistance` | ❌ Not supported | iOS only |
| `NumberOfTimesFallen` | `HKQuantityTypeIdentifierNumberOfTimesFallen` | ❌ Not supported | iOS only |
| `StandHours` | `HKCategoryTypeIdentifierAppleStandHour` | ❌ Not supported | iOS only |

### Advanced Fitness Metrics

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `Speed` | ❌ Not supported | `SpeedRecord` | Android only |
| `Power` | ❌ Not supported | `PowerRecord` | Android only |
| `RunningStrideLength` | `HKQuantityTypeIdentifierRunningStrideLength` | ❌ Not supported | iOS 16+ only |
| `RunningVerticalOscillation` | `HKQuantityTypeIdentifierRunningVerticalOscillation` | ❌ Not supported | iOS 16+ only |
| `RunningGroundContactTime` | `HKQuantityTypeIdentifierRunningGroundContactTime` | ❌ Not supported | iOS 16+ only |
| `CyclingCadence` | `HKQuantityTypeIdentifierCyclingCadence` | ❌ Not supported | iOS 17+ only |
| `CyclingPower` | `HKQuantityTypeIdentifierCyclingPower` | `PowerRecord` | iOS 17+ / Android |
| `CyclingPedalingCadence` | ❌ Not supported | `CyclingPedalingCadenceRecord` | Android only |
| `CyclingFunctionalThresholdPower` | `HKQuantityTypeIdentifierCyclingFunctionalThresholdPower` | ❌ Not supported | iOS 17+ only |
| `SwimmingStrokeStyle` | Stored as workout metadata | ❌ Not supported | Stroke type |

### Environmental & Audio

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `EnvironmentalAudioExposure` | `HKQuantityTypeIdentifierEnvironmentalAudioExposure` | ❌ Not supported | iOS only |
| `HeadphoneAudioExposure` | `HKQuantityTypeIdentifierHeadphoneAudioExposure` | ❌ Not supported | iOS only |
| `UVExposure` | `HKQuantityTypeIdentifierUVExposure` | ❌ Not supported | iOS only |
| `TimeInDaylight` | `HKQuantityTypeIdentifierTimeInDaylight` | ❌ Not supported | iOS 17+ only |

### Clinical Data

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `Electrocardiogram` | `HKElectrocardiogramType` | ❌ Not supported | iOS 14+ only |
| `IrregularHeartRhythmEvent` | `HKCategoryTypeIdentifierIrregularHeartRhythmEvent` | ❌ Not supported | iOS only |
| `PeripheralPerfusionIndex` | `HKQuantityTypeIdentifierPeripheralPerfusionIndex` | ❌ Not supported | iOS only |

### Reproductive Health

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `MenstruationFlow` | `HKCategoryTypeIdentifierMenstrualFlow` | `MenstruationFlowRecord` | iOS 9+ |
| `MenstruationPeriod` | ❌ Not supported | `MenstruationPeriodRecord` | Android only |
| `IntermenstrualBleeding` | `HKCategoryTypeIdentifierIntermenstrualBleeding` | `IntermenstrualBleedingRecord` | iOS 9+ |
| `CervicalMucus` | `HKCategoryTypeIdentifierCervicalMucusQuality` | `CervicalMucusRecord` | iOS 9+ |
| `OvulationTest` | `HKCategoryTypeIdentifierOvulationTestResult` | `OvulationTestRecord` | iOS 9+ |
| `SexualActivity` | `HKCategoryTypeIdentifierSexualActivity` | `SexualActivityRecord` | iOS 9+ |

### Other

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `Mindfulness` | `HKCategoryTypeIdentifierMindfulSession` | Supported via sessions | Both platforms |

### Clinical Records (FHIR)

| Vitality Type | HealthKit (iOS) | Health Connect (Android) | Notes |
|---------------|-----------------|-------------------------|-------|
| `ClinicalAllergies` | `HKClinicalTypeIdentifierAllergyRecord` | `MedicalResource` (AllergyIntolerance) | FHIR AllergyIntolerance |
| `ClinicalConditions` | `HKClinicalTypeIdentifierConditionRecord` | `MedicalResource` (Condition) | FHIR Condition |
| `ClinicalImmunizations` | `HKClinicalTypeIdentifierImmunizationRecord` | `MedicalResource` (Immunization) | FHIR Immunization |
| `ClinicalLabResults` | `HKClinicalTypeIdentifierLabResultRecord` | `MedicalResource` (Observation) | FHIR Observation |
| `ClinicalMedications` | `HKClinicalTypeIdentifierMedicationRecord` | `MedicalResource` (MedicationStatement) | FHIR MedicationStatement |
| `ClinicalProcedures` | `HKClinicalTypeIdentifierProcedureRecord` | `MedicalResource` (Procedure) | FHIR Procedure |
| `ClinicalVitalSigns` | `HKClinicalTypeIdentifierVitalSignRecord` | `MedicalResource` (Observation) | FHIR Observation |

### Platform-Specific Notes

#### iOS-Only Features
- **Mobility Metrics**: Walking asymmetry, double support, stair speeds (iOS 14+)
- **Advanced Running Metrics**: Stride length, vertical oscillation, ground contact time (iOS 16+)
- **Cycling Metrics**: Cadence, power, functional threshold (iOS 17+)
- **Environmental Sensing**: Audio exposure, UV exposure, time in daylight
- **Clinical Features**: ECG, irregular rhythm notifications
- **Apple Watch Integration**: Seamless data from paired devices

#### Android-Only Features
- **Reproductive Health**: Comprehensive menstruation and fertility tracking
- **Nutrition Details**: Granular macronutrient tracking via NutritionRecord
- **Activity Metrics**: Speed and power records
- **Session-Based Data**: Rich metadata for exercise sessions
- **Medical Records (Android 16+)**: Full FHIR R4 support via MedicalResource API

## Workout Types

Both platforms support these workout types:

```kotlin
enum class WorkoutType {
    RUNNING, WALKING, CYCLING, SWIMMING, STRENGTH_TRAINING,
    YOGA, PILATES, DANCE, MARTIAL_ARTS, ROWING, ELLIPTICAL,
    STAIR_CLIMBING, HIGH_INTENSITY_INTERVAL_TRAINING,
    FUNCTIONAL_TRAINING, CORE_TRAINING, CROSS_TRAINING,
    FLEXIBILITY, MIXED_CARDIO, SOCCER, BASKETBALL, TENNIS,
    GOLF, HIKING, SKIING, SNOWBOARDING, SKATING,
    SURFING, CLIMBING, EQUESTRIAN, FISHING, HUNTING,
    PLAY, MEDITATION, COOLDOWN, OTHER
}
```

### Workout Session Example

```kotlin
// Start a workout session
suspend fun startRunningWorkout() {
    // Method 1: Simple workout start
    val sessionResult = healthConnector.startWorkoutSession(
        workoutType = WorkoutType.RUNNING,
        title = "Morning Run",
        metadata = mapOf("surface" to "outdoor", "weather" to "sunny")
    )
    
    // Method 2: With configuration object
    val config = WorkoutConfiguration(
        type = WorkoutType.RUNNING,
        isIndoor = false,
        enableGpsTracking = true,
        metadata = mapOf("surface" to "track")
    )
    
    val result = healthConnector.startWorkoutSession(config)
    val session = result.getOrElse {
        println("Failed to start workout: $it")
        return
    }
    
    val sessionId = session.id
    val sessionStartTime = Clock.System.now()
    
    // Monitor real-time metrics during workout using Flow
    launch {
        healthConnector.observeActiveWorkout()
            .collect { workoutData ->
                println("Distance: ${workoutData.totalDistance} m")
                println("Calories: ${workoutData.totalCalories} kcal")
                println("Duration: ${workoutData.duration}")
            }
    }
    
    // Monitor heart rate separately
    launch {
        val heartRateFlow: Flow<HeartRateData> = healthConnector.observe(
            HealthDataType.HeartRate,
            samplingInterval = 5.seconds
        )
        heartRateFlow.collect { heartRate ->
            println("Current heart rate: ${heartRate.beatsPerMinute} bpm")
        }
    }
    
    // Workout control flow
    delay(5.minutes)
    
    // Pause workout
    healthConnector.pauseWorkoutSession(sessionId)
    println("Workout paused")
    
    delay(30.seconds)
    
    // Resume workout
    healthConnector.resumeWorkoutSession(sessionId)
    println("Workout resumed")
    
    delay(10.minutes)
    
    // End workout
    val endResult = healthConnector.endWorkoutSession(sessionId)
    endResult.fold(
        onSuccess = {
            println("Workout completed!")
            // Fetch workout data separately if you need summary statistics
            val workoutResult = healthConnector.readWorkouts(
                startDate = sessionStartTime,
                endDate = Clock.System.now()
            )
            workoutResult.getOrNull()?.firstOrNull()?.let { workout ->
                println("Duration: ${workout.duration}")
                println("Total calories: ${workout.totalCalories} kcal")
                println("Total distance: ${workout.totalDistance} meters")
            }
        },
        onFailure = { exception ->
            println("Failed to end workout: $exception")
            // Optionally discard the session
            healthConnector.discardWorkoutSession(sessionId)
        }
    )
}
```

## Medical Records (FHIR)

Vitality provides comprehensive FHIR R4 support for medical records:

```kotlin
// Check if clinical records are available
suspend fun checkClinicalRecordsSupport() {
    if (healthConnector.areClinicalRecordsAvailable()) {
        println("Clinical records are supported on this device")
    } else {
        println("Clinical records not available")
    }
}

// Read immunization records
suspend fun getVaccinationHistory() {
    val result = healthConnector.readImmunizations()
    result.fold(
        onSuccess = { immunizations ->
            immunizations.forEach { immunization ->
                println("Vaccine: ${immunization.vaccineCode.text}")
                println("Date: ${immunization.occurrenceDateTime}")
                println("Status: ${immunization.status}")
                println("Dose: ${immunization.doseQuantity?.value} ${immunization.doseQuantity?.unit}")
            }
        },
        onFailure = { exception ->
            println("Failed to read immunizations: $exception")
        }
    )
}

// Read medications with date filter
suspend fun getCurrentMedications() {
    val oneYearAgo = Clock.System.now().minus(365.days)
    
    val result = healthConnector.readMedications(startDate = oneYearAgo)
    result.fold(
        onSuccess = { medications ->
            medications.forEach { resource ->
                when (resource) {
                    is FHIRMedicationStatement -> {
                        println("Medication: ${resource.medicationCodeableConcept?.text}")
                        println("Status: ${resource.status}")
                        println("Dosage: ${resource.dosage.firstOrNull()?.text}")
                    }
                    is FHIRMedicationRequest -> {
                        println("Prescription: ${resource.medicationCodeableConcept?.text}")
                        println("Status: ${resource.status}")
                        println("Requester: ${resource.requester?.display}")
                    }
                }
            }
        },
        onFailure = { exception ->
            println("Failed to read medications: $exception")
        }
    )
}

// Read active allergies
suspend fun getActiveAllergies() {
    val result = healthConnector.readAllergies(includeInactive = false)
    result.fold(
        onSuccess = { allergies ->
            allergies.forEach { allergy ->
                println("Allergen: ${allergy.code?.text}")
                println("Type: ${allergy.type}")
                println("Category: ${allergy.category.joinToString()}")
                println("Criticality: ${allergy.criticality}")
                println("Reactions: ${allergy.reaction.joinToString { it.description ?: "Unknown" }}")
            }
        },
        onFailure = { exception ->
            println("Failed to read allergies: $exception")
        }
    )
}

// Read medical conditions
suspend fun getMedicalConditions() {
    val result = healthConnector.readConditions(includeResolved = false)
    result.fold(
        onSuccess = { conditions ->
            conditions.forEach { condition ->
                println("Condition: ${condition.code.text}")
                println("Clinical Status: ${condition.clinicalStatus?.text}")
                println("Verification: ${condition.verificationStatus?.text}")
                println("Onset: ${condition.onsetDateTime}")
                println("Severity: ${condition.severity?.text}")
            }
        },
        onFailure = { exception ->
            println("Failed to read conditions: $exception")
        }
    )
}

// Read lab results with category filter
suspend fun getLabResults() {
    val result = healthConnector.readLabResults(category = "laboratory")
    result.fold(
        onSuccess = { observations ->
            observations.forEach { observation ->
                println("Test: ${observation.code.text}")
                println("Value: ${observation.valueQuantity?.value} ${observation.valueQuantity?.unit}")
                println("Status: ${observation.status}")
                println("Date: ${observation.effectiveDateTime}")
                println("Reference Range: ${observation.referenceRange.firstOrNull()?.text}")
            }
        },
        onFailure = { exception ->
            println("Failed to read lab results: $exception")
        }
    )
}

// Read medical procedures
suspend fun getRecentProcedures() {
    val sixMonthsAgo = Clock.System.now().minus(180.days)
    
    val result = healthConnector.readProcedures(startDate = sixMonthsAgo)
    result.fold(
        onSuccess = { procedures ->
            procedures.forEach { procedure ->
                println("Procedure: ${procedure.code?.text}")
                println("Status: ${procedure.status}")
                println("Date: ${procedure.performedDateTime}")
                println("Outcome: ${procedure.outcome?.text}")
                println("Performer: ${procedure.performer.firstOrNull()?.actor?.display}")
            }
        },
        onFailure = { exception ->
            println("Failed to read procedures: $exception")
        }
    )
}
```

### Supported FHIR Resource Types

- `ALLERGY_INTOLERANCE` - Allergies and intolerances
- `CONDITION` - Medical conditions and diagnoses
- `IMMUNIZATION` - Vaccination records
- `MEDICATION` - Current medications
- `MEDICATION_REQUEST` - Prescriptions
- `MEDICATION_STATEMENT` - Medication usage history
- `OBSERVATION` - Lab results and vital signs
- `PROCEDURE` - Medical procedures
- `DIAGNOSTIC_REPORT` - Lab and imaging reports
- `DOCUMENT_REFERENCE` - Clinical documents

## Advanced Features

### Statistical Queries

```kotlin
// Get statistics for a single data type
suspend fun getWeeklyHeartRateStats() {
    val endTime = Clock.System.now()
    val startTime = endTime.minus(7.days)
    
    val result = healthConnector.readStatistics(
        dataType = HealthDataType.HeartRate,
        startDate = startTime,
        endDate = endTime,
        statisticOptions = setOf(
            StatisticOption.AVERAGE,
            StatisticOption.MIN,
            StatisticOption.MAX
        ),
        bucketDuration = 1.days
    )
    result.fold(
        onSuccess = { stats ->
            stats.dataPoints.forEach { dataPoint ->
                println("Date: ${dataPoint.startTime} - ${dataPoint.endTime}")
                println("Average HR: ${dataPoint.average} bpm")
                println("Min HR: ${dataPoint.min} bpm")
                println("Max HR: ${dataPoint.max} bpm")
            }
        },
        onFailure = { exception ->
            println("Failed to get statistics: $exception")
        }
    )
}

// Get statistics for multiple data types - call readStatistics for each type
suspend fun getDailyFitnessStats() {
    val today = Clock.System.now()
    val yesterday = today.minus(1.days)
    
    val dataTypes = setOf(
        HealthDataType.Steps,
        HealthDataType.Calories,
        HealthDataType.Distance,
        HealthDataType.HeartRate
    )
    
    dataTypes.forEach { dataType ->
        val result = healthConnector.readStatistics(
            dataType = dataType,
            startDate = yesterday,
            endDate = today,
            statisticOptions = when (dataType) {
                HealthDataType.HeartRate -> setOf(StatisticOption.AVERAGE)
                else -> setOf(StatisticOption.TOTAL)
            }
        )
        result.fold(
            onSuccess = { stats ->
                println("\n$dataType statistics:")
                when (dataType) {
                    HealthDataType.Steps, HealthDataType.Calories, HealthDataType.Distance -> {
                        println("Total: ${stats.total}")
                    }
                    HealthDataType.HeartRate -> {
                        println("Average: ${stats.average}")
                    }
                    else -> {
                        println("Data: $stats")
                    }
                }
            },
            onFailure = { exception ->
                println("Failed to get $dataType stats: $exception")
            }
        )
    }
}
```

### Platform-Specific Configuration

#### iOS Background Delivery

```kotlin
// iOS supports background delivery for health data
val connector = createHealthConnector()

// Check platform capabilities
val capabilities = connector.getPlatformCapabilities()
if (capabilities.supportsBackgroundDelivery) {
    // Heart rate updates will be delivered even when app is suspended
    val heartRateFlow: Flow<HeartRateData> = connector.observe(HealthDataType.HeartRate)
    heartRateFlow.collect { heartRate ->
        // Process in background
        if (heartRate.beatsPerMinute > 100 || heartRate.beatsPerMinute < 50) {
            sendNotification("Abnormal heart rate: ${heartRate.beatsPerMinute} bpm")
        }
    }
}

// Background delivery works for all Flow-based observers on iOS
val stepsFlow: Flow<StepsData> = connector.observe(HealthDataType.Steps)
stepsFlow.collect { steps ->
    // Will continue receiving updates in background
    updateStepWidget(steps.count)
}
```