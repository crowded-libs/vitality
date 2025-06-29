package vitality.models

import vitality.WorkoutType

/**
 * Configuration for workout sessions
 */
data class WorkoutConfiguration(
    val type: WorkoutType,
    val isIndoor: Boolean? = null,
    val enableGpsTracking: Boolean = true,
    val metadata: Map<String, Any> = emptyMap()
)