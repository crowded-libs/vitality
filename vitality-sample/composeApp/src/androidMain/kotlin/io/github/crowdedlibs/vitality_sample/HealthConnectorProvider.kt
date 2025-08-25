package io.github.crowdedlibs.vitality_sample

import androidx.activity.ComponentActivity
import vitality.HealthConnector
import vitality.createHealthConnector

/**
 * Singleton provider for HealthConnector on Android.
 * This ensures the connector is created only once and activity result
 * registration happens at the right time.
 */
object HealthConnectorProvider {
    private var connector: HealthConnector? = null
    
    /**
     * Get or create the HealthConnector instance.
     * Must be called from an Activity context.
     */
    fun getOrCreate(activity: ComponentActivity): HealthConnector {
        return connector ?: createHealthConnector(activity).also {
            connector = it
        }
    }
    
    /**
     * Get the existing HealthConnector instance.
     * Returns null if not yet created.
     */
    fun get(): HealthConnector? = connector
}