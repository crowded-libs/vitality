package io.github.crowdedlibs.vitality_sample

import vitality.HealthConnector

/**
 * Platform-specific factory for creating HealthConnector instances.
 */
expect fun getHealthConnector(): HealthConnector