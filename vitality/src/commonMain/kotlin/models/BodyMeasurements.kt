package vitality.models

import vitality.WeightUnit
import kotlin.time.Instant

/**
 * Body measurement data
 */
data class BodyMeasurements(
    override val timestamp: Instant,
    val weight: Double? = null,
    val weightUnit: WeightUnit? = null,
    val height: Double? = null, // in meters
    val bodyFatPercentage: Double? = null,
    val bmi: Double? = null,
    val leanBodyMass: Double? = null,
    val leanBodyMassUnit: WeightUnit? = null,
    val waistCircumference: Double? = null, // in meters
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()