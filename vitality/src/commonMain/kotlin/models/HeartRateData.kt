package vitality.models

import kotlin.time.Instant

/**
 * Heart rate data point
 */
data class HeartRateData(
    override val timestamp: Instant,
    val bpm: Int,
    val variability: Double? = null, // HRV in milliseconds
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()