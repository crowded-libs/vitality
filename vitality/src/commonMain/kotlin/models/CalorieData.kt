package vitality.models

import kotlin.time.Instant

/**
 * Calorie data point
 */
data class CalorieData(
    override val timestamp: Instant,
    val activeCalories: Double,
    val basalCalories: Double? = null,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()