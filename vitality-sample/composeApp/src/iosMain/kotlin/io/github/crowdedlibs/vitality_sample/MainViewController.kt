package io.github.crowdedlibs.vitality_sample

import androidx.compose.ui.window.ComposeUIViewController
import vitality.createHealthConnector

fun MainViewController() = ComposeUIViewController { 
    val healthConnector = createHealthConnector()
    App(healthConnector) 
}