package vitality.models

import kotlin.time.Instant
import kotlin.time.Duration

/**
 * Sleep session data
 */
data class SleepData(
    override val timestamp: Instant, // Start time
    val startTime: Instant,
    val endTime: Instant,
    val duration: Duration,
    val stages: List<SleepStage> = emptyList(),
    val heartRateAverage: Int? = null,
    val heartRateMin: Int? = null,
    val respiratoryRateAverage: Double? = null,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Individual sleep stage within a sleep session
 */
data class SleepStage(
    val stage: SleepStageType,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Duration
)

/**
 * Types of sleep stages
 */
enum class SleepStageType {
    AWAKE,
    LIGHT,
    DEEP,
    REM,
    UNKNOWN
}

