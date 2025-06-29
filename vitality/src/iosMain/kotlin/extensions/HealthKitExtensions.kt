package vitality.extensions

import vitality.*
import vitality.models.*
import vitality.healthkit.*
import platform.HealthKit.*
import platform.Foundation.*
import platform.UIKit.UIDevice
import kotlin.time.Instant
import kotlinx.datetime.toKotlinInstant
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Extension functions for HealthKit type conversions
 */

internal fun HealthDataType.toHKQuantityType(): HKQuantityType? = when (this) {
    HealthDataType.Steps -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount)
    HealthDataType.Distance -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDistanceWalkingRunning)
    HealthDataType.Calories -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned)
    HealthDataType.ActiveCalories -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned)
    HealthDataType.BasalCalories -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBasalEnergyBurned)
    HealthDataType.Floors -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierFlightsClimbed)
    HealthDataType.HeartRate -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRate)
    HealthDataType.HeartRateVariability -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRateVariabilitySDNN)
    HealthDataType.OxygenSaturation -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierOxygenSaturation)
    HealthDataType.RespiratoryRate -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRespiratoryRate)
    HealthDataType.BodyTemperature -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyTemperature)
    HealthDataType.Weight -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMass)
    HealthDataType.Height -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeight)
    HealthDataType.BodyFat -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyFatPercentage)
    HealthDataType.BMI -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMassIndex)
    HealthDataType.LeanBodyMass -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierLeanBodyMass)
    HealthDataType.Water -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryWater)
    HealthDataType.BloodGlucose -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBloodGlucose)
    HealthDataType.VO2Max -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierVO2Max)
    HealthDataType.WalkingAsymmetry -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingAsymmetryPercentage)
    HealthDataType.WalkingDoubleSupportPercentage -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingDoubleSupportPercentage)
    HealthDataType.WalkingSpeed -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingSpeed)
    HealthDataType.WalkingStepLength -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingStepLength)
    HealthDataType.StairAscentSpeed -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStairAscentSpeed)
    HealthDataType.StairDescentSpeed -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStairDescentSpeed)
    HealthDataType.SixMinuteWalkTestDistance -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierSixMinuteWalkTestDistance)
    HealthDataType.NumberOfTimesFallen -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierNumberOfTimesFallen)
    HealthDataType.EnvironmentalAudioExposure -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierEnvironmentalAudioExposure)
    HealthDataType.HeadphoneAudioExposure -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeadphoneAudioExposure)
    HealthDataType.UVExposure -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierUVExposure)
    HealthDataType.RunningStrideLength -> if (isIOS16OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningStrideLength)
    } else { null }
    HealthDataType.RunningVerticalOscillation -> if (isIOS16OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningVerticalOscillation)
    } else { null }
    HealthDataType.RunningGroundContactTime -> if (isIOS16OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningGroundContactTime)
    } else { null }
    HealthDataType.CyclingCadence -> if (isIOS17OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingCadence)
    } else { null }
    HealthDataType.CyclingPower -> if (isIOS17OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingPower)
    } else { null }
    HealthDataType.CyclingFunctionalThresholdPower -> if (isIOS17OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingFunctionalThresholdPower)
    } else { null }
    HealthDataType.PeripheralPerfusionIndex -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierPeripheralPerfusionIndex)
    
    // Nutrition types
    HealthDataType.Protein -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryProtein)
    HealthDataType.Carbohydrates -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryCarbohydrates)
    HealthDataType.Fat -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryFatTotal)
    HealthDataType.Fiber -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryFiber)
    HealthDataType.Sugar -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietarySugar)
    HealthDataType.Caffeine -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryCaffeine)
    
    // Resting heart rate (iOS 11+)
    HealthDataType.RestingHeartRate -> if (isIOS11OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRestingHeartRate)
    } else { null }
    
    else -> null
}

