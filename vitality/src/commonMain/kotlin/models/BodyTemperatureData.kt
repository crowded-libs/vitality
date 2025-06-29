package vitality.models

import kotlin.time.Instant

/**
 * Represents body temperature measurement
 */
data class BodyTemperatureData(
    override val timestamp: Instant,
    val temperature: Double,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Temperature units
 */
enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT,
    KELVIN
}