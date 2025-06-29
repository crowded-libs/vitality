package vitality.models

import kotlin.time.Instant

/**
 * Steps data point
 */
data class StepsData(
    override val timestamp: Instant,
    val count: Int,
    val cadence: Double? = null, // steps per minute
    val floorsClimbed: Int? = null,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()