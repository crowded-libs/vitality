package io.github.crowdedlibs.vitality_sample

import android.app.Application
import vitality.HealthConnectorContextProvider

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the HealthConnectorContextProvider with the application context
        // This is required for Health Connect to work properly on Android
        HealthConnectorContextProvider.initialize(applicationContext)
    }
}