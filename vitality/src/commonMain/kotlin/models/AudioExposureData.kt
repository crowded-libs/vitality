package vitality.models

import kotlin.time.Instant
import kotlin.time.Duration

/**
 * Data model for environmental audio exposure levels
 * Platform support: iOS only (HealthKit iOS 13+)
 * Android: Not supported in Health Connect
 */
data class EnvironmentalAudioExposureData(
    override val timestamp: Instant,
    val level: Double, // decibels (dB)
    val duration: Duration,
    val startTime: Instant,
    val endTime: Instant,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Data model for headphone audio exposure levels
 * Platform support: iOS only (HealthKit iOS 13+)
 * Android: Not supported in Health Connect
 */
data class HeadphoneAudioExposureData(
    override val timestamp: Instant,
    val level: Double, // decibels (dB)
    val duration: Duration,
    val startTime: Instant,
    val endTime: Instant,
    val isNotificationEnabled: Boolean = true,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()