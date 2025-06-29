package vitality.models.fhir

import kotlinx.serialization.Serializable

/**
 * Common FHIR data types used across multiple resources
 */

@Serializable
data class CodeableConcept(
    val coding: List<Coding>? = null,
    val text: String? = null
)

@Serializable
data class Coding(
    val system: String? = null,
    val version: String? = null,
    val code: String? = null,
    val display: String? = null,
    val userSelected: Boolean? = null
)

@Serializable
data class Reference(
    val reference: String? = null,
    val type: String? = null,
    val identifier: Identifier? = null,
    val display: String? = null
)

@Serializable
data class Identifier(
    val use: String? = null,
    val type: CodeableConcept? = null,
    val system: String? = null,
    val value: String? = null,
    val period: Period? = null
)

@Serializable
data class Period(
    val start: String? = null,
    val end: String? = null
)

@Serializable
data class Quantity(
    val value: Double? = null,
    val comparator: String? = null,
    val unit: String? = null,
    val system: String? = null,
    val code: String? = null
)

@Serializable
data class Range(
    val low: Quantity? = null,
    val high: Quantity? = null
)

@Serializable
data class Annotation(
    val authorReference: Reference? = null,
    val authorString: String? = null,
    val time: String? = null,
    val text: String
)

@Serializable
data class Dosage(
    val sequence: Int? = null,
    val text: String? = null,
    val additionalInstruction: List<CodeableConcept>? = null,
    val patientInstruction: String? = null,
    val timing: Timing? = null,
    val asNeededBoolean: Boolean? = null,
    val asNeededCodeableConcept: CodeableConcept? = null,
    val site: CodeableConcept? = null,
    val route: CodeableConcept? = null,
    val method: CodeableConcept? = null,
    val doseAndRate: List<DoseAndRate>? = null,
    val maxDosePerPeriod: Ratio? = null,
    val maxDosePerAdministration: Quantity? = null,
    val maxDosePerLifetime: Quantity? = null
)

@Serializable
data class DoseAndRate(
    val type: CodeableConcept? = null,
    val doseRange: Range? = null,
    val doseQuantity: Quantity? = null,
    val rateRatio: Ratio? = null,
    val rateRange: Range? = null,
    val rateQuantity: Quantity? = null
)

@Serializable
data class Ratio(
    val numerator: Quantity? = null,
    val denominator: Quantity? = null
)

@Serializable
data class Timing(
    val event: List<String>? = null,
    val repeat: TimingRepeat? = null,
    val code: CodeableConcept? = null
)

@Serializable
data class TimingRepeat(
    val boundsDuration: Duration? = null,
    val boundsRange: Range? = null,
    val boundsPeriod: Period? = null,
    val count: Int? = null,
    val countMax: Int? = null,
    val duration: Double? = null,
    val durationMax: Double? = null,
    val durationUnit: String? = null,
    val frequency: Int? = null,
    val frequencyMax: Int? = null,
    val period: Double? = null,
    val periodMax: Double? = null,
    val periodUnit: String? = null,
    val dayOfWeek: List<String>? = null,
    val timeOfDay: List<String>? = null,
    val `when`: List<String>? = null,
    val offset: Int? = null
)

@Serializable
data class Duration(
    val value: Double? = null,
    val comparator: String? = null,
    val unit: String? = null,
    val system: String? = null,
    val code: String? = null
)

interface FHIRResource {
    val resourceType: String
    val id: String?
}