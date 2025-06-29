package vitality

/**
 * Represents a health data permission request
 */
data class HealthPermission(
    val dataType: HealthDataType,
    val accessType: AccessType
) {
    enum class AccessType { 
        READ, 
        WRITE 
    }
}

/**
 * Result of a permission request
 */
data class PermissionResult(
    val granted: Set<HealthPermission>,
    val denied: Set<HealthPermission>,
    val platformSpecificInfo: Map<String, Any> = emptyMap()
)

/**
 * Current permission status for health data access
 */
sealed class PermissionStatus {
    object Granted : PermissionStatus()
    object Denied : PermissionStatus()
    object NotDetermined : PermissionStatus()
    data class PartiallyGranted(
        val granted: Set<HealthPermission>,
        val denied: Set<HealthPermission>
    ) : PermissionStatus()
}