internal fun HealthDataType.toHKSampleType(): HKSampleType? = when (this) {
    HealthDataType.Steps -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount)
    HealthDataType.Distance -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDistanceWalkingRunning)
    HealthDataType.Calories -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned)
    HealthDataType.ActiveCalories -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned)
    HealthDataType.BasalCalories -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBasalEnergyBurned)
    HealthDataType.Floors -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierFlightsClimbed)
    HealthDataType.HeartRate -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRate)
    HealthDataType.HeartRateVariability -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRateVariabilitySDNN)
    HealthDataType.BloodPressure -> HKCorrelationType.correlationTypeForIdentifier(HKCorrelationTypeIdentifierBloodPressure)
    HealthDataType.OxygenSaturation -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierOxygenSaturation)
    HealthDataType.RespiratoryRate -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRespiratoryRate)
    HealthDataType.BodyTemperature -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyTemperature)
    HealthDataType.Weight -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMass)
    HealthDataType.Height -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeight)
    HealthDataType.BodyFat -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyFatPercentage)
    HealthDataType.BMI -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMassIndex)
    HealthDataType.LeanBodyMass -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierLeanBodyMass)
    HealthDataType.Workout -> HKWorkoutType.workoutType()
    HealthDataType.Sleep -> HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis)
    HealthDataType.Water -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryWater)
    HealthDataType.BloodGlucose -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBloodGlucose)
    HealthDataType.VO2Max -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierVO2Max)
    
    HealthDataType.WalkingAsymmetry -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingAsymmetryPercentage)
    HealthDataType.WalkingDoubleSupportPercentage -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingDoubleSupportPercentage)
    HealthDataType.WalkingSpeed -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingSpeed)
    HealthDataType.WalkingStepLength -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingStepLength)
    HealthDataType.StairAscentSpeed -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStairAscentSpeed)
    HealthDataType.StairDescentSpeed -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStairDescentSpeed)
    HealthDataType.SixMinuteWalkTestDistance -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierSixMinuteWalkTestDistance)
    HealthDataType.NumberOfTimesFallen -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierNumberOfTimesFallen)
    HealthDataType.StandHours -> HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierAppleStandHour)
    
    HealthDataType.EnvironmentalAudioExposure -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierEnvironmentalAudioExposure)
    HealthDataType.HeadphoneAudioExposure -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeadphoneAudioExposure)
    HealthDataType.UVExposure -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierUVExposure)
    
    HealthDataType.RunningStrideLength -> if (isIOS16OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningStrideLength)
    } else { null }
    HealthDataType.RunningVerticalOscillation -> if (isIOS16OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningVerticalOscillation)
    } else { null }
    HealthDataType.RunningGroundContactTime -> if (isIOS16OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningGroundContactTime)
    } else { null }
    HealthDataType.CyclingCadence -> if (isIOS17OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingCadence)
    } else { null }
    HealthDataType.CyclingPower -> if (isIOS17OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingPower)
    } else { null }
    HealthDataType.CyclingFunctionalThresholdPower -> if (isIOS17OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingFunctionalThresholdPower)
    } else { null }
    HealthDataType.SwimmingStrokeStyle -> null
    
    HealthDataType.Electrocardiogram -> if (isIOS14OrLater()) {
        HKSeriesType.electrocardiogramType()
    } else { null }
    HealthDataType.IrregularHeartRhythmEvent -> HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierIrregularHeartRhythmEvent)
    HealthDataType.PeripheralPerfusionIndex -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierPeripheralPerfusionIndex)
    
    HealthDataType.Mindfulness -> HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierMindfulSession)
    
    HealthDataType.ClinicalAllergies -> if (isIOS12OrLater()) {
        HKClinicalType.clinicalTypeForIdentifier(HKClinicalTypeIdentifierAllergyRecord)
    } else { null }
    HealthDataType.ClinicalConditions -> if (isIOS12OrLater()) {
        HKClinicalType.clinicalTypeForIdentifier(HKClinicalTypeIdentifierConditionRecord)
    } else { null }
    HealthDataType.ClinicalImmunizations -> if (isIOS12OrLater()) {
        HKClinicalType.clinicalTypeForIdentifier(HKClinicalTypeIdentifierImmunizationRecord)
    } else { null }
    HealthDataType.ClinicalLabResults -> if (isIOS12OrLater()) {
        HKClinicalType.clinicalTypeForIdentifier(HKClinicalTypeIdentifierLabResultRecord)
    } else { null }
    HealthDataType.ClinicalMedications -> if (isIOS12OrLater()) {
        HKClinicalType.clinicalTypeForIdentifier(HKClinicalTypeIdentifierMedicationRecord)
    } else { null }
    HealthDataType.ClinicalProcedures -> if (isIOS12OrLater()) {
        HKClinicalType.clinicalTypeForIdentifier(HKClinicalTypeIdentifierProcedureRecord)
    } else { null }
    HealthDataType.ClinicalVitalSigns -> if (isIOS12OrLater()) {
        HKClinicalType.clinicalTypeForIdentifier(HKClinicalTypeIdentifierVitalSignRecord)
    } else { null }
    
    // Reproductive Health types (iOS 9+) - all are category types
    HealthDataType.MenstruationFlow -> HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierMenstrualFlow)
    HealthDataType.IntermenstrualBleeding -> HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierIntermenstrualBleeding)
    HealthDataType.CervicalMucus -> HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierCervicalMucusQuality)
    HealthDataType.OvulationTest -> HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierOvulationTestResult)
    HealthDataType.SexualActivity -> HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSexualActivity)
    
    // Nutrition types
    HealthDataType.Protein -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryProtein)
    HealthDataType.Carbohydrates -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryCarbohydrates)
    HealthDataType.Fat -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryFatTotal)
    HealthDataType.Fiber -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryFiber)
    HealthDataType.Sugar -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietarySugar)
    HealthDataType.Caffeine -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDietaryCaffeine)
    
    // Resting heart rate (iOS 11+)
    HealthDataType.RestingHeartRate -> if (isIOS11OrLater()) {
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRestingHeartRate)
    } else { null }
    
    else -> null
}

