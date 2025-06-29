package vitality.models.fhir

import kotlinx.serialization.Serializable

/**
 * FHIR Immunization resource
 * Describes the event of a patient being administered a vaccine or a record of an immunization
 */
@Serializable
data class FHIRImmunization(
    override val resourceType: String = "Immunization",
    override val id: String? = null,
    val identifier: List<Identifier>? = null,
    val status: String, // completed | entered-in-error | not-done
    val statusReason: CodeableConcept? = null,
    val vaccineCode: CodeableConcept,
    val patient: Reference,
    val encounter: Reference? = null,
    val occurrenceDateTime: String? = null,
    val occurrenceString: String? = null,
    val recorded: String? = null,
    val primarySource: Boolean? = null,
    val reportOrigin: CodeableConcept? = null,
    val location: Reference? = null,
    val manufacturer: Reference? = null,
    val lotNumber: String? = null,
    val expirationDate: String? = null,
    val site: CodeableConcept? = null,
    val route: CodeableConcept? = null,
    val doseQuantity: Quantity? = null,
    val performer: List<Performer>? = null,
    val note: List<Annotation>? = null,
    val reasonCode: List<CodeableConcept>? = null,
    val reasonReference: List<Reference>? = null,
    val isSubpotent: Boolean? = null,
    val subpotentReason: List<CodeableConcept>? = null,
    val education: List<Education>? = null,
    val programEligibility: List<CodeableConcept>? = null,
    val fundingSource: CodeableConcept? = null,
    val reaction: List<Reaction>? = null,
    val protocolApplied: List<ProtocolApplied>? = null
) : FHIRResource {
    
    @Serializable
    data class Performer(
        val function: CodeableConcept? = null,
        val actor: Reference
    )
    
    @Serializable
    data class Education(
        val documentType: String? = null,
        val reference: String? = null,
        val publicationDate: String? = null,
        val presentationDate: String? = null
    )
    
    @Serializable
    data class Reaction(
        val date: String? = null,
        val detail: Reference? = null,
        val reported: Boolean? = null
    )
    
    @Serializable
    data class ProtocolApplied(
        val series: String? = null,
        val authority: Reference? = null,
        val targetDisease: List<CodeableConcept>? = null,
        val doseNumberPositiveInt: Int? = null,
        val doseNumberString: String? = null,
        val seriesDosesPositiveInt: Int? = null,
        val seriesDosesString: String? = null
    )
}