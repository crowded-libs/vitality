package vitality.models.fhir

import kotlinx.serialization.Serializable

/**
 * FHIR Procedure resource
 * An action that is or was performed on or for a patient
 */
@Serializable
data class FHIRProcedure(
    override val resourceType: String = "Procedure",
    override val id: String? = null,
    val identifier: List<Identifier>? = null,
    val instantiatesCanonical: List<String>? = null,
    val instantiatesUri: List<String>? = null,
    val basedOn: List<Reference>? = null,
    val partOf: List<Reference>? = null,
    val status: String, // preparation | in-progress | not-done | on-hold | stopped | completed | entered-in-error | unknown
    val statusReason: CodeableConcept? = null,
    val category: CodeableConcept? = null,
    val code: CodeableConcept? = null,
    val subject: Reference,
    val encounter: Reference? = null,
    val performedDateTime: String? = null,
    val performedPeriod: Period? = null,
    val performedString: String? = null,
    val performedAge: Quantity? = null,
    val performedRange: Range? = null,
    val recorder: Reference? = null,
    val asserter: Reference? = null,
    val performer: List<Performer>? = null,
    val location: Reference? = null,
    val reasonCode: List<CodeableConcept>? = null,
    val reasonReference: List<Reference>? = null,
    val bodySite: List<CodeableConcept>? = null,
    val outcome: CodeableConcept? = null,
    val report: List<Reference>? = null,
    val complication: List<CodeableConcept>? = null,
    val complicationDetail: List<Reference>? = null,
    val followUp: List<CodeableConcept>? = null,
    val note: List<Annotation>? = null,
    val focalDevice: List<FocalDevice>? = null,
    val usedReference: List<Reference>? = null,
    val usedCode: List<CodeableConcept>? = null
) : FHIRResource {
    
    @Serializable
    data class Performer(
        val function: CodeableConcept? = null,
        val actor: Reference,
        val onBehalfOf: Reference? = null
    )
    
    @Serializable
    data class FocalDevice(
        val action: CodeableConcept? = null,
        val manipulated: Reference
    )
}