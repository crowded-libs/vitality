package io.github.crowdedlibs.vitality_sample

import vitality.HealthConnector
import vitality.createHealthConnector

/**
 * iOS implementation that creates a new HealthConnector
 */
actual fun getHealthConnector(): HealthConnector {
    return createHealthConnector()
}