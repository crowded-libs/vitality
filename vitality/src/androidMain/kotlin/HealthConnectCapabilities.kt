package vitality

import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import kotlin.reflect.KClass

/**
 * Health Connect capabilities detection and reporting
 */
object HealthConnectCapabilities {
    
    /**
     * API levels where specific features were introduced
     */
    object ApiLevels {
        const val BASIC_HEALTH_CONNECT = 26
        const val EXERCISE_ROUTES = 29
        const val ADVANCED_METRICS = 30
        const val SLEEP_STAGES = 31
        const val NUTRITION_HYDRATION = 33
        const val SKIN_TEMPERATURE = 34
        const val MEDICAL_RECORDS = 35
        const val ENHANCED_MEDICAL = 36
    }
    
    /**
     * Check if a specific health data type is supported on the current device
     */
    fun isDataTypeSupported(dataType: HealthDataType): Boolean {
        val currentApiLevel = Build.VERSION.SDK_INT
        
        return when (dataType) {
            HealthDataType.Steps,
            HealthDataType.Distance,
            HealthDataType.HeartRate,
            HealthDataType.Calories,
            HealthDataType.ActiveCalories,
            HealthDataType.BasalCalories,
            HealthDataType.Weight,
            HealthDataType.BodyFat -> currentApiLevel >= ApiLevels.BASIC_HEALTH_CONNECT
            
            HealthDataType.Workout -> currentApiLevel >= ApiLevels.EXERCISE_ROUTES
            
            HealthDataType.RestingHeartRate,
            HealthDataType.HeartRateVariability,
            HealthDataType.BloodPressure,
            HealthDataType.OxygenSaturation,
            HealthDataType.RespiratoryRate -> currentApiLevel >= ApiLevels.ADVANCED_METRICS
            
            HealthDataType.Sleep -> currentApiLevel >= ApiLevels.SLEEP_STAGES
            
            HealthDataType.Protein,
            HealthDataType.Carbohydrates,
            HealthDataType.Fat,
            HealthDataType.Water -> currentApiLevel >= ApiLevels.NUTRITION_HYDRATION
            
            // Skin temperature needs API 34
            HealthDataType.BodyTemperature -> currentApiLevel >= ApiLevels.SKIN_TEMPERATURE
            
            HealthDataType.BloodGlucose -> currentApiLevel >= ApiLevels.ADVANCED_METRICS
            
            HealthDataType.MenstruationFlow,
            HealthDataType.SexualActivity,
            HealthDataType.OvulationTest,
            HealthDataType.CervicalMucus,
            HealthDataType.IntermenstrualBleeding -> currentApiLevel >= ApiLevels.ADVANCED_METRICS
            
            HealthDataType.LeanBodyMass -> currentApiLevel >= ApiLevels.BASIC_HEALTH_CONNECT
            
            HealthDataType.ClinicalAllergies,
            HealthDataType.ClinicalConditions,
            HealthDataType.ClinicalImmunizations,
            HealthDataType.ClinicalLabResults,
            HealthDataType.ClinicalMedications,
            HealthDataType.ClinicalProcedures,
            HealthDataType.ClinicalVitalSigns -> currentApiLevel >= ApiLevels.MEDICAL_RECORDS
            
            else -> currentApiLevel >= ApiLevels.BASIC_HEALTH_CONNECT
        }
    }
    
    /**
     * Get device capabilities for Health Connect
     */
    fun getDeviceCapabilities(context: android.content.Context): DeviceCapabilities {
        val apiLevel = Build.VERSION.SDK_INT
        
        return DeviceCapabilities(
            apiLevel = apiLevel,
            isHealthConnectAvailable = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE,
            hasExerciseRoutes = apiLevel >= ApiLevels.EXERCISE_ROUTES,
            hasSleepStages = apiLevel >= ApiLevels.SLEEP_STAGES,
            hasAdvancedMetrics = apiLevel >= ApiLevels.ADVANCED_METRICS,
            hasNutritionTracking = apiLevel >= ApiLevels.NUTRITION_HYDRATION,
            hasSkinTemperature = apiLevel >= ApiLevels.SKIN_TEMPERATURE,
            hasMedicalRecords = apiLevel >= ApiLevels.MEDICAL_RECORDS,
            hasEnhancedMedical = apiLevel >= ApiLevels.ENHANCED_MEDICAL,
            supportedDataTypes = getSupportedDataTypes(),
            deviceModel = Build.MODEL,
            deviceManufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            healthConnectVersion = getHealthConnectVersion(context)
        )
    }
    
    /**
     * Get all supported data types for current API level
     */
    fun getSupportedDataTypes(): Set<HealthDataType> {
        // List all known data types since HealthDataType is a sealed class, not an enum
        val allDataTypes = listOf(
            HealthDataType.Steps,
            HealthDataType.Distance,
            HealthDataType.HeartRate,
            HealthDataType.Calories,
            HealthDataType.ActiveCalories,
            HealthDataType.BasalCalories,
            HealthDataType.Weight,
            HealthDataType.BodyFat,
            HealthDataType.Workout,
            HealthDataType.RestingHeartRate,
            HealthDataType.HeartRateVariability,
            HealthDataType.BloodPressure,
            HealthDataType.OxygenSaturation,
            HealthDataType.RespiratoryRate,
            HealthDataType.Sleep,
            HealthDataType.Protein,
            HealthDataType.Carbohydrates,
            HealthDataType.Fat,
            HealthDataType.Water,
            HealthDataType.BodyTemperature,
            HealthDataType.BloodGlucose,
            HealthDataType.MenstruationFlow,
            HealthDataType.SexualActivity,
            HealthDataType.OvulationTest,
            HealthDataType.CervicalMucus,
            HealthDataType.IntermenstrualBleeding,
            HealthDataType.LeanBodyMass
        )
        return allDataTypes.filter { isDataTypeSupported(it) }.toSet()
    }
    
    /**
     * Get the Health Connect version if available
     */
    private fun getHealthConnectVersion(context: android.content.Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo("com.google.android.apps.healthdata", 0)
            packageInfo.versionName
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if a specific Health Connect feature is available
     */
    fun isFeatureAvailable(feature: HealthConnectFeature): Boolean {
        // This is a simple API level check, no need for try-catch
        return when (feature) {
            HealthConnectFeature.EXERCISE_ROUTES -> {
                // Exercise routes require API 29+
                Build.VERSION.SDK_INT >= ApiLevels.EXERCISE_ROUTES
            }
            HealthConnectFeature.BACKGROUND_READ -> {
                // Background read is always available with proper permissions
                true
            }
            HealthConnectFeature.RECORD_RETENTION -> {
                // Check if record retention policies are supported
                Build.VERSION.SDK_INT >= 34
            }
            HealthConnectFeature.MEDICAL_RECORDS -> {
                // Medical records require API 35+
                Build.VERSION.SDK_INT >= ApiLevels.MEDICAL_RECORDS
            }
            HealthConnectFeature.FHIR_RESOURCES -> {
                // FHIR resources require API 35+
                Build.VERSION.SDK_INT >= ApiLevels.MEDICAL_RECORDS
            }
        }
    }
    
    /**
     * Get record type class for a health data type
     */
    fun getRecordTypeForDataType(dataType: HealthDataType): KClass<out Record>? {
        return when (dataType) {
            HealthDataType.Steps -> StepsRecord::class
            HealthDataType.Distance -> DistanceRecord::class
            HealthDataType.HeartRate -> HeartRateRecord::class
            HealthDataType.Calories, HealthDataType.ActiveCalories -> ActiveCaloriesBurnedRecord::class
            HealthDataType.BasalCalories -> BasalMetabolicRateRecord::class
            HealthDataType.Weight -> WeightRecord::class
            HealthDataType.BodyFat -> BodyFatRecord::class
            HealthDataType.Workout -> ExerciseSessionRecord::class
            HealthDataType.Sleep -> SleepSessionRecord::class
            HealthDataType.BloodPressure -> BloodPressureRecord::class
            HealthDataType.BloodGlucose -> BloodGlucoseRecord::class
            HealthDataType.OxygenSaturation -> OxygenSaturationRecord::class
            HealthDataType.BodyTemperature -> BodyTemperatureRecord::class
            HealthDataType.RespiratoryRate -> RespiratoryRateRecord::class
            HealthDataType.RestingHeartRate -> RestingHeartRateRecord::class
            HealthDataType.HeartRateVariability -> HeartRateVariabilityRmssdRecord::class
            HealthDataType.Protein,
            HealthDataType.Carbohydrates,
            HealthDataType.Fat -> NutritionRecord::class
            HealthDataType.Water -> HydrationRecord::class
            HealthDataType.MenstruationFlow -> MenstruationFlowRecord::class
            HealthDataType.SexualActivity -> SexualActivityRecord::class
            HealthDataType.OvulationTest -> OvulationTestRecord::class
            HealthDataType.LeanBodyMass -> LeanBodyMassRecord::class
            HealthDataType.CervicalMucus -> CervicalMucusRecord::class
            HealthDataType.IntermenstrualBleeding -> IntermenstrualBleedingRecord::class
            else -> null
        }
    }
    
    /**
     * Check if enhanced metadata is available for a data type
     */
    fun hasEnhancedMetadata(dataType: HealthDataType): Boolean {
        val apiLevel = Build.VERSION.SDK_INT
        
        return when (dataType) {
            HealthDataType.HeartRate,
            HealthDataType.BloodPressure,
            HealthDataType.BloodGlucose,
            HealthDataType.OxygenSaturation -> apiLevel >= 30
            
            HealthDataType.Workout -> apiLevel >= 29
            
            else -> true
        }
    }
}

/**
 * Device capabilities for Health Connect
 */
data class DeviceCapabilities(
    val apiLevel: Int,
    val isHealthConnectAvailable: Boolean,
    val hasExerciseRoutes: Boolean,
    val hasSleepStages: Boolean,
    val hasAdvancedMetrics: Boolean,
    val hasNutritionTracking: Boolean,
    val hasSkinTemperature: Boolean,
    val hasMedicalRecords: Boolean,
    val hasEnhancedMedical: Boolean,
    val supportedDataTypes: Set<HealthDataType>,
    val deviceModel: String,
    val deviceManufacturer: String,
    val androidVersion: String,
    val healthConnectVersion: String?
)

/**
 * Health Connect features that may or may not be available
 */
enum class HealthConnectFeature {
    EXERCISE_ROUTES,
    BACKGROUND_READ,
    RECORD_RETENTION,
    MEDICAL_RECORDS,
    FHIR_RESOURCES
}