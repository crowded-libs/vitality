package vitality.models

import vitality.DistanceUnit
import kotlin.time.Instant

/**
 * Distance data point
 */
data class DistanceData(
    override val timestamp: Instant,
    val distance: Double,
    val unit: DistanceUnit,
    val activityType: DistanceActivityType,
    val startTime: Instant? = null, // For interval data
    val endTime: Instant? = null, // For interval data
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()
    
/**
 * Type of activity for distance tracking
 */
enum class DistanceActivityType {
    WALKING,
    RUNNING,
    CYCLING,
    SWIMMING,
    WHEELCHAIR,
    OTHER,
    UNKNOWN
}