internal fun WorkoutType.toHKWorkoutActivityType(): HKWorkoutActivityType = when (this) {
    WorkoutType.RUNNING -> HKWorkoutActivityTypeRunning
    WorkoutType.WALKING -> HKWorkoutActivityTypeWalking
    WorkoutType.CYCLING -> HKWorkoutActivityTypeCycling
    WorkoutType.SWIMMING -> HKWorkoutActivityTypeSwimming
    WorkoutType.STRENGTH_TRAINING -> HKWorkoutActivityTypeTraditionalStrengthTraining
    WorkoutType.YOGA -> HKWorkoutActivityTypeYoga
    WorkoutType.PILATES -> HKWorkoutActivityTypePilates
    WorkoutType.DANCE -> HKWorkoutActivityTypeDanceInspiredTraining
    WorkoutType.MARTIAL_ARTS -> HKWorkoutActivityTypeMartialArts
    WorkoutType.ROWING -> HKWorkoutActivityTypeRowing
    WorkoutType.ELLIPTICAL -> HKWorkoutActivityTypeElliptical
    WorkoutType.STAIR_CLIMBING -> HKWorkoutActivityTypeStairClimbing
    WorkoutType.HIGH_INTENSITY_INTERVAL_TRAINING -> HKWorkoutActivityTypeHighIntensityIntervalTraining
    WorkoutType.FUNCTIONAL_TRAINING -> HKWorkoutActivityTypeFunctionalStrengthTraining
    WorkoutType.CORE_TRAINING -> HKWorkoutActivityTypeCoreTraining
    WorkoutType.CROSS_TRAINING -> HKWorkoutActivityTypeCrossTraining
    WorkoutType.FLEXIBILITY -> HKWorkoutActivityTypeFlexibility
    WorkoutType.MIXED_CARDIO -> HKWorkoutActivityTypeMixedCardio
    WorkoutType.SOCCER -> HKWorkoutActivityTypeSoccer
    WorkoutType.BASKETBALL -> HKWorkoutActivityTypeBasketball
    WorkoutType.TENNIS -> HKWorkoutActivityTypeTennis
    WorkoutType.GOLF -> HKWorkoutActivityTypeGolf
    WorkoutType.HIKING -> HKWorkoutActivityTypeHiking
    WorkoutType.SKIING -> HKWorkoutActivityTypeDownhillSkiing
    WorkoutType.SNOWBOARDING -> HKWorkoutActivityTypeSnowboarding
    WorkoutType.SKATING -> HKWorkoutActivityTypeSkatingSports
    WorkoutType.SURFING -> HKWorkoutActivityTypeSurfingSports
    WorkoutType.CLIMBING -> HKWorkoutActivityTypeClimbing
    WorkoutType.EQUESTRIAN -> HKWorkoutActivityTypeEquestrianSports
    WorkoutType.FISHING -> HKWorkoutActivityTypeFishing
    WorkoutType.HUNTING -> HKWorkoutActivityTypeHunting
    WorkoutType.PLAY -> HKWorkoutActivityTypePlay
    WorkoutType.MEDITATION -> HKWorkoutActivityTypeMindAndBody
    WorkoutType.COOLDOWN -> HKWorkoutActivityTypeCooldown
    WorkoutType.OTHER -> HKWorkoutActivityTypeOther
}

internal fun NSDate.toKotlinInstant(): Instant {
    return this.timeIntervalSince1970.toLong().let { 
        Instant.fromEpochSeconds(it)
    }
}

internal fun HKQuantitySample.toHeartRateData(): HeartRateData {
    val bpm = quantity.doubleValueForUnit(HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit())).toInt()
    
    return HeartRateData(
        timestamp = startDate.toKotlinInstant(),
        bpm = bpm,
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}

