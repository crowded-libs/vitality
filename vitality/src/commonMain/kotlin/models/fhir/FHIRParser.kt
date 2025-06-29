package vitality.models.fhir

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import vitality.exceptions.HealthConnectorException

/**
 * FHIR resource parser using kotlinx-serialization
 * Handles parsing of FHIR JSON resources with error handling and type detection
 */
object FHIRParser {
    
    /**
     * JSON configuration for FHIR parsing
     * - ignoreUnknownKeys: FHIR resources can have many optional fields
     * - isLenient: Handle variations in FHIR formatting
     * - coerceInputValues: Handle null/missing values gracefully
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }
    
    /**
     * Parse a FHIR JSON string into the appropriate resource type
     * @throws HealthConnectorException.ParseException if parsing fails
     */
    fun parseResource(fhirJson: String): FHIRResource? {
        return try {
            // First, parse as JsonObject to get the resourceType
            val jsonObject = json.parseToJsonElement(fhirJson).jsonObject
            val resourceType = jsonObject["resourceType"]?.jsonPrimitive?.content
            
            // Parse based on resource type
            when (resourceType) {
                "Immunization" -> parseImmunization(fhirJson)
                "MedicationStatement" -> parseMedicationStatement(fhirJson)
                "MedicationRequest" -> parseMedicationRequest(fhirJson)
                "AllergyIntolerance" -> parseAllergyIntolerance(fhirJson)
                "Condition" -> parseCondition(fhirJson)
                "Observation" -> parseObservation(fhirJson)
                "Procedure" -> parseProcedure(fhirJson)
                else -> null // Unknown resource type is not an error
            }
        } catch (e: SerializationException) {
            throw HealthConnectorException.ParseException(
                message = "Failed to parse FHIR resource: ${e.message}",
                data = fhirJson.take(200), // Limit data size in exception
                cause = e
            )
        } catch (e: Exception) {
            throw HealthConnectorException.ParseException(
                message = "Unexpected error parsing FHIR resource: ${e.message}",
                data = fhirJson.take(200),
                cause = e
            )
        }
    }
    
    /**
     * Parse an Immunization resource
     * @throws HealthConnectorException.ParseException if parsing fails
     */
    fun parseImmunization(fhirJson: String): FHIRImmunization? {
        return try {
            json.decodeFromString<FHIRImmunization>(fhirJson)
        } catch (e: SerializationException) {
            throw HealthConnectorException.ParseException(
                message = "Failed to parse Immunization resource: ${e.message}",
                data = fhirJson.take(200),
                cause = e
            )
        }
    }
    
    /**
     * Parse a MedicationStatement resource
     * @throws HealthConnectorException.ParseException if parsing fails
     */
    fun parseMedicationStatement(fhirJson: String): FHIRMedicationStatement? {
        return try {
            json.decodeFromString<FHIRMedicationStatement>(fhirJson)
        } catch (e: SerializationException) {
            throw HealthConnectorException.ParseException(
                message = "Failed to parse MedicationStatement resource: ${e.message}",
                data = fhirJson.take(200),
                cause = e
            )
        }
    }
    
    /**
     * Parse a MedicationRequest resource
     * @throws HealthConnectorException.ParseException if parsing fails
     */
    fun parseMedicationRequest(fhirJson: String): FHIRMedicationRequest? {
        return try {
            json.decodeFromString<FHIRMedicationRequest>(fhirJson)
        } catch (e: SerializationException) {
            throw HealthConnectorException.ParseException(
                message = "Failed to parse MedicationRequest resource: ${e.message}",
                data = fhirJson.take(200),
                cause = e
            )
        }
    }
    
    /**
     * Parse an AllergyIntolerance resource
     * @throws HealthConnectorException.ParseException if parsing fails
     */
    fun parseAllergyIntolerance(fhirJson: String): FHIRAllergyIntolerance? {
        return try {
            json.decodeFromString<FHIRAllergyIntolerance>(fhirJson)
        } catch (e: SerializationException) {
            throw HealthConnectorException.ParseException(
                message = "Failed to parse AllergyIntolerance resource: ${e.message}",
                data = fhirJson.take(200),
                cause = e
            )
        }
    }
    
    /**
     * Parse a Condition resource
     * @throws HealthConnectorException.ParseException if parsing fails
     */
    fun parseCondition(fhirJson: String): FHIRCondition? {
        return try {
            json.decodeFromString<FHIRCondition>(fhirJson)
        } catch (e: SerializationException) {
            throw HealthConnectorException.ParseException(
                message = "Failed to parse Condition resource: ${e.message}",
                data = fhirJson.take(200),
                cause = e
            )
        }
    }
    
    /**
     * Parse an Observation resource
     * @throws HealthConnectorException.ParseException if parsing fails
     */
    fun parseObservation(fhirJson: String): FHIRObservation? {
        return try {
            json.decodeFromString<FHIRObservation>(fhirJson)
        } catch (e: SerializationException) {
            throw HealthConnectorException.ParseException(
                message = "Failed to parse Observation resource: ${e.message}",
                data = fhirJson.take(200),
                cause = e
            )
        }
    }
    
    /**
     * Parse a Procedure resource
     * @throws HealthConnectorException.ParseException if parsing fails
     */
    fun parseProcedure(fhirJson: String): FHIRProcedure? {
        return try {
            json.decodeFromString<FHIRProcedure>(fhirJson)
        } catch (e: SerializationException) {
            throw HealthConnectorException.ParseException(
                message = "Failed to parse Procedure resource: ${e.message}",
                data = fhirJson.take(200),
                cause = e
            )
        }
    }
    
    /**
     * Parse multiple FHIR resources from a list of JSON strings
     */
    fun parseResources(fhirJsonList: List<String>): List<FHIRResource> {
        return fhirJsonList.mapNotNull { parseResource(it) }
    }
    
    /**
     * Detect the resource type from a FHIR JSON string without full parsing
     */
    fun detectResourceType(fhirJson: String): String? {
        return try {
            val jsonObject = json.parseToJsonElement(fhirJson).jsonObject
            jsonObject["resourceType"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
}