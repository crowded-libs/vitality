package vitality.models

import kotlin.time.Instant

/**
 * ECG (Electrocardiogram) classification types
 */
enum class ECGClassification {
    NOT_SET,
    SINUS_RHYTHM,
    ATRIAL_FIBRILLATION,
    INCONCLUSIVE_LOW_HEART_RATE,
    INCONCLUSIVE_HIGH_HEART_RATE,
    INCONCLUSIVE_POOR_RECORDING,
    INCONCLUSIVE_OTHER,
    UNRECOGNIZED,
    UNKNOWN
}

/**
 * ECG symptoms that can be recorded
 */
enum class ECGSymptom {
    CHEST_TIGHTNESS_OR_PAIN,
    SHORTNESS_OF_BREATH,
    LIGHTHEADEDNESS_OR_DIZZINESS,
    HEART_PALPITATIONS,
    RAPID_POUNDING_FLUTTERING_OR_SKIPPED_HEARTBEAT,
    OTHER
}

/**
 * ECG symptoms status
 */
enum class ECGSymptomsStatus {
    NOT_SET,
    NONE,
    PRESENT,
    UNKNOWN
}

/**
 * Represents electrocardiogram (ECG) data
 */
data class ElectrocardiogramData(
    override val timestamp: Instant,
    val classification: ECGClassification,
    val averageHeartRate: Int? = null,
    val samplingFrequency: Double = 512.0, // Hz, typically 512 Hz for Apple Watch
    val numberOfVoltageMeasurements: Int,
    val symptomsStatus: ECGSymptomsStatus = ECGSymptomsStatus.NOT_SET,
    val symptoms: Set<ECGSymptom> = emptySet(),
    val voltageMeasurements: List<Double> = emptyList(), // μV (microvolts)
    override val source: DataSource? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : HealthDataPoint()