@OptIn(ExperimentalForeignApi::class)
internal fun HKSourceRevision.toDataSource(): DataSource {
    return DataSource(
        name = source.name,
        type = when {
            source.bundleIdentifier.contains("watch", ignoreCase = true) -> SourceType.DEVICE
            source.bundleIdentifier.contains("phone", ignoreCase = true) -> SourceType.DEVICE
            else -> SourceType.APPLICATION
        },
        device = productType?.let { type ->
            vitality.models.DeviceInfo(
                manufacturer = "Apple",
                model = type,
                type = when {
                    type.contains("Watch", ignoreCase = true) -> DataSourceDeviceType.WATCH
                    type.contains("iPhone", ignoreCase = true) -> DataSourceDeviceType.PHONE
                    else -> DataSourceDeviceType.OTHER
                },
                softwareVersion = version
            )
        }
    )
}

@Suppress("UNCHECKED_CAST")
internal fun NSDictionary.toKotlinMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    allKeys.forEach { key ->
        (key as? String)?.let { k ->
            objectForKey(k)?.let { value ->
                map[k] = value
            }
        }
    }
    return map
}

internal fun isIOS11OrLater(): Boolean {
    return true
}

internal fun isIOS12OrLater(): Boolean {
    return true
}

internal fun isIOS14OrLater(): Boolean {
    return true
}

@OptIn(ExperimentalForeignApi::class)
internal fun isIOS16OrLater(): Boolean {
    val systemVersion = UIDevice.currentDevice.systemVersion
    val majorVersion = systemVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
    return majorVersion >= 16
}

@OptIn(ExperimentalForeignApi::class)
internal fun isIOS17OrLater(): Boolean {
    val systemVersion = UIDevice.currentDevice.systemVersion
    val majorVersion = systemVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
    return majorVersion >= 17
}

