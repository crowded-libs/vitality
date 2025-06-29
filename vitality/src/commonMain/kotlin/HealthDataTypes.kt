package vitality

/**
 * Comprehensive health data types supported by the health API abstraction.
 * Includes common types supported by both HealthKit and Health Connect,
 * as well as platform-specific types.
 */
sealed class HealthDataType {
    // Fitness
    object Steps : HealthDataType()
    object Distance : HealthDataType()
    object Calories : HealthDataType()
    object ActiveCalories : HealthDataType()
    object BasalCalories : HealthDataType()
    object Floors : HealthDataType()
    
    // Vitals
    object HeartRate : HealthDataType()
    object HeartRateVariability : HealthDataType()
    object BloodPressure : HealthDataType()
    object OxygenSaturation : HealthDataType()
    object RespiratoryRate : HealthDataType()
    object BodyTemperature : HealthDataType()
    
    // Body Measurements
    object Weight : HealthDataType()
    object Height : HealthDataType()
    object BodyFat : HealthDataType()
    object BMI : HealthDataType()
    object LeanBodyMass : HealthDataType()
    
    // Workout
    object Workout : HealthDataType()
    object VO2Max : HealthDataType()
    
    // Nutrition
    object Water : HealthDataType()
    object Protein : HealthDataType()
    object Carbohydrates : HealthDataType()
    object Fat : HealthDataType()
    object Fiber : HealthDataType()
    object Sugar : HealthDataType()
    object Caffeine : HealthDataType()
    
    // Sleep
    object Sleep : HealthDataType()
    
    // Movement & Mobility
    object WalkingAsymmetry : HealthDataType()
    object WalkingDoubleSupportPercentage : HealthDataType()
    object WalkingSpeed : HealthDataType()
    object WalkingStepLength : HealthDataType()
    object StairAscentSpeed : HealthDataType()
    object StairDescentSpeed : HealthDataType()
    object SixMinuteWalkTestDistance : HealthDataType()
    object NumberOfTimesFallen : HealthDataType()
    object StandHours : HealthDataType()
    
    // Audio & Environmental
    object EnvironmentalAudioExposure : HealthDataType()
    object HeadphoneAudioExposure : HealthDataType()
    object UVExposure : HealthDataType()
    
    // Advanced Workout Metrics
    object RunningStrideLength : HealthDataType()
    object RunningVerticalOscillation : HealthDataType()
    object RunningGroundContactTime : HealthDataType()
    object CyclingCadence : HealthDataType()
    object CyclingPower : HealthDataType()
    object CyclingFunctionalThresholdPower : HealthDataType()
    object SwimmingStrokeStyle : HealthDataType()
    
    // Clinical
    object Electrocardiogram : HealthDataType()
    object IrregularHeartRhythmEvent : HealthDataType()
    object PeripheralPerfusionIndex : HealthDataType()
    
    // Clinical Records (FHIR)
    object ClinicalAllergies : HealthDataType()
    object ClinicalConditions : HealthDataType()
    object ClinicalImmunizations : HealthDataType()
    object ClinicalLabResults : HealthDataType()
    object ClinicalMedications : HealthDataType()
    object ClinicalProcedures : HealthDataType()
    object ClinicalVitalSigns : HealthDataType()
    
    // Mindfulness
    object Mindfulness : HealthDataType()
    
    // Advanced Vitals
    object RestingHeartRate : HealthDataType()
    
    // Activity Metrics (Health Connect only)
    object WheelchairPushes : HealthDataType()
    
    // Reproductive Health
    object MenstruationFlow : HealthDataType()
    object MenstruationPeriod : HealthDataType()  // Health Connect only
    object OvulationTest : HealthDataType()
    object SexualActivity : HealthDataType()
    object CervicalMucus : HealthDataType()
    object IntermenstrualBleeding : HealthDataType()
    
    // Other
    object BloodGlucose : HealthDataType()
    
    companion object {
        /**
         * Convert a string to HealthDataType
         */
        fun fromString(value: String): HealthDataType? {
            return when (value) {
                "Steps" -> Steps
                "Distance" -> Distance
                "Calories" -> Calories
                "ActiveCalories" -> ActiveCalories
                "BasalCalories" -> BasalCalories
                "Floors" -> Floors
                "HeartRate" -> HeartRate
                "HeartRateVariability" -> HeartRateVariability
                "BloodPressure" -> BloodPressure
                "OxygenSaturation" -> OxygenSaturation
                "RespiratoryRate" -> RespiratoryRate
                "BodyTemperature" -> BodyTemperature
                "Weight" -> Weight
                "Height" -> Height
                "BodyFat" -> BodyFat
                "BMI" -> BMI
                "LeanBodyMass" -> LeanBodyMass
                "Workout" -> Workout
                "VO2Max" -> VO2Max
                "Water" -> Water
                "Protein" -> Protein
                "Carbohydrates" -> Carbohydrates
                "Fat" -> Fat
                "Fiber" -> Fiber
                "Sugar" -> Sugar
                "Caffeine" -> Caffeine
                "Sleep" -> Sleep
                "ClinicalAllergies" -> ClinicalAllergies
                "ClinicalConditions" -> ClinicalConditions
                "ClinicalImmunizations" -> ClinicalImmunizations
                "ClinicalLabResults" -> ClinicalLabResults
                "ClinicalMedications" -> ClinicalMedications
                "ClinicalProcedures" -> ClinicalProcedures
                "ClinicalVitalSigns" -> ClinicalVitalSigns
                "Mindfulness" -> Mindfulness
                "RestingHeartRate" -> RestingHeartRate
                "WheelchairPushes" -> WheelchairPushes
                "MenstruationFlow" -> MenstruationFlow
                "MenstruationPeriod" -> MenstruationPeriod
                "OvulationTest" -> OvulationTest
                "SexualActivity" -> SexualActivity
                "CervicalMucus" -> CervicalMucus
                "IntermenstrualBleeding" -> IntermenstrualBleeding
                "BloodGlucose" -> BloodGlucose
                else -> null
            }
        }
    }
}

/**
 * Workout types supported across platforms
 */
enum class WorkoutType {
    // Common types supported by both platforms
    RUNNING, WALKING, CYCLING, SWIMMING, STRENGTH_TRAINING,
    YOGA, PILATES, DANCE, MARTIAL_ARTS, ROWING, ELLIPTICAL,
    STAIR_CLIMBING, HIGH_INTENSITY_INTERVAL_TRAINING,
    FUNCTIONAL_TRAINING, CORE_TRAINING, CROSS_TRAINING,
    FLEXIBILITY, MIXED_CARDIO, SOCCER, BASKETBALL, TENNIS,
    GOLF, HIKING, SKIING, SNOWBOARDING, SKATING,
    
    SURFING, CLIMBING, EQUESTRIAN, FISHING, HUNTING,
    PLAY, MEDITATION, COOLDOWN, OTHER
}

/**
 * Units for weight measurements
 */
enum class WeightUnit {
    KILOGRAMS,
    POUNDS,
    STONES
}

/**
 * Units for distance measurements
 */
enum class DistanceUnit {
    METERS,
    KILOMETERS,
    MILES,
    FEET
}