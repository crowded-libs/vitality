package vitality.models.fhir

import kotlinx.serialization.Serializable

/**
 * FHIR AllergyIntolerance resource
 * Risk of harmful or undesirable, physiological response which is unique to an individual
 */
@Serializable
data class FHIRAllergyIntolerance(
    override val resourceType: String = "AllergyIntolerance",
    override val id: String? = null,
    val identifier: List<Identifier>? = null,
    val clinicalStatus: CodeableConcept? = null, // active | inactive | resolved
    val verificationStatus: CodeableConcept? = null, // unconfirmed | confirmed | refuted | entered-in-error
    val type: String? = null, // allergy | intolerance
    val category: List<String>? = null, // food | medication | environment | biologic
    val criticality: String? = null, // low | high | unable-to-assess
    val code: CodeableConcept? = null,
    val patient: Reference,
    val encounter: Reference? = null,
    val onsetDateTime: String? = null,
    val onsetAge: Quantity? = null,
    val onsetPeriod: Period? = null,
    val onsetRange: Range? = null,
    val onsetString: String? = null,
    val recordedDate: String? = null,
    val recorder: Reference? = null,
    val asserter: Reference? = null,
    val lastOccurrence: String? = null,
    val note: List<Annotation>? = null,
    val reaction: List<Reaction>? = null
) : FHIRResource {
    
    @Serializable
    data class Reaction(
        val substance: CodeableConcept? = null,
        val manifestation: List<CodeableConcept>,
        val description: String? = null,
        val onset: String? = null,
        val severity: String? = null, // mild | moderate | severe
        val exposureRoute: CodeableConcept? = null,
        val note: List<Annotation>? = null
    )
}