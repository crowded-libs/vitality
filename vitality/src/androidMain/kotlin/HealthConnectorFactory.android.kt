package vitality

import androidx.activity.ComponentActivity

/**
 * Android implementation factory
 * 
 * Note: Before using this factory, you must:
 * 1. Initialize HealthConnectorContextProvider with your application context, typically in your Application.onCreate():
 *    ```
 *    HealthConnectorContextProvider.initialize(applicationContext)
 *    ```
 * 2. Set the current activity for permission requests:
 *    ```
 *    HealthConnectorContextProvider.activity = yourActivity
 *    ```
 * 
 * Alternatively, you can create the connector directly with an activity:
 * ```
 * val connector = createHealthConnector(activity)
 * ```
 */
actual fun createHealthConnector(): HealthConnector {
    if (!HealthConnectorContextProvider.isInitialized) {
        throw IllegalStateException(
            "HealthConnectorContextProvider not initialized. Call HealthConnectorContextProvider.initialize() with your application context first."
        )
    }
    
    if (!HealthConnectorContextProvider.hasActivity) {
        throw IllegalStateException(
            "Activity not set. Set HealthConnectorContextProvider.activity with your ComponentActivity or use createHealthConnector(activity) instead."
        )
    }
    
    return HealthConnectConnector(HealthConnectorContextProvider.activity)
}

/**
 * Create a HealthConnector with a specific activity
 * This is the recommended way to create the connector on Android
 * 
 * @param activity The ComponentActivity to use for permission requests
 */
fun createHealthConnector(activity: ComponentActivity): HealthConnector {
    if (!HealthConnectorContextProvider.isInitialized) {
        HealthConnectorContextProvider.initialize(activity.applicationContext)
    }
    
    HealthConnectorContextProvider.activity = activity
    
    return HealthConnectConnector(activity)
}
