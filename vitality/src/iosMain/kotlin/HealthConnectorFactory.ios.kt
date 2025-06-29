package vitality

/**
 * iOS implementation factory
 */
actual fun createHealthConnector(): HealthConnector = HealthKitConnector()