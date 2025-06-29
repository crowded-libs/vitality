package vitality.models

import kotlin.time.Instant
import kotlin.time.Duration

/**
 * Represents speed data during activity
 */
data class SpeedData(
    override val timestamp: Instant,
    val speedMetersPerSecond: Double,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Represents power output during exercise
 */
data class PowerData(
    override val timestamp: Instant,
    val watts: Double,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Represents cycling cadence data
 */
data class CyclingCadenceData(
    override val timestamp: Instant,
    val rpm: Int, // Revolutions per minute
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Represents wheelchair pushes data
 */
data class WheelchairPushesData(
    override val timestamp: Instant,
    val pushCount: Int,
    val duration: Duration? = null,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Represents running stride length data
 */
data class RunningStrideLengthData(
    override val timestamp: Instant,
    val strideLength: Double, // meters
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()