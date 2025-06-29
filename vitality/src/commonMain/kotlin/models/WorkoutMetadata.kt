package vitality.models

/**
 * Platform-supported metadata for workout sessions
 * Only includes fields that are natively supported by HealthKit or Health Connect
 */
data class WorkoutMetadata(
    // Basic workout info (both platforms support these as metadata)
    val title: String? = null,        // iOS: HKMetadataKeyWorkoutBrandName, Android: metadata
    val notes: String? = null,        // iOS: custom metadata, Android: notes field
    
    // Environmental data (supported through metadata)
    val isIndoor: Boolean? = null,    // iOS: HKMetadataKeyIndoorWorkout, Android: metadata
    
    // Device info (both platforms track source device)
    val deviceName: String? = null,   // Device that recorded the workout
    val deviceManufacturer: String? = null,
    
    val customData: Map<String, Any> = emptyMap()
)


