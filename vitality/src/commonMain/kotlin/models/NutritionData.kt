package vitality.models

import kotlin.time.Instant

/**
 * Nutrition data point
 */
data class NutritionData(
    override val timestamp: Instant,
    val calories: Double? = null,
    val protein: Double? = null, // grams
    val carbohydrates: Double? = null, // grams
    val fat: Double? = null, // grams
    val saturatedFat: Double? = null, // grams
    val unsaturatedFat: Double? = null, // grams
    val fiber: Double? = null, // grams
    val sugar: Double? = null, // grams
    val sodium: Double? = null, // milligrams
    val cholesterol: Double? = null, // milligrams
    val water: Double? = null, // milliliters
    val caffeine: Double? = null, // milligrams
    val alcohol: Double? = null, // grams
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Water intake data
 */
data class HydrationData(
    override val timestamp: Instant,
    val volume: Double, // milliliters
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()