// Mobility conversions
internal fun HKQuantitySample.toWalkingAsymmetryData(): WalkingAsymmetryData {
    val percentage = quantity.doubleValueForUnit(HKUnit.percentUnit())
    return WalkingAsymmetryData(
        timestamp = startDate.toKotlinInstant(),
        percentage = percentage,
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}

internal fun HKQuantitySample.toWalkingSpeedData(): WalkingSpeedData {
    val speed = quantity.doubleValueForUnit(HKUnit.meterUnit().unitDividedByUnit(HKUnit.secondUnit()))
    return WalkingSpeedData(
        timestamp = startDate.toKotlinInstant(),
        speed = speed,
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}

// Audio exposure conversions
internal fun HKQuantitySample.toEnvironmentalAudioExposureData(): EnvironmentalAudioExposureData {
    val decibels = quantity.doubleValueForUnit(HKUnit.decibelAWeightedSoundPressureLevelUnit())
    val duration = endDate.timeIntervalSinceDate(startDate).toDuration(DurationUnit.SECONDS)
    return EnvironmentalAudioExposureData(
        timestamp = startDate.toKotlinInstant(),
        level = decibels,
        duration = duration,
        startTime = startDate.toKotlinInstant(),
        endTime = endDate.toKotlinInstant(),
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}

// Clinical conversions
// ECG conversion using Swift interop via ECGReader
@OptIn(ExperimentalForeignApi::class)
internal fun ECGData.toElectrocardiogramData(): ElectrocardiogramData {
    val classification = when (this.classification()) {
        "SINUS_RHYTHM" -> ECGClassification.SINUS_RHYTHM
        "ATRIAL_FIBRILLATION" -> ECGClassification.ATRIAL_FIBRILLATION
        "INCONCLUSIVE_LOW_HEART_RATE" -> ECGClassification.INCONCLUSIVE_LOW_HEART_RATE
        "INCONCLUSIVE_HIGH_HEART_RATE" -> ECGClassification.INCONCLUSIVE_HIGH_HEART_RATE
        "INCONCLUSIVE_POOR_READING" -> ECGClassification.INCONCLUSIVE_POOR_RECORDING
        "INCONCLUSIVE_OTHER" -> ECGClassification.INCONCLUSIVE_OTHER
        "UNRECOGNIZED" -> ECGClassification.UNRECOGNIZED
        "UNKNOWN" -> ECGClassification.UNKNOWN
        else -> ECGClassification.NOT_SET
    }
    
    val symptomsStatus = when (this.symptomsStatus()) {
        "NONE" -> ECGSymptomsStatus.NONE
        "PRESENT" -> ECGSymptomsStatus.PRESENT
        "UNKNOWN" -> ECGSymptomsStatus.UNKNOWN
        else -> ECGSymptomsStatus.NOT_SET
    }
    
    // Convert symptoms array to set
    val symptomsSet = this.symptoms().mapNotNull { symptomString ->
        when (symptomString as? String) {
            "CHEST_TIGHTNESS_OR_PAIN" -> ECGSymptom.CHEST_TIGHTNESS_OR_PAIN
            "SHORTNESS_OF_BREATH" -> ECGSymptom.SHORTNESS_OF_BREATH
            "LIGHTHEADEDNESS_OR_DIZZINESS" -> ECGSymptom.LIGHTHEADEDNESS_OR_DIZZINESS
            "HEART_PALPITATIONS" -> ECGSymptom.HEART_PALPITATIONS
            "RAPID_POUNDING_FLUTTERING_OR_SKIPPED_HEARTBEAT" -> ECGSymptom.RAPID_POUNDING_FLUTTERING_OR_SKIPPED_HEARTBEAT
            "OTHER" -> ECGSymptom.OTHER
            else -> null
        }
    }.toSet()

    @Suppress("UNCHECKED_CAST")
    return ElectrocardiogramData(
        timestamp = startDate().toKotlinInstant(),
        classification = classification,
        averageHeartRate = averageHeartRate()?.intValue,
        samplingFrequency = samplingFrequency(),
        numberOfVoltageMeasurements = numberOfVoltageMeasurements().toInt(),
        symptomsStatus = symptomsStatus,
        symptoms = symptomsSet,
        metadata = (metadata() as? Map<String, Any>)?.toMap() ?: emptyMap()
    )
}

// Advanced workout conversions
internal fun HKQuantitySample.toRunningStrideLengthData(): RunningStrideLengthData {
    val length = quantity.doubleValueForUnit(HKUnit.meterUnit())
    return RunningStrideLengthData(
        timestamp = startDate.toKotlinInstant(),
        strideLength = length,
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}

internal fun HKQuantitySample.toPowerData(): PowerData {
    val power = quantity.doubleValueForUnit(HKUnit.wattUnit())
    return PowerData(
        timestamp = startDate.toKotlinInstant(),
        watts = power,
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}

// Mindfulness conversions
internal fun HKCategorySample.toMindfulnessSessionData(): MindfulnessSessionData {
    val duration = endDate.timeIntervalSinceDate(startDate).toDuration(DurationUnit.SECONDS)
    return MindfulnessSessionData(
        timestamp = startDate.toKotlinInstant(),
        duration = duration,
        startTime = startDate.toKotlinInstant(),
        endTime = endDate.toKotlinInstant(),
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}

internal fun HKCategorySample.toMenstruationFlowData(): MenstruationFlowData {
    val flowLevel = when (value) {
        1L -> FlowLevel.LIGHT
        2L -> FlowLevel.MEDIUM
        3L -> FlowLevel.HEAVY
        4L -> FlowLevel.SPOTTING
        else -> FlowLevel.UNSPECIFIED
    }
    return MenstruationFlowData(
        timestamp = startDate.toKotlinInstant(),
        flow = flowLevel,
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}

internal fun HKCategorySample.toIntermenstrualBleedingData(): IntermenstrualBleedingData {
    return IntermenstrualBleedingData(
        timestamp = startDate.toKotlinInstant(),
        isSpotting = true,
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}

internal fun HKCategorySample.toCervicalMucusData(): CervicalMucusData {
    val quality = when (value) {
        1L -> CervicalMucusQuality.DRY
        2L -> CervicalMucusQuality.STICKY
        3L -> CervicalMucusQuality.CREAMY
        4L -> CervicalMucusQuality.WATERY
        5L -> CervicalMucusQuality.EGG_WHITE
        else -> CervicalMucusQuality.DRY  // Default
    }
    return CervicalMucusData(
        timestamp = startDate.toKotlinInstant(),
        quality = quality,
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}

internal fun HKCategorySample.toOvulationTestData(): OvulationTestData {
    val result = when (value) {
        1L -> OvulationTestResult.NEGATIVE
        2L -> OvulationTestResult.POSITIVE
        3L -> OvulationTestResult.INDETERMINATE
        else -> OvulationTestResult.INDETERMINATE
    }
    return OvulationTestData(
        timestamp = startDate.toKotlinInstant(),
        result = result,
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}

internal fun HKCategorySample.toSexualActivityData(): SexualActivityData {
    // In HealthKit, sexual activity can store protection used in metadata
    val protectionUsed = (metadata as? NSDictionary)?.objectForKey("HKSexualActivityProtectionUsed") as? Boolean
    return SexualActivityData(
        timestamp = startDate.toKotlinInstant(),
        protectionUsed = protectionUsed,
        source = sourceRevision.toDataSource(),
        metadata = (metadata as? NSDictionary)?.toKotlinMap() ?: emptyMap()
    )
}