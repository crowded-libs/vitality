package vitality

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MedicalResource
import androidx.health.connect.client.request.CreateMedicalDataSourceRequest
import androidx.health.connect.client.request.GetMedicalDataSourcesRequest
import androidx.health.connect.client.request.ReadMedicalResourcesInitialRequest
import androidx.health.connect.client.request.ReadMedicalResourcesPageRequest
import androidx.health.connect.client.request.UpsertMedicalResourceRequest
import androidx.annotation.RequiresPermission
import androidx.health.connect.client.records.FhirVersion
import kotlinx.coroutines.delay
import vitality.exceptions.HealthConnectorException
import vitality.models.fhir.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.minutes

/**
 * Medical records support for Health Connect (Android 16+ / API 35+)
 * 
 * This class provides access to FHIR-based medical records when available.
 * Uses the Personal Health Record (PHR) API introduced in Health Connect SDK 1.1.0-rc02.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@OptIn(ExperimentalPersonalHealthRecordApi::class)
class HealthConnectMedicalRecords(
    private val healthConnectClient: HealthConnectClient
) {
    companion object {
        private const val DATA_SOURCE_NAME = "Connected Health Library"
        private const val DATA_SOURCE_DISPLAY_NAME = "Connected Health Medical Records"

        private const val FHIR_TYPE_IMMUNIZATION = "Immunization"
        private const val FHIR_TYPE_ALLERGY_INTOLERANCE = "AllergyIntolerance"
        private const val FHIR_TYPE_CONDITION = "Condition"
        private const val FHIR_TYPE_MEDICATION_STATEMENT = "MedicationStatement"
        private const val FHIR_TYPE_OBSERVATION = "Observation"
        private const val FHIR_TYPE_PROCEDURE = "Procedure"
    }

    private var dataSourceId: String? = null

    /**
     * Check if medical records are supported on this device
     */
    fun isMedicalRecordsSupported(): Boolean {
        return Build.VERSION.SDK_INT >= 35 && isFeatureAvailable()
    }

    /**
     * Check if enhanced medical features are available (API 36+)
     */
    fun hasEnhancedMedicalFeatures(): Boolean {
        return Build.VERSION.SDK_INT >= 36 && isFeatureAvailable()
    }

    private fun isFeatureAvailable(): Boolean {
        return try {
            val status = healthConnectClient.features.getFeatureStatus(
                HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD
            )
            status == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ensure we have a medical data source created
     */
    @RequiresPermission("android.permission.health.WRITE_MEDICAL_DATA")
    private suspend fun ensureDataSource(): String {
        if (dataSourceId != null) {
            return dataSourceId!!
        }

        val existingSources = healthConnectClient.getMedicalDataSources(
            GetMedicalDataSourcesRequest(packageNames = emptyList())
        )

        val existingSource = existingSources.find { 
            it.displayName == DATA_SOURCE_DISPLAY_NAME 
        }

        if (existingSource != null) {
            dataSourceId = existingSource.id
            return existingSource.id
        }

        val request = CreateMedicalDataSourceRequest(
            displayName = DATA_SOURCE_DISPLAY_NAME,
            fhirVersion = FhirVersion(4, 0, 1),
            fhirBaseUri = Uri.parse("https://fhir.com/oauth/api/FHIR/R4/")
        )

        val dataSource = healthConnectClient.createMedicalDataSource(request)
        dataSourceId = dataSource.id
        return dataSource.id
    }

    /**
     * Read allergy records
     */
    suspend fun readAllergies(
        startTime: Instant = Clock.System.now() - 365.days,
        endTime: Instant = Clock.System.now()
    ): Result<List<FHIRAllergyIntolerance>> {
        if (!isMedicalRecordsSupported()) {
            return Result.failure(
                HealthConnectorException.UnsupportedFeature(
                    "Medical records require Android 16+ (API 35+)"
                )
            )
        }

        return try {
            val resources = readMedicalResourcesByType(
                resourceType = FHIR_TYPE_ALLERGY_INTOLERANCE,
                startTime = startTime,
                endTime = endTime
            )

            val allergies = resources.mapNotNull { resource ->
                FHIRParser.parseAllergyIntolerance(resource.fhirResource.data)
            }

            Result.success(allergies)
        } catch (e: Exception) {
            Result.failure(
                HealthConnectorException.DataAccessError(
                    "Failed to read allergy records: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Read condition/diagnosis records
     */
    suspend fun readConditions(
        startTime: Instant = Clock.System.now() - (365 * 5).days,
        endTime: Instant = Clock.System.now()
    ): Result<List<FHIRCondition>> {
        if (!isMedicalRecordsSupported()) {
            return Result.failure(
                HealthConnectorException.UnsupportedFeature(
                    "Medical records require Android 16+ (API 35+)"
                )
            )
        }

        return try {
            val resources = readMedicalResourcesByType(
                resourceType = FHIR_TYPE_CONDITION,
                startTime = startTime,
                endTime = endTime
            )

            val conditions = resources.mapNotNull { resource ->
                FHIRParser.parseCondition(resource.fhirResource.data)
            }

            Result.success(conditions)
        } catch (e: Exception) {
            Result.failure(
                HealthConnectorException.DataAccessError(
                    "Failed to read condition records: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Read immunization records
     */
    suspend fun readImmunizations(
        startTime: Instant = Clock.System.now() - (365 * 10).days,
        endTime: Instant = Clock.System.now()
    ): Result<List<FHIRImmunization>> {
        if (!isMedicalRecordsSupported()) {
            return Result.failure(
                HealthConnectorException.UnsupportedFeature(
                    "Medical records require Android 16+ (API 35+)"
                )
            )
        }

        return try {
            val resources = readMedicalResourcesByType(
                resourceType = FHIR_TYPE_IMMUNIZATION,
                startTime = startTime,
                endTime = endTime
            )

            val immunizations = resources.mapNotNull { resource ->
                FHIRParser.parseImmunization(resource.fhirResource.data)
            }

            Result.success(immunizations)
        } catch (e: Exception) {
            Result.failure(
                HealthConnectorException.DataAccessError(
                    "Failed to read immunization records: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Read medication records
     */
    suspend fun readMedications(): Result<List<FHIRMedicationStatement>> {
        if (!isMedicalRecordsSupported()) {
            return Result.failure(
                HealthConnectorException.UnsupportedFeature(
                    "Medical records require Android 16+ (API 35+)"
                )
            )
        }

        return try {
            val resources = readMedicalResourcesByType(
                resourceType = FHIR_TYPE_MEDICATION_STATEMENT,
                startTime = Clock.System.now() - 365.days,
                endTime = Clock.System.now()
            )

            val medications = resources.mapNotNull { resource ->
                FHIRParser.parseMedicationStatement(resource.fhirResource.data)
            }

            Result.success(medications)
        } catch (e: Exception) {
            Result.failure(
                HealthConnectorException.DataAccessError(
                    "Failed to read medication records: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Read lab results as FHIR Observation resources
     */
    suspend fun readLabResults(
        startTime: Instant = Clock.System.now() - 365.days,
        endTime: Instant = Clock.System.now()
    ): Result<List<FHIRObservation>> {
        if (!isMedicalRecordsSupported()) {
            return Result.failure(
                HealthConnectorException.UnsupportedFeature(
                    "Medical records require Android 16+ (API 35+)"
                )
            )
        }

        return try {
            val resources = readMedicalResourcesByType(
                resourceType = FHIR_TYPE_OBSERVATION,
                startTime = startTime,
                endTime = endTime
            )

            val observations = resources.mapNotNull { resource ->
                FHIRParser.parseObservation(resource.fhirResource.data)
            }

            Result.success(observations)
        } catch (e: Exception) {
            Result.failure(
                HealthConnectorException.DataAccessError(
                    "Failed to read lab results: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Read procedure records
     */
    suspend fun readProcedures(
        startTime: Instant = Clock.System.now() - (365 * 5).days,
        endTime: Instant = Clock.System.now()
    ): Result<List<FHIRProcedure>> {
        if (!isMedicalRecordsSupported()) {
            return Result.failure(
                HealthConnectorException.UnsupportedFeature(
                    "Medical records require Android 16+ (API 35+)"
                )
            )
        }

        return try {
            val resources = readMedicalResourcesByType(
                resourceType = FHIR_TYPE_PROCEDURE,
                startTime = startTime,
                endTime = endTime
            )

            val procedures = resources.mapNotNull { resource ->
                FHIRParser.parseProcedure(resource.fhirResource.data)
            }

            Result.success(procedures)
        } catch (e: Exception) {
            Result.failure(
                HealthConnectorException.DataAccessError(
                    "Failed to read procedure records: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Helper function to read medical resources by type with pagination
     */
    private suspend fun readMedicalResourcesByType(
        resourceType: String,
        startTime: Instant,
        endTime: Instant
    ): List<MedicalResource> {
        val allResources = mutableListOf<MedicalResource>()

        val initialRequest = ReadMedicalResourcesInitialRequest(
            medicalResourceType = 0,
            medicalDataSourceIds = emptySet()
        )

        var response = healthConnectClient.readMedicalResources(initialRequest)

        response.medicalResources.forEach { resource ->
            if (isResourceTypeMatch(resource, resourceType) && 
                isWithinTimeRange(resource, startTime, endTime)) {
                allResources.add(resource)
            }
        }

        while (response.nextPageToken != null) {
            val pageRequest = ReadMedicalResourcesPageRequest(
                pageToken = response.nextPageToken!!
            )
            response = healthConnectClient.readMedicalResources(pageRequest)

            response.medicalResources.forEach { resource ->
                if (isResourceTypeMatch(resource, resourceType) && 
                    isWithinTimeRange(resource, startTime, endTime)) {
                    allResources.add(resource)
                }
            }
        }

        return allResources
    }

    private fun isResourceTypeMatch(resource: MedicalResource, resourceType: String): Boolean {
        return try {
            val detectedType = FHIRParser.detectResourceType(resource.fhirResource.data)
            detectedType == resourceType
        } catch (e: Exception) {
            false
        }
    }

    private fun isWithinTimeRange(
        resource: MedicalResource, 
        startTime: Instant, 
        endTime: Instant
    ): Boolean {
        return try {
            val fhirData = resource.fhirResource.data
            val resourceType = FHIRParser.detectResourceType(fhirData)
            
            val dateString = when (resourceType) {
                "Observation" -> extractDateFromObservation(fhirData)
                "Immunization" -> extractDateFromImmunization(fhirData)
                "Condition" -> extractDateFromCondition(fhirData)
                "Procedure" -> extractDateFromProcedure(fhirData)
                "AllergyIntolerance" -> extractDateFromAllergy(fhirData)
                "MedicationStatement" -> extractDateFromMedication(fhirData)
                else -> null
            }
            
            dateString?.let { 
                val resourceDate = parseISO8601Date(it)
                resourceDate in startTime..endTime
            } ?: true // Include resources without dates
            
        } catch (e: Exception) {
            true
        }
    }
    
    private fun extractDateFromObservation(fhirJson: String): String? {
        return try {
            val jsonObject = Json.parseToJsonElement(fhirJson).jsonObject
            jsonObject["effectiveDateTime"]?.jsonPrimitive?.content
                ?: jsonObject["effectivePeriod"]?.jsonObject?.get("start")?.jsonPrimitive?.content
                ?: jsonObject["issued"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractDateFromImmunization(fhirJson: String): String? {
        return try {
            val jsonObject = Json.parseToJsonElement(fhirJson).jsonObject
            jsonObject["occurrenceDateTime"]?.jsonPrimitive?.content
                ?: jsonObject["recorded"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractDateFromCondition(fhirJson: String): String? {
        return try {
            val jsonObject = Json.parseToJsonElement(fhirJson).jsonObject
            jsonObject["onsetDateTime"]?.jsonPrimitive?.content
                ?: jsonObject["onsetPeriod"]?.jsonObject?.get("start")?.jsonPrimitive?.content
                ?: jsonObject["recordedDate"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractDateFromProcedure(fhirJson: String): String? {
        return try {
            val jsonObject = Json.parseToJsonElement(fhirJson).jsonObject
            jsonObject["performedDateTime"]?.jsonPrimitive?.content
                ?: jsonObject["performedPeriod"]?.jsonObject?.get("start")?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractDateFromAllergy(fhirJson: String): String? {
        return try {
            val jsonObject = Json.parseToJsonElement(fhirJson).jsonObject
            jsonObject["onsetDateTime"]?.jsonPrimitive?.content
                ?: jsonObject["onsetPeriod"]?.jsonObject?.get("start")?.jsonPrimitive?.content
                ?: jsonObject["recordedDate"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractDateFromMedication(fhirJson: String): String? {
        return try {
            val jsonObject = Json.parseToJsonElement(fhirJson).jsonObject
            jsonObject["effectiveDateTime"]?.jsonPrimitive?.content
                ?: jsonObject["effectivePeriod"]?.jsonObject?.get("start")?.jsonPrimitive?.content
                ?: jsonObject["dateAsserted"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseISO8601Date(dateString: String): Instant {
        return try {
            when {
                dateString.length == 4 -> {
                    Instant.parse("${dateString}-01-01T00:00:00Z")
                }
                dateString.length == 7 -> {
                    Instant.parse("${dateString}-01T00:00:00Z")
                }
                dateString.length == 10 -> {
                    Instant.parse("${dateString}T00:00:00Z")
                }
                else -> {
                    Instant.parse(dateString)
                }
            }
        } catch (e: Exception) {
            throw HealthConnectorException.ParseException(
                message = "Failed to parse ISO8601 date: $dateString",
                data = dateString,
                cause = e
            )
        }
    }

    /**
     * Write medical record data
     */
    @RequiresPermission("android.permission.health.WRITE_MEDICAL_DATA")
    suspend fun writeMedicalRecord(
        resourceType: MedicalResourceType,
        fhirResource: FHIRResource,
        validationLevel: ValidationLevel = ValidationLevel.STRICT
    ): Result<Unit> {
        if (!isMedicalRecordsSupported()) {
            return Result.failure(
                HealthConnectorException.UnsupportedFeature(
                    "Medical records require Android 16+ (API 35+)"
                )
            )
        }

        if (validationLevel == ValidationLevel.STRICT && !hasEnhancedMedicalFeatures()) {
            return Result.failure(
                HealthConnectorException.UnsupportedFeature(
                    "Strict validation requires Android 16.1+ (API 36+)"
                )
            )
        }

        return try {
            val sourceId = ensureDataSource()

            val fhirJson = when (fhirResource) {
                is FHIRImmunization -> kotlinx.serialization.json.Json.encodeToString(
                    FHIRImmunization.serializer(), fhirResource
                )
                is FHIRAllergyIntolerance -> kotlinx.serialization.json.Json.encodeToString(
                    FHIRAllergyIntolerance.serializer(), fhirResource
                )
                is FHIRCondition -> kotlinx.serialization.json.Json.encodeToString(
                    FHIRCondition.serializer(), fhirResource
                )
                is FHIRMedicationStatement -> kotlinx.serialization.json.Json.encodeToString(
                    FHIRMedicationStatement.serializer(), fhirResource
                )
                is FHIRObservation -> kotlinx.serialization.json.Json.encodeToString(
                    FHIRObservation.serializer(), fhirResource
                )
                is FHIRProcedure -> kotlinx.serialization.json.Json.encodeToString(
                    FHIRProcedure.serializer(), fhirResource
                )
                else -> throw IllegalArgumentException("Unsupported FHIR resource type")
            }

            val request = UpsertMedicalResourceRequest(
                dataSourceId = sourceId,
                data = fhirJson,
                fhirVersion = FhirVersion(4, 0, 1)
            )

            healthConnectClient.upsertMedicalResources(listOf(request))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                HealthConnectorException.DataAccessError(
                    "Failed to write medical record: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Observe changes to medical records
     */
    fun observeMedicalRecordChanges(
        resourceTypes: Set<MedicalResourceType> = MedicalResourceType.entries.toSet(),
        pollingInterval: Duration = 30.minutes
    ): Flow<MedicalRecordChange> = flow {
        if (!isMedicalRecordsSupported()) {
            throw HealthConnectorException.UnsupportedFeature(
                "Medical records require Android 16+ (API 35+)"
            )
        }

        while (true) {
            delay(pollingInterval.inWholeMilliseconds)
        }
    }

    /**
     * Request permissions for medical records
     */
    suspend fun requestMedicalRecordPermissions(
        resourceTypes: Set<MedicalResourceType>
    ): Result<MedicalPermissionResult> {
        if (!isMedicalRecordsSupported()) {
            return Result.failure(
                HealthConnectorException.UnsupportedFeature(
                    "Medical records require Android 16+ (API 35+)"
                )
            )
        }

        return try {
            val permissions = mutableSetOf<String>()

            permissions.add(HealthPermission.PERMISSION_WRITE_MEDICAL_DATA)

            if (resourceTypes.contains(MedicalResourceType.IMMUNIZATION)) {
                permissions.add(HealthPermission.PERMISSION_READ_MEDICAL_DATA_VACCINES)
            }

            val grantedPermissions = healthConnectClient.permissionController
                .getGrantedPermissions()

            val granted = mutableSetOf<MedicalResourceType>()
            val denied = mutableSetOf<MedicalResourceType>()

            resourceTypes.forEach { type ->
                when (type) {
                    MedicalResourceType.IMMUNIZATION -> {
                        if (grantedPermissions.contains(HealthPermission.PERMISSION_READ_MEDICAL_DATA_VACCINES)) {
                            granted.add(type)
                        } else {
                            denied.add(type)
                        }
                    }
                    else -> {
                        if (grantedPermissions.contains(HealthPermission.PERMISSION_WRITE_MEDICAL_DATA)) {
                            granted.add(type)
                        } else {
                            denied.add(type)
                        }
                    }
                }
            }

            Result.success(
                MedicalPermissionResult(
                    granted = granted,
                    denied = denied,
                    requiresAdditionalConsent = emptySet()
                )
            )
        } catch (e: Exception) {
            Result.failure(
                HealthConnectorException.PermissionDenied(
                    emptySet()
                )
            )
        }
    }
}

/**
 * Types of medical resources supported
 */
enum class MedicalResourceType {
    ALLERGY_INTOLERANCE,
    CONDITION,
    IMMUNIZATION,
    MEDICATION_STATEMENT,
    OBSERVATION,
    PROCEDURE,
    DIAGNOSTIC_REPORT,
    CARE_PLAN,
    GOAL,
    CARE_TEAM
}

/**
 * Result of medical record permission request
 */
data class MedicalPermissionResult(
    val granted: Set<MedicalResourceType>,
    val denied: Set<MedicalResourceType>,
    val requiresAdditionalConsent: Set<MedicalResourceType>
)

/**
 * Represents a change in medical records
 */
data class MedicalRecordChange(
    val resourceType: MedicalResourceType,
    val changeType: ChangeType,
    val resourceId: String,
    val timestamp: Instant
)

/**
 * Type of change to a medical record
 */
enum class ChangeType {
    CREATED,
    UPDATED,
    DELETED
}

/**
 * Validation level for medical data
 */
enum class ValidationLevel {
    NONE,
    BASIC,
    STRICT
}
