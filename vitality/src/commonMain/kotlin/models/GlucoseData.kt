package vitality.models

import kotlin.time.Instant

/**
 * Blood glucose data
 */
data class GlucoseData(
    override val timestamp: Instant,
    val glucoseLevel: Double, // mmol/L
    val specimenSource: SpecimenSource? = null,
    val mealRelation: MealRelation? = null,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint() {
    
    /**
     * Convert to mg/dL
     */
    fun toMgDl(): Double = glucoseLevel * 18.0182
}

/**
 * Source of glucose specimen
 */
enum class SpecimenSource {
    INTERSTITIAL_FLUID,
    CAPILLARY_BLOOD,
    PLASMA,
    SERUM,
    TEARS,
    WHOLE_BLOOD,
    UNKNOWN
}

/**
 * Timing relation to meal
 */
enum class MealRelation {
    BEFORE_MEAL,
    AFTER_MEAL,
    FASTING,
    RANDOM,
    UNKNOWN
}