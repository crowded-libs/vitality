package vitality.extensions

import android.os.Build
import androidx.health.connect.client.records.*
import vitality.HealthDataType
import vitality.WorkoutType
import vitality.exceptions.HealthConnectorException
import vitality.models.DataSource
import vitality.models.HeartRateData
import vitality.models.SourceType
import vitality.models.WorkoutData
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import androidx.health.connect.client.permission.HealthPermission as AndroidHealthPermission
import java.time.Instant as JavaInstant

/**
 * Extension functions for Health Connect type conversions
 */

internal fun vitality.HealthPermission.toHealthConnectPermission(): String? {
    val recordType: kotlin.reflect.KClass<out Record>? = when (dataType) {
        HealthDataType.Steps -> StepsRecord::class
        HealthDataType.Distance -> DistanceRecord::class
        HealthDataType.ActiveCalories -> ActiveCaloriesBurnedRecord::class
        HealthDataType.BasalCalories -> BasalMetabolicRateRecord::class
        HealthDataType.Calories -> TotalCaloriesBurnedRecord::class
        HealthDataType.HeartRate -> HeartRateRecord::class
        HealthDataType.HeartRateVariability -> HeartRateVariabilityRmssdRecord::class
        HealthDataType.BloodPressure -> BloodPressureRecord::class
        HealthDataType.OxygenSaturation -> OxygenSaturationRecord::class
        HealthDataType.RespiratoryRate -> RespiratoryRateRecord::class
        HealthDataType.BodyTemperature -> BodyTemperatureRecord::class
        HealthDataType.Weight -> WeightRecord::class
        HealthDataType.Height -> HeightRecord::class
        HealthDataType.BodyFat -> BodyFatRecord::class
        HealthDataType.BMI -> null
        HealthDataType.LeanBodyMass -> LeanBodyMassRecord::class
        HealthDataType.Workout -> ExerciseSessionRecord::class
        HealthDataType.Sleep -> SleepSessionRecord::class
        HealthDataType.Water -> HydrationRecord::class
        HealthDataType.Protein -> NutritionRecord::class
        HealthDataType.Carbohydrates -> NutritionRecord::class
        HealthDataType.Fat -> NutritionRecord::class
        HealthDataType.BloodGlucose -> BloodGlucoseRecord::class
        HealthDataType.VO2Max -> Vo2MaxRecord::class
        HealthDataType.RestingHeartRate -> RestingHeartRateRecord::class
        HealthDataType.WheelchairPushes -> WheelchairPushesRecord::class
        HealthDataType.MenstruationFlow -> MenstruationFlowRecord::class
        HealthDataType.MenstruationPeriod -> MenstruationPeriodRecord::class
        HealthDataType.OvulationTest -> OvulationTestRecord::class
        HealthDataType.SexualActivity -> SexualActivityRecord::class
        HealthDataType.CervicalMucus -> CervicalMucusRecord::class
        HealthDataType.IntermenstrualBleeding -> IntermenstrualBleedingRecord::class
        HealthDataType.Mindfulness -> null
        HealthDataType.ClinicalAllergies -> null
        HealthDataType.ClinicalConditions -> null
        HealthDataType.ClinicalImmunizations -> null
        HealthDataType.ClinicalLabResults -> null
        HealthDataType.ClinicalMedications -> null
        HealthDataType.ClinicalProcedures -> null
        HealthDataType.ClinicalVitalSigns -> null
        else -> null
    }
    
    return try {
        if (recordType != null) {
            val permission = when (accessType) {
                vitality.HealthPermission.AccessType.READ -> 
                    AndroidHealthPermission.getReadPermission(recordType)
                vitality.HealthPermission.AccessType.WRITE -> 
                    AndroidHealthPermission.getWritePermission(recordType)
            }
            permission
        } else {
            null
        }
    } catch (e: Exception) {
        throw HealthConnectorException.ConversionException(
            message = "Failed to convert permission for $dataType with access type $accessType",
            sourceType = "HealthPermission",
            targetType = "AndroidHealthPermission",
            cause = e
        )
    }
}

internal fun HeartRateRecord.toHeartRateData(): HeartRateData {
    val avgBpm = samples.map { it.beatsPerMinute }.average().toInt()
    
    return HeartRateData(
        timestamp = startTime.toKotlinInstant(),
        bpm = avgBpm,
        source = DataSource(
            name = metadata.dataOrigin?.packageName ?: "Health Connect",
            type = SourceType.APPLICATION
        ),
        metadata = buildMap {
            put("sampleCount", samples.size)
            populateCommonMetadata(metadata)
            
            if (Build.VERSION.SDK_INT >= 30) {
                val minBpm = samples.minOfOrNull { it.beatsPerMinute }
                val maxBpm = samples.maxOfOrNull { it.beatsPerMinute }
                minBpm?.let { put("minBpm", it) }
                maxBpm?.let { put("maxBpm", it) }
            }
            
            if (Build.VERSION.SDK_INT >= 30) {
                metadata.recordingMethod?.let { put("recordingMethod", it) }
            }
        }
    )
}

internal fun ExerciseSessionRecord.toWorkoutData(): WorkoutData {
    return WorkoutData(
        timestamp = startTime.toKotlinInstant(),
        id = metadata.id,
        type = exerciseType.toWorkoutType(),
        title = title,
        startTime = startTime.toKotlinInstant(),
        endTime = endTime.toKotlinInstant(),
        duration = endTime.let { endInstant ->
            val endMillis = endInstant.toEpochMilli()
            val startMillis = startTime.toEpochMilli()
            (endMillis - startMillis).milliseconds
        },
        source = DataSource(
            name = metadata.dataOrigin?.packageName ?: "Health Connect",
            type = SourceType.APPLICATION
        ),
        metadata = buildMap {
            notes?.let { put("notes", it) }
            populateCommonMetadata(metadata)
            
            if (Build.VERSION.SDK_INT >= 29) {
                // Route data would be available through additional queries if supported
                put("hasRoute", false)
            }
            
            if (Build.VERSION.SDK_INT >= 30) {
                metadata.recordingMethod?.let { put("recordingMethod", it) }
            }
        }
    )
}

internal fun Int.toWorkoutType(): WorkoutType {
    // Map Health Connect exercise type constants to WorkoutType
    return when (this) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> WorkoutType.RUNNING
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> WorkoutType.WALKING
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> WorkoutType.CYCLING
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> WorkoutType.SWIMMING
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> WorkoutType.STRENGTH_TRAINING
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> WorkoutType.YOGA
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> WorkoutType.PILATES
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> WorkoutType.DANCE
        ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> WorkoutType.MARTIAL_ARTS
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> WorkoutType.ROWING
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> WorkoutType.ELLIPTICAL
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> WorkoutType.STAIR_CLIMBING
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> WorkoutType.HIGH_INTENSITY_INTERVAL_TRAINING
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> WorkoutType.FUNCTIONAL_TRAINING
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> WorkoutType.FLEXIBILITY
        ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> WorkoutType.OTHER
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> WorkoutType.BASKETBALL
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> WorkoutType.TENNIS
        ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> WorkoutType.GOLF
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> WorkoutType.HIKING
        ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> WorkoutType.SKIING
        ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> WorkoutType.SNOWBOARDING
        ExerciseSessionRecord.EXERCISE_TYPE_SKATING,
        ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING -> WorkoutType.SKATING
        ExerciseSessionRecord.EXERCISE_TYPE_SURFING -> WorkoutType.SURFING
        ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> WorkoutType.CLIMBING
        else -> WorkoutType.OTHER
    }
}

internal fun WorkoutType.toExerciseType(): Int {
    return when (this) {
        WorkoutType.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        WorkoutType.WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        WorkoutType.CYCLING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        WorkoutType.SWIMMING -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
        WorkoutType.STRENGTH_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        WorkoutType.YOGA -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
        WorkoutType.PILATES -> ExerciseSessionRecord.EXERCISE_TYPE_PILATES
        WorkoutType.DANCE -> ExerciseSessionRecord.EXERCISE_TYPE_DANCING
        WorkoutType.MARTIAL_ARTS -> ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS
        WorkoutType.ROWING -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING
        WorkoutType.ELLIPTICAL -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
        WorkoutType.STAIR_CLIMBING -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING
        WorkoutType.HIGH_INTENSITY_INTERVAL_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        WorkoutType.FUNCTIONAL_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS
        WorkoutType.CORE_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS
        WorkoutType.CROSS_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS
        WorkoutType.FLEXIBILITY -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
        WorkoutType.MIXED_CARDIO -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        WorkoutType.SOCCER -> ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN
        WorkoutType.BASKETBALL -> ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL
        WorkoutType.TENNIS -> ExerciseSessionRecord.EXERCISE_TYPE_TENNIS
        WorkoutType.GOLF -> ExerciseSessionRecord.EXERCISE_TYPE_GOLF
        WorkoutType.HIKING -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
        WorkoutType.SKIING -> ExerciseSessionRecord.EXERCISE_TYPE_SKIING
        WorkoutType.SNOWBOARDING -> ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING
        WorkoutType.SKATING -> ExerciseSessionRecord.EXERCISE_TYPE_SKATING
        WorkoutType.SURFING -> ExerciseSessionRecord.EXERCISE_TYPE_SURFING
        WorkoutType.CLIMBING -> ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING
        WorkoutType.MEDITATION -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }
}

internal fun JavaInstant.toKotlinInstant(): Instant {
    return try {
        Instant.fromEpochMilliseconds(this.toEpochMilli())
    } catch (e: Exception) {
        throw HealthConnectorException.ConversionException(
            message = "Failed to convert Java Instant to Kotlin Instant: ${e.message}",
            sourceType = "java.time.Instant",
            targetType = "kotlin.time.Instant",
            cause = e
        )
    }
}

/**
 * Helper function to populate common metadata fields from Health Connect records
 */
internal fun MutableMap<String, Any>.populateCommonMetadata(metadata: androidx.health.connect.client.records.metadata.Metadata) {
    metadata.dataOrigin.packageName.let { put("sourceApp", it) }
    put("recordId", metadata.id)
    put("lastModified", metadata.lastModifiedTime.toKotlinInstant().toString())
    metadata.clientRecordId?.let { put("clientRecordId", it) }
    
    if (Build.VERSION.SDK_INT >= 30) {
        metadata.device?.let { device ->
            put("deviceManufacturer", device.manufacturer ?: "Unknown")
            put("deviceModel", device.model ?: "Unknown")
            device.type?.let { put("deviceType", it) }
        }
    }
}
