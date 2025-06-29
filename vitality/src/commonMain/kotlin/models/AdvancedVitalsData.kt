package vitality.models

import kotlin.time.Instant

/**
 * Represents resting heart rate data
 */
data class RestingHeartRateData(
    override val timestamp: Instant,
    val bpm: Int,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Heart rate variability (HRV) data - measures variation in time between heartbeats
 */
data class HeartRateVariabilityData(
    override val timestamp: Instant,
    val sdnn: Double, // Standard deviation of NN intervals in milliseconds
    val rmssd: Double? = null, // Root mean square of successive differences
    val pnn50: Double? = null, // Percentage of successive differences > 50ms
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Advanced blood pressure data with additional metrics
 */
data class BloodPressureData(
    override val timestamp: Instant,
    val systolic: Int,
    val diastolic: Int,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Respiratory rate data
 */
data class RespiratoryRateData(
    override val timestamp: Instant,
    val breathsPerMinute: Double,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Oxygen saturation data
 */
data class OxygenSaturationData(
    override val timestamp: Instant,
    val percentage: Double,
    val supplementalOxygenFlow: Double? = null, // L/min if on oxygen
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

