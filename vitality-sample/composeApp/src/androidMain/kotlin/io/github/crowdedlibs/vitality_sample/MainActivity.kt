package io.github.crowdedlibs.vitality_sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import vitality.HealthConnectorContextProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Set the activity for Health Connect permission requests
        HealthConnectorContextProvider.activity = this
        
        // Create the HealthConnector here to ensure activity result registration
        // happens before onCreate() completes
        val healthConnector = HealthConnectorProvider.getOrCreate(this)

        setContent {
            App(healthConnector)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    // For preview, we can't create a real HealthConnector
    // The preview will show the UI but won't have functional health operations
}