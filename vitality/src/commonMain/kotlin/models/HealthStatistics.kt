package vitality.models

import vitality.HealthDataType
import kotlin.time.Instant

/**
 * Statistical aggregation options for health data
 */
enum class StatisticOption {
    MINIMUM,
    MAXIMUM,
    AVERAGE,
    SUM,
    COUNT
}

/**
 * Result of a statistical query on health data
 */
data class HealthStatistics(
    val dataType: HealthDataType,
    val startTime: Instant,
    val endTime: Instant,
    val buckets: List<StatisticBucket> = emptyList()
)

/**
 * A time bucket containing statistical data
 */
data class StatisticBucket(
    val startTime: Instant,
    val endTime: Instant,
    val statistics: Map<StatisticOption, Double> = emptyMap()
)