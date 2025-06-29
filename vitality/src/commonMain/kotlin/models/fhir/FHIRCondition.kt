package vitality.models.fhir

import kotlinx.serialization.Serializable

/**
 * FHIR Condition resource
 * A clinical condition, problem, diagnosis, or other event, situation, issue, or clinical concept
 */
@Serializable
data class FHIRCondition(
    override val resourceType: String = "Condition",
    override val id: String? = null,
    val identifier: List<Identifier>? = null,
    val clinicalStatus: CodeableConcept? = null, // active | recurrence | relapse | inactive | remission | resolved
    val verificationStatus: CodeableConcept? = null, // unconfirmed | provisional | differential | confirmed | refuted | entered-in-error
    val category: List<CodeableConcept>? = null,
    val severity: CodeableConcept? = null,
    val code: CodeableConcept? = null,
    val bodySite: List<CodeableConcept>? = null,
    val subject: Reference,
    val encounter: Reference? = null,
    val onsetDateTime: String? = null,
    val onsetAge: Quantity? = null,
    val onsetPeriod: Period? = null,
    val onsetRange: Range? = null,
    val onsetString: String? = null,
    val abatementDateTime: String? = null,
    val abatementAge: Quantity? = null,
    val abatementPeriod: Period? = null,
    val abatementRange: Range? = null,
    val abatementString: String? = null,
    val recordedDate: String? = null,
    val recorder: Reference? = null,
    val asserter: Reference? = null,
    val stage: List<Stage>? = null,
    val evidence: List<Evidence>? = null,
    val note: List<Annotation>? = null
) : FHIRResource {
    
    @Serializable
    data class Stage(
        val summary: CodeableConcept? = null,
        val assessment: List<Reference>? = null,
        val type: CodeableConcept? = null
    )
    
    @Serializable
    data class Evidence(
        val code: List<CodeableConcept>? = null,
        val detail: List<Reference>? = null
    )
    
    /**
     * Check if this condition is resolved
     */
    fun isResolved(): Boolean {
        return clinicalStatus?.coding?.any { 
            it.code in listOf("resolved", "remission") 
        } ?: false
    }
}