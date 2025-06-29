package vitality.exceptions

import vitality.HealthDataType
import vitality.HealthPermission

/**
 * Base exception for health connector errors.
 * All exceptions include an optional cause to preserve the original error context.
 */
sealed class HealthConnectorException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Thrown when the health platform fails to initialize
     */
    data class InitializationError(
        override val message: String,
        override val cause: Throwable? = null
    ) : HealthConnectorException(message, cause)
    
    /**
     * Thrown when required permissions are denied
     */
    data class PermissionDenied(
        val permissions: Set<HealthPermission>,
        override val cause: Throwable? = null
    ) : HealthConnectorException(
        "Permission denied for: ${permissions.joinToString { "${it.dataType}:${it.accessType}" }}",
        cause
    )
    
    /**
     * Thrown when requested data is not available
     */
    data class DataNotAvailable(
        val dataType: HealthDataType,
        override val cause: Throwable? = null
    ) : HealthConnectorException(
        "Data not available for type: $dataType",
        cause
    )
    
    /**
     * Thrown when workout session operations fail
     */
    data class WorkoutSessionError(
        override val message: String,
        override val cause: Throwable? = null
    ) : HealthConnectorException(message, cause)
    
    /**
     * Thrown when a feature is not supported on the current platform
     */
    data class PlatformNotSupported(
        val feature: String,
        override val cause: Throwable? = null
    ) : HealthConnectorException(
        "Feature not supported on this platform: $feature",
        cause
    )
    
    /**
     * Thrown when data synchronization fails
     */
    data class DataSyncError(
        override val message: String,
        override val cause: Throwable? = null
    ) : HealthConnectorException(message, cause)
    
    /**
     * Thrown when attempting to use an unsupported feature
     */
    data class UnsupportedFeature(
        override val message: String,
        override val cause: Throwable? = null
    ) : HealthConnectorException(message, cause)
    
    /**
     * Thrown when data access operations fail
     */
    data class DataAccessError(
        override val message: String,
        override val cause: Throwable? = null
    ) : HealthConnectorException(message, cause)
    
    /**
     * Thrown when data parsing fails (e.g., FHIR, JSON)
     */
    data class ParseException(
        override val message: String,
        val data: String? = null,
        override val cause: Throwable? = null
    ) : HealthConnectorException(message, cause)
    
    /**
     * Thrown when data type conversion fails
     */
    data class ConversionException(
        override val message: String,
        val sourceType: String? = null,
        val targetType: String? = null,
        override val cause: Throwable? = null
    ) : HealthConnectorException(message, cause)
    
    /**
     * Thrown when data validation fails
     */
    data class InvalidDataException(
        override val message: String,
        val fieldName: String? = null,
        val invalidValue: Any? = null,
        override val cause: Throwable? = null
    ) : HealthConnectorException(message, cause)
}