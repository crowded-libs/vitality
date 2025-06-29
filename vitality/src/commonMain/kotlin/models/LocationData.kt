package vitality.models

import kotlin.time.Instant

/**
 * Location data for workout routes
 */
data class LocationData(
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Double? = null,         // Horizontal accuracy in meters
    val altitudeAccuracy: Double? = null, // Vertical accuracy in meters
    val speed: Double? = null,            // Speed in m/s
    val bearing: Double? = null,          // Direction in degrees
)

/**
 * Workout event types
 */
enum class WorkoutEventType {
    PAUSE,
    RESUME,
    LAP,
    MARKER,
    SEGMENT,
    MOTION_PAUSED,   // iOS automatic pause
    MOTION_RESUMED   // iOS automatic resume
}