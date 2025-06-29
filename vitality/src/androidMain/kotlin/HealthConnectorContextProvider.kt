package vitality

import android.content.Context
import androidx.activity.ComponentActivity

/**
 * Singleton to provide application context and activity to the Health Connector
 * This allows the factory method to access these without changing its signature
 */
object HealthConnectorContextProvider {
    private var _applicationContext: Context? = null
    private var _currentActivity: ComponentActivity? = null
    
    /**
     * The application context
     * @throws IllegalStateException if the context hasn't been initialized
     */
    val applicationContext: Context
        get() = _applicationContext ?: throw IllegalStateException(
            "HealthConnectorContextProvider not initialized. Call initialize() with your application context first."
        )
    
    /**
     * The current activity for permission requests
     * Set this from your Activity's onCreate() or when creating the HealthConnector
     * @throws IllegalStateException when getting if the activity hasn't been set
     */
    var activity: ComponentActivity
        get() = _currentActivity ?: throw IllegalStateException(
            "Activity not set in HealthConnectorContextProvider. Set the activity property with your ComponentActivity first."
        )
        set(value) {
            _currentActivity = value
        }
    
    /**
     * Whether the context provider has been initialized
     */
    val isInitialized: Boolean
        get() = _applicationContext != null
    
    /**
     * Whether an activity has been set
     */
    val hasActivity: Boolean
        get() = _currentActivity != null
    
    /**
     * Initialize with the application context
     * This should be called early in the application lifecycle, typically in Application.onCreate()
     */
    fun initialize(context: Context) {
        _applicationContext = context.applicationContext
    }
}