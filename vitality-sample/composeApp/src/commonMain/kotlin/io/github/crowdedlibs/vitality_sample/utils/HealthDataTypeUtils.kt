package io.github.crowdedlibs.vitality_sample.utils

import vitality.HealthDataType

/**
 * Get a human-readable display name for a HealthDataType
 */
fun HealthDataType.getDisplayName(): String {
    return when (this) {
        // Fitness
        is HealthDataType.Steps -> "Steps"
        is HealthDataType.Distance -> "Distance"
        is HealthDataType.Calories -> "Calories"
        is HealthDataType.ActiveCalories -> "Active Calories"
        is HealthDataType.BasalCalories -> "Basal Calories"
        is HealthDataType.Floors -> "Floors Climbed"
        
        // Vitals
        is HealthDataType.HeartRate -> "Heart Rate"
        is HealthDataType.HeartRateVariability -> "Heart Rate Variability"
        is HealthDataType.BloodPressure -> "Blood Pressure"
        is HealthDataType.OxygenSaturation -> "Oxygen Saturation"
        is HealthDataType.RespiratoryRate -> "Respiratory Rate"
        is HealthDataType.BodyTemperature -> "Body Temperature"
        
        // Body Measurements
        is HealthDataType.Weight -> "Weight"
        is HealthDataType.Height -> "Height"
        is HealthDataType.BodyFat -> "Body Fat"
        is HealthDataType.BMI -> "BMI"
        is HealthDataType.LeanBodyMass -> "Lean Body Mass"
        
        // Workout
        is HealthDataType.Workout -> "Workout"
        is HealthDataType.VO2Max -> "VO2 Max"
        
        // Nutrition
        is HealthDataType.Water -> "Water"
        is HealthDataType.Protein -> "Protein"
        is HealthDataType.Carbohydrates -> "Carbohydrates"
        is HealthDataType.Fat -> "Fat"
        is HealthDataType.Fiber -> "Fiber"
        is HealthDataType.Sugar -> "Sugar"
        is HealthDataType.Caffeine -> "Caffeine"
        
        // Sleep
        is HealthDataType.Sleep -> "Sleep"
        
        // Movement & Mobility
        is HealthDataType.WalkingAsymmetry -> "Walking Asymmetry"
        is HealthDataType.WalkingDoubleSupportPercentage -> "Walking Double Support %"
        is HealthDataType.WalkingSpeed -> "Walking Speed"
        is HealthDataType.WalkingStepLength -> "Walking Step Length"
        is HealthDataType.StairAscentSpeed -> "Stair Ascent Speed"
        is HealthDataType.StairDescentSpeed -> "Stair Descent Speed"
        is HealthDataType.SixMinuteWalkTestDistance -> "6-Minute Walk Distance"
        is HealthDataType.NumberOfTimesFallen -> "Number of Falls"
        is HealthDataType.StandHours -> "Stand Hours"
        
        // Audio & Environmental
        is HealthDataType.EnvironmentalAudioExposure -> "Environmental Audio Exposure"
        is HealthDataType.HeadphoneAudioExposure -> "Headphone Audio Exposure"
        is HealthDataType.UVExposure -> "UV Exposure"
        
        // Advanced Workout Metrics
        is HealthDataType.RunningStrideLength -> "Running Stride Length"
        is HealthDataType.RunningVerticalOscillation -> "Running Vertical Oscillation"
        is HealthDataType.RunningGroundContactTime -> "Running Ground Contact Time"
        is HealthDataType.CyclingCadence -> "Cycling Cadence"
        is HealthDataType.CyclingPower -> "Cycling Power"
        is HealthDataType.CyclingFunctionalThresholdPower -> "Cycling FTP"
        is HealthDataType.SwimmingStrokeStyle -> "Swimming Stroke Style"
        
        // Clinical
        is HealthDataType.Electrocardiogram -> "ECG"
        is HealthDataType.IrregularHeartRhythmEvent -> "Irregular Heart Rhythm"
        is HealthDataType.PeripheralPerfusionIndex -> "Peripheral Perfusion Index"
        
        // Clinical Records (FHIR)
        is HealthDataType.ClinicalAllergies -> "Clinical Allergies"
        is HealthDataType.ClinicalConditions -> "Clinical Conditions"
        is HealthDataType.ClinicalImmunizations -> "Clinical Immunizations"
        is HealthDataType.ClinicalLabResults -> "Clinical Lab Results"
        is HealthDataType.ClinicalMedications -> "Clinical Medications"
        is HealthDataType.ClinicalProcedures -> "Clinical Procedures"
        is HealthDataType.ClinicalVitalSigns -> "Clinical Vital Signs"
        
        // Mindfulness
        is HealthDataType.Mindfulness -> "Mindfulness"
        
        // Advanced Vitals
        is HealthDataType.RestingHeartRate -> "Resting Heart Rate"
        
        // Activity Metrics
        is HealthDataType.WheelchairPushes -> "Wheelchair Pushes"
        
        // Reproductive Health
        is HealthDataType.MenstruationFlow -> "Menstruation Flow"
        is HealthDataType.MenstruationPeriod -> "Menstruation Period"
        is HealthDataType.OvulationTest -> "Ovulation Test"
        is HealthDataType.SexualActivity -> "Sexual Activity"
        is HealthDataType.CervicalMucus -> "Cervical Mucus"
        is HealthDataType.IntermenstrualBleeding -> "Intermenstrual Bleeding"
        
        // Other
        is HealthDataType.BloodGlucose -> "Blood Glucose"
    }
}