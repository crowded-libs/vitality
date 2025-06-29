package vitality

/**
 * Platform-specific health API capabilities
 */
data class HealthCapabilities(
    val platformName: String,
    val supportsBackgroundDelivery: Boolean,
    val supportsWearableIntegration: Boolean,
    val supportsLiveWorkoutMetrics: Boolean,
    val availableDataTypes: Set<HealthDataType>,
    val unavailableDataTypes: Set<HealthDataType>,
    val supportedWorkoutTypes: Set<WorkoutType>,
    val platformSpecificCapabilities: Map<String, Any>
) {
    /**
     * Check if a specific data type is available on this platform
     */
    fun isDataTypeAvailable(dataType: HealthDataType): Boolean {
        return dataType in availableDataTypes
    }
    
    /**
     * Check if a workout type is supported
     */
    fun isWorkoutTypeSupported(workoutType: WorkoutType): Boolean {
        return workoutType in supportedWorkoutTypes
    }
    
    /**
     * Get platform-specific capability value
     */
    inline fun <reified T> getPlatformCapability(key: String): T? {
        return platformSpecificCapabilities[key] as? T
    }
}