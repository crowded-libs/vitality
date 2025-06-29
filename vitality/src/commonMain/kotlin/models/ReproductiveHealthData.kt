package vitality.models

import kotlin.time.Instant

/**
 * Represents menstrual flow data
 */
data class MenstruationFlowData(
    override val timestamp: Instant,
    val flow: FlowLevel,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Flow levels for menstruation
 */
enum class FlowLevel {
    SPOTTING,
    LIGHT,
    MEDIUM,
    HEAVY,
    UNSPECIFIED
}

/**
 * Represents ovulation test results
 */
data class OvulationTestData(
    override val timestamp: Instant,
    val result: OvulationTestResult,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Ovulation test result types
 */
enum class OvulationTestResult {
    POSITIVE,
    NEGATIVE,
    INDETERMINATE
}

/**
 * Represents sexual activity data
 */
data class SexualActivityData(
    override val timestamp: Instant,
    val protectionUsed: Boolean? = null,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Represents cervical mucus quality observations
 */
data class CervicalMucusData(
    override val timestamp: Instant,
    val quality: CervicalMucusQuality,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

/**
 * Cervical mucus quality types
 */
enum class CervicalMucusQuality {
    DRY,
    STICKY,
    CREAMY,
    WATERY,
    EGG_WHITE
}

/**
 * Represents intermenstrual bleeding (spotting between periods)
 */
data class IntermenstrualBleedingData(
    override val timestamp: Instant,
    val isSpotting: Boolean = true,
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()

