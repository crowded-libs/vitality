package vitality.models.fhir

import kotlinx.serialization.Serializable

/**
 * FHIR Observation resource
 * Measurements and simple assertions made about a patient (including lab results)
 */
@Serializable
data class FHIRObservation(
    override val resourceType: String = "Observation",
    override val id: String? = null,
    val identifier: List<Identifier>? = null,
    val basedOn: List<Reference>? = null,
    val partOf: List<Reference>? = null,
    val status: String, // registered | preliminary | final | amended | corrected | cancelled | entered-in-error | unknown
    val category: List<CodeableConcept>? = null,
    val code: CodeableConcept,
    val subject: Reference? = null,
    val focus: List<Reference>? = null,
    val encounter: Reference? = null,
    val effectiveDateTime: String? = null,
    val effectivePeriod: Period? = null,
    val effectiveTiming: Timing? = null,
    val effectiveInstant: String? = null,
    val issued: String? = null,
    val performer: List<Reference>? = null,
    val valueQuantity: Quantity? = null,
    val valueCodeableConcept: CodeableConcept? = null,
    val valueString: String? = null,
    val valueBoolean: Boolean? = null,
    val valueInteger: Int? = null,
    val valueRange: Range? = null,
    val valueRatio: Ratio? = null,
    val valueSampledData: SampledData? = null,
    val valueTime: String? = null,
    val valueDateTime: String? = null,
    val valuePeriod: Period? = null,
    val dataAbsentReason: CodeableConcept? = null,
    val interpretation: List<CodeableConcept>? = null,
    val note: List<Annotation>? = null,
    val bodySite: CodeableConcept? = null,
    val method: CodeableConcept? = null,
    val specimen: Reference? = null,
    val device: Reference? = null,
    val referenceRange: List<ReferenceRange>? = null,
    val hasMember: List<Reference>? = null,
    val derivedFrom: List<Reference>? = null,
    val component: List<Component>? = null
) : FHIRResource {
    
    @Serializable
    data class ReferenceRange(
        val low: Quantity? = null,
        val high: Quantity? = null,
        val type: CodeableConcept? = null,
        val appliesTo: List<CodeableConcept>? = null,
        val age: Range? = null,
        val text: String? = null
    )
    
    @Serializable
    data class Component(
        val code: CodeableConcept,
        val valueQuantity: Quantity? = null,
        val valueCodeableConcept: CodeableConcept? = null,
        val valueString: String? = null,
        val valueBoolean: Boolean? = null,
        val valueInteger: Int? = null,
        val valueRange: Range? = null,
        val valueRatio: Ratio? = null,
        val valueSampledData: SampledData? = null,
        val valueTime: String? = null,
        val valueDateTime: String? = null,
        val valuePeriod: Period? = null,
        val dataAbsentReason: CodeableConcept? = null,
        val interpretation: List<CodeableConcept>? = null,
        val referenceRange: List<ReferenceRange>? = null
    )
    
    @Serializable
    data class SampledData(
        val origin: Quantity,
        val period: Double,
        val factor: Double? = null,
        val lowerLimit: Double? = null,
        val upperLimit: Double? = null,
        val dimensions: Int,
        val data: String? = null
    )
}