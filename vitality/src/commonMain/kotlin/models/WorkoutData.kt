package vitality.models

import vitality.WorkoutType
import kotlin.time.Instant
import kotlin.time.Duration

/**
 * Workout session data
 */
data class WorkoutData(
    override val timestamp: Instant, // Start time
    val id: String,
    val type: WorkoutType,
    val title: String? = null,
    val startTime: Instant,
    val endTime: Instant? = null,
    val duration: Duration? = null,
    val totalCalories: Double? = null,
    val activeCalories: Double? = null,
    val totalDistance: Double? = null,
    val distanceUnit: vitality.DistanceUnit? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val minHeartRate: Int? = null,
    val elevationGained: Double? = null,
    val elevationLost: Double? = null,
    val stepCount: Int? = null,
    val isIndoor: Boolean? = null,
    
    val segments: List<WorkoutSegment>? = null,
    val routeData: List<LocationData>? = null,
    
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Workout segment
 */
data class WorkoutSegment(
    val name: String? = null,
    val type: WorkoutType,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Duration,
    val totalCalories: Double? = null,
    val totalDistance: Double? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val metadata: Map<String, Any> = emptyMap()
)