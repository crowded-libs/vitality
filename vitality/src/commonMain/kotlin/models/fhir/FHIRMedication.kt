package vitality.models.fhir

import kotlinx.serialization.Serializable

/**
 * FHIR MedicationStatement resource
 * A record of a medication that is being consumed by a patient
 */
@Serializable
data class FHIRMedicationStatement(
    override val resourceType: String = "MedicationStatement",
    override val id: String? = null,
    val identifier: List<Identifier>? = null,
    val basedOn: List<Reference>? = null,
    val partOf: List<Reference>? = null,
    val status: String, // active | completed | entered-in-error | intended | stopped | on-hold | unknown | not-taken
    val statusReason: List<CodeableConcept>? = null,
    val category: CodeableConcept? = null,
    val medicationCodeableConcept: CodeableConcept? = null,
    val medicationReference: Reference? = null,
    val subject: Reference,
    val context: Reference? = null,
    val effectiveDateTime: String? = null,
    val effectivePeriod: Period? = null,
    val dateAsserted: String? = null,
    val informationSource: Reference? = null,
    val derivedFrom: List<Reference>? = null,
    val reasonCode: List<CodeableConcept>? = null,
    val reasonReference: List<Reference>? = null,
    val note: List<Annotation>? = null,
    val dosage: List<Dosage>? = null
) : FHIRResource

/**
 * FHIR MedicationRequest resource
 * An order or request for both supply of the medication and the instructions for administration
 */
@Serializable
data class FHIRMedicationRequest(
    override val resourceType: String = "MedicationRequest",
    override val id: String? = null,
    val identifier: List<Identifier>? = null,
    val status: String, // active | on-hold | cancelled | completed | entered-in-error | stopped | draft | unknown
    val statusReason: CodeableConcept? = null,
    val intent: String, // proposal | plan | order | original-order | reflex-order | filler-order | instance-order | option
    val category: List<CodeableConcept>? = null,
    val priority: String? = null, // routine | urgent | asap | stat
    val doNotPerform: Boolean? = null,
    val reportedBoolean: Boolean? = null,
    val reportedReference: Reference? = null,
    val medicationCodeableConcept: CodeableConcept? = null,
    val medicationReference: Reference? = null,
    val subject: Reference,
    val encounter: Reference? = null,
    val supportingInformation: List<Reference>? = null,
    val authoredOn: String? = null,
    val requester: Reference? = null,
    val performer: Reference? = null,
    val performerType: CodeableConcept? = null,
    val recorder: Reference? = null,
    val reasonCode: List<CodeableConcept>? = null,
    val reasonReference: List<Reference>? = null,
    val instantiatesCanonical: List<String>? = null,
    val instantiatesUri: List<String>? = null,
    val basedOn: List<Reference>? = null,
    val groupIdentifier: Identifier? = null,
    val courseOfTherapyType: CodeableConcept? = null,
    val insurance: List<Reference>? = null,
    val note: List<Annotation>? = null,
    val dosageInstruction: List<Dosage>? = null,
    val dispenseRequest: DispenseRequest? = null,
    val substitution: Substitution? = null,
    val priorPrescription: Reference? = null,
    val detectedIssue: List<Reference>? = null,
    val eventHistory: List<Reference>? = null
) : FHIRResource {
    
    @Serializable
    data class DispenseRequest(
        val initialFill: InitialFill? = null,
        val dispenseInterval: Duration? = null,
        val validityPeriod: Period? = null,
        val numberOfRepeatsAllowed: Int? = null,
        val quantity: Quantity? = null,
        val expectedSupplyDuration: Duration? = null,
        val performer: Reference? = null
    ) {
        @Serializable
        data class InitialFill(
            val quantity: Quantity? = null,
            val duration: Duration? = null
        )
    }
    
    @Serializable
    data class Substitution(
        val allowedBoolean: Boolean? = null,
        val allowedCodeableConcept: CodeableConcept? = null,
        val reason: CodeableConcept? = null
    )
}