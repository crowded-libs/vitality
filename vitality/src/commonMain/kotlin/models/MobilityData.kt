package vitality.models

import kotlin.time.Instant

/**
 * Data model for walking asymmetry measurements
 */
data class WalkingAsymmetryData(
    override val timestamp: Instant,
    val percentage: Double, // 0-100%
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Data model for walking speed measurements
 */
data class WalkingSpeedData(
    override val timestamp: Instant,
    val speed: Double, // meters per second
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Data model for walking step length
 */
data class WalkingStepLengthData(
    override val timestamp: Instant,
    val stepLength: Double, // meters
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

