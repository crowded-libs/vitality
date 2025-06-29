package vitality.models

import kotlin.time.Instant
import kotlin.time.Duration

/**
 * Data model for mindfulness/meditation sessions
 * iOS: Full support via HealthKit
 * Android: Limited support in Health Connect (basic meditation tracking)
 */
data class MindfulnessSessionData(
    override val timestamp: Instant,
    val duration: Duration,
    val startTime: Instant,
    val endTime: Instant,
    val heartRateVariability: Double? = null, // milliseconds
    val respiratoryRate: Double? = null, // breaths per minute
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()