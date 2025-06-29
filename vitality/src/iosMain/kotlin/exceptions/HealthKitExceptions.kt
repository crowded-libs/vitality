package vitality.exceptions

import vitality.HealthDataType
import vitality.HealthPermission

/**
 * HealthKit-specific exceptions with detailed error information
 */
sealed class HealthKitException : Exception() {
    abstract val errorCode: String?
    abstract val errorDomain: String?
    abstract val underlyingError: Throwable?

    /**
     * Authorization-related errors
     */
    data class AuthorizationException(
        val deniedPermissions: Set<HealthPermission>,
        val undeterminedPermissions: Set<HealthPermission> = emptySet(),
        override val errorCode: String? = null,
        override val errorDomain: String? = "HKErrorDomain",
        override val underlyingError: Throwable? = null
    ) : HealthKitException() {
        override val message: String = buildString {
            append("HealthKit authorization failed. ")
            if (deniedPermissions.isNotEmpty()) {
                append("Denied: ${deniedPermissions.joinToString { "${it.dataType}:${it.accessType}" }}. ")
            }
            if (undeterminedPermissions.isNotEmpty()) {
                append("Not determined: ${undeterminedPermissions.joinToString { "${it.dataType}:${it.accessType}" }}")
            }
            errorCode?.let { append(" (Error code: $it)") }
        }
    }

    /**
     * Data availability errors
     */
    data class DataUnavailableException(
        val dataType: HealthDataType? = null,
        override val errorCode: String? = null,
        override val errorDomain: String? = "HKErrorDomain",
        override val underlyingError: Throwable? = null
    ) : HealthKitException() {
        override val message: String = buildString {
            append("HealthKit data unavailable")
            dataType?.let { append(" for $it") }
            append(". ")
            errorCode?.let { append(" (Error code: $it)") }
        }
    }

    /**
     * Version compatibility errors
     */
    data class VersionException(
        val requiredVersion: String,
        val currentVersion: String,
        val feature: String,
        override val errorCode: String? = null,
        override val errorDomain: String? = null,
        override val underlyingError: Throwable? = null
    ) : HealthKitException() {
        override val message: String =
            "Feature '$feature' requires iOS $requiredVersion or later. Current version: $currentVersion"
    }

    /**
     * Query errors
     */
    data class QueryException(
        val queryType: String,
        val details: String,
        override val errorCode: String? = null,
        override val errorDomain: String? = "HKErrorDomain",
        override val underlyingError: Throwable? = null
    ) : HealthKitException() {
        override val message: String = "HealthKit query failed ($queryType): $details"
    }

    /**
     * Workout session errors
     */
    data class WorkoutSessionException(
        val sessionId: String?,
        val details: String,
        override val errorCode: String? = null,
        override val errorDomain: String? = "HKErrorDomain",
        override val underlyingError: Throwable? = null
    ) : HealthKitException() {
        override val message: String = buildString {
            append("Workout session error")
            sessionId?.let { append(" (ID: $it)") }
            errorCode?.let { append(" (Error code: $it)") }
        }
    }

    /**
     * HealthKit error codes
     */
    object HealthKitErrorCode {
        const val ERROR_HEALTH_DATA_UNAVAILABLE = "1"
        const val ERROR_HEALTH_DATA_RESTRICTED = "2"
        const val ERROR_INVALID_ARGUMENT = "3"
        const val ERROR_AUTHORIZATION_DENIED = "4"
        const val ERROR_AUTHORIZATION_NOT_DETERMINED = "5"
        const val ERROR_DATABASE_INACCESSIBLE = "6"
        const val ERROR_USER_CANCELED = "7"
        const val ERROR_ANOTHER_WORKOUT_SESSION_STARTED = "8"
        const val ERROR_USER_EXITED_WORKOUT_SESSION = "9"
        const val ERROR_REQUIRED_AUTHORIZATION_DENIED = "10"
        const val ERROR_DATA_SIZE_EXCEEDED = "11"
        const val ERROR_BACKGROUND_DELIVERY_DISABLED = "12"
    }
}

    /**
     * Extension to convert NSError to HealthKitException
     */
    fun createHealthKitException(
        error: platform.Foundation.NSError,
        context: String? = null
    ): HealthKitException {
        val errorCode = error.code.toString()
        val errorDomain = error.domain
        val localizedDescription = error.localizedDescription

        return when (errorCode) {
            HealthKitException.HealthKitErrorCode.ERROR_AUTHORIZATION_DENIED,
            HealthKitException.HealthKitErrorCode.ERROR_AUTHORIZATION_NOT_DETERMINED,
            HealthKitException.HealthKitErrorCode.ERROR_REQUIRED_AUTHORIZATION_DENIED -> {
                HealthKitException.AuthorizationException(
                    deniedPermissions = emptySet(),
                    errorCode = errorCode,
                    errorDomain = errorDomain
                )
            }

            HealthKitException.HealthKitErrorCode.ERROR_HEALTH_DATA_UNAVAILABLE,
            HealthKitException.HealthKitErrorCode.ERROR_HEALTH_DATA_RESTRICTED -> {
                HealthKitException.DataUnavailableException(
                    dataType = null,
                    errorCode = errorCode,
                    errorDomain = errorDomain
                )
            }

            HealthKitException.HealthKitErrorCode.ERROR_ANOTHER_WORKOUT_SESSION_STARTED,
            HealthKitException.HealthKitErrorCode.ERROR_USER_EXITED_WORKOUT_SESSION -> {
                HealthKitException.WorkoutSessionException(
                    sessionId = null,
                    details = localizedDescription,
                    errorCode = errorCode,
                    errorDomain = errorDomain
                )
            }

            else -> {
                HealthKitException.QueryException(
                    queryType = context ?: "Unknown",
                    details = localizedDescription,
                    errorCode = errorCode,
                    errorDomain = errorDomain
                )
            }
        }
    }