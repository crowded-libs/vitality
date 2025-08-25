@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package vitality

/**
 * Android Health Connect implementation of health data type capability provider
 */
actual object HealthDataTypeCapabilityProvider {
    
    actual fun getCapabilities(dataType: HealthDataType): HealthDataTypeCapabilities {
        return when (dataType) {
            // Clinical records - read-only in Health Connect (Android 16+/API 35+)
            HealthDataType.ClinicalAllergies,
            HealthDataType.ClinicalConditions,
            HealthDataType.ClinicalImmunizations,
            HealthDataType.ClinicalLabResults,
            HealthDataType.ClinicalMedications,
            HealthDataType.ClinicalProcedures,
            HealthDataType.ClinicalVitalSigns -> HealthDataTypeCapabilities(
                dataType = dataType,
                canRead = true,
                canWrite = false,
                platformNotes = "Medical records via Personal Health Record API (Android 16+)"
            )
            
            // iOS-specific types not available in Health Connect
            HealthDataType.StandHours,
            HealthDataType.WalkingAsymmetry,
            HealthDataType.WalkingDoubleSupportPercentage,
            HealthDataType.WalkingSpeed,
            HealthDataType.WalkingStepLength,
            HealthDataType.StairAscentSpeed,
            HealthDataType.StairDescentSpeed,
            HealthDataType.SixMinuteWalkTestDistance,
            HealthDataType.IrregularHeartRhythmEvent,
            HealthDataType.Electrocardiogram,
            HealthDataType.EnvironmentalAudioExposure,
            HealthDataType.HeadphoneAudioExposure,
            HealthDataType.RunningStrideLength,
            HealthDataType.RunningVerticalOscillation,
            HealthDataType.RunningGroundContactTime,
            HealthDataType.CyclingCadence,
            HealthDataType.CyclingPower,
            HealthDataType.CyclingFunctionalThresholdPower,
            HealthDataType.SwimmingStrokeStyle,
            HealthDataType.Mindfulness,
            HealthDataType.UVExposure -> HealthDataTypeCapabilities(
                dataType = dataType,
                canRead = false,
                canWrite = false,
                platformNotes = "Not available in Health Connect"
            )
            
            // Most types in Health Connect support both read and write
            else -> HealthDataTypeCapabilities(
                dataType = dataType,
                canRead = true,
                canWrite = true,
                platformNotes = null
            )
        }
    }
    
    actual fun getAllCapabilities(): List<HealthDataTypeCapabilities> {
        // Get all known health data types
        val allTypes = listOf(
            HealthDataType.Steps, HealthDataType.Distance, HealthDataType.Calories,
            HealthDataType.ActiveCalories, HealthDataType.BasalCalories, HealthDataType.Floors,
            HealthDataType.HeartRate, HealthDataType.HeartRateVariability, HealthDataType.RestingHeartRate,
            HealthDataType.BloodPressure, HealthDataType.OxygenSaturation, HealthDataType.RespiratoryRate,
            HealthDataType.BodyTemperature, HealthDataType.Weight, HealthDataType.Height,
            HealthDataType.BodyFat, HealthDataType.BMI, HealthDataType.LeanBodyMass,
            HealthDataType.Workout, HealthDataType.Sleep, HealthDataType.Water,
            HealthDataType.BloodGlucose, HealthDataType.VO2Max,
            HealthDataType.WalkingAsymmetry, HealthDataType.WalkingDoubleSupportPercentage,
            HealthDataType.WalkingSpeed, HealthDataType.WalkingStepLength,
            HealthDataType.StairAscentSpeed, HealthDataType.StairDescentSpeed,
            HealthDataType.SixMinuteWalkTestDistance, HealthDataType.StandHours,
            HealthDataType.NumberOfTimesFallen, HealthDataType.EnvironmentalAudioExposure,
            HealthDataType.HeadphoneAudioExposure, HealthDataType.UVExposure,
            HealthDataType.RunningStrideLength, HealthDataType.RunningVerticalOscillation,
            HealthDataType.RunningGroundContactTime, HealthDataType.CyclingCadence,
            HealthDataType.CyclingPower, HealthDataType.CyclingFunctionalThresholdPower,
            HealthDataType.SwimmingStrokeStyle, HealthDataType.Electrocardiogram,
            HealthDataType.IrregularHeartRhythmEvent, HealthDataType.PeripheralPerfusionIndex,
            HealthDataType.Mindfulness, HealthDataType.MenstruationFlow,
            HealthDataType.IntermenstrualBleeding, HealthDataType.CervicalMucus,
            HealthDataType.OvulationTest, HealthDataType.SexualActivity,
            HealthDataType.Protein, HealthDataType.Carbohydrates, HealthDataType.Fat,
            HealthDataType.Fiber, HealthDataType.Sugar, HealthDataType.Caffeine,
            HealthDataType.ClinicalAllergies, HealthDataType.ClinicalConditions,
            HealthDataType.ClinicalImmunizations, HealthDataType.ClinicalLabResults,
            HealthDataType.ClinicalMedications, HealthDataType.ClinicalProcedures,
            HealthDataType.ClinicalVitalSigns
        )
        return allTypes.map { getCapabilities(it) }
    }
    
    actual fun canRead(dataType: HealthDataType): Boolean {
        return getCapabilities(dataType).canRead
    }
    
    actual fun canWrite(dataType: HealthDataType): Boolean {
        return getCapabilities(dataType).canWrite
    }
}