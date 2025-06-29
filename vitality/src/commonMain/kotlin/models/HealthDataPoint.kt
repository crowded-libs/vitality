package vitality.models

import kotlin.time.Instant

abstract class HealthDataPoint {
    abstract val timestamp: Instant
    abstract val source: DataSource?
    abstract val metadata: Map<String, Any>
}

/**
 * Source of health data
 */
data class DataSource(
    val name: String,
    val type: SourceType,
    val bundleIdentifier: String? = null,
    val device: DeviceInfo? = null
)

/**
 * Type of data source
 */
enum class SourceType {
    DEVICE,
    APPLICATION,
    UNKNOWN
}

/**
 * Device information for health data
 */
data class DeviceInfo(
    val manufacturer: String?,
    val model: String?,
    val type: DataSourceDeviceType,
    val softwareVersion: String? = null
)

/**
 * Types of health tracking devices (for data sources)
 */
enum class DataSourceDeviceType {
    PHONE,
    WATCH,
    OTHER
}