package io.github.crowdedlibs.vitality_sample

import vitality.HealthConnector

/**
 * Android implementation that gets the singleton HealthConnector
 */
actual fun getHealthConnector(): HealthConnector {
    return HealthConnectorProvider.get() 
        ?: throw IllegalStateException("HealthConnector not initialized. Make sure MainActivity has been created.")
}