package io.github.crowdedlibs.vitality_sample.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.crowdedlibs.vitality_sample.repository.HealthRepository
import io.github.crowdedlibs.vitality_sample.state.HealthDemoState
import io.github.crowdedlibs.vitality_sample.state.HealthTab
import io.github.crowdedlibs.vitality_sample.state.WorkoutSession
import io.github.crowdedlibs.vitality_sample.state.WorkoutState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import vitality.HealthConnector
import vitality.HealthDataType
import vitality.HealthDataTypeCapabilityProvider
import vitality.HealthPermission
import vitality.WeightUnit
import vitality.WorkoutType
import vitality.models.CalorieData
import vitality.models.HeartRateData
import vitality.models.StepsData
import vitality.models.WorkoutData

@OptIn(ExperimentalTime::class)
class HealthViewModel(
    healthConnector: HealthConnector
) : ViewModel() {
    private val repository = HealthRepository(healthConnector)
    
    private val _state = MutableStateFlow(HealthDemoState())
    val state: StateFlow<HealthDemoState> = _state.asStateFlow()
    
    private var monitoringJobs = mutableMapOf<HealthDataType, Job>()
    private var workoutMonitoringJob: Job? = null
    
    init {
        viewModelScope.launch {
            // Add a small delay to ensure the activity is ready
            delay(100)
            
            repository.initialize()
                .onSuccess {
                    loadAvailablePermissions()
                    checkCurrentPermissionStatus()
                }
                .onFailure { error ->
                    showError("Failed to initialize health connector: ${error.message}")
                    // Still try to load permissions in case partial initialization worked
                    loadAvailablePermissions()
                }
        }
    }
    
    fun selectTab(tab: HealthTab) {
        _state.update { it.copy(selectedTab = tab) }
    }
    
    // Permissions
    private suspend fun loadAvailablePermissions() {
        val dataTypes = repository.getAvailableDataTypes()
        
        if (dataTypes.isEmpty()) {
            // Don't show error on initial load, just skip
            return
        }
        
        // Generate permissions based on platform capabilities
        val permissions = dataTypes.flatMap { dataType ->
            val capabilities = HealthDataTypeCapabilityProvider.getCapabilities(dataType)
            val perms = mutableListOf<HealthPermission>()
            
            if (capabilities.canRead) {
                perms.add(HealthPermission(dataType, HealthPermission.AccessType.READ))
            }
            if (capabilities.canWrite) {
                perms.add(HealthPermission(dataType, HealthPermission.AccessType.WRITE))
            }
            
            perms
        }
        _state.update { it.copy(availablePermissions = permissions) }
    }
    
    private suspend fun checkCurrentPermissionStatus() {
        // Check status of all available permissions
        val allPermissions = _state.value.availablePermissions.toSet()
        if (allPermissions.isNotEmpty()) {
            val status = repository.checkPermissions(allPermissions)
            _state.update { it.copy(permissionStatus = status) }
        }
    }
    
    fun refreshPermissions() {
        viewModelScope.launch {
            repository.initialize()
                .onSuccess {
                    loadAvailablePermissions()
                    checkCurrentPermissionStatus()
                }
                .onFailure { error ->
                    showError("Failed to connect to Health Connect: ${error.message}")
                }
        }
    }
    
    fun togglePermission(permission: HealthPermission) {
        _state.update { state ->
            val selected = if (permission in state.selectedPermissions) {
                state.selectedPermissions - permission
            } else {
                state.selectedPermissions + permission
            }
            state.copy(selectedPermissions = selected)
        }
    }
    
    fun selectAllPermissions() {
        _state.update { state ->
            state.copy(selectedPermissions = state.availablePermissions.toSet())
        }
    }
    
    fun deselectAllPermissions() {
        _state.update { state ->
            state.copy(selectedPermissions = emptySet())
        }
    }
    
    fun requestPermissions() {
        viewModelScope.launch {
            _state.update { it.copy(isRequestingPermissions = true) }
            
            repository.requestPermissions(_state.value.selectedPermissions).fold(
                onSuccess = { result ->
                    // Check the actual permission status after request
                    val status = repository.checkPermissions(_state.value.selectedPermissions)
                    
                    // Clear selected permissions after successful request
                    _state.update {
                        it.copy(
                            isRequestingPermissions = false,
                            permissionStatus = status,
                            selectedPermissions = emptySet(),
                            successMessage = "Permissions updated: ${result.granted.size} granted, ${result.denied.size} denied"
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isRequestingPermissions = false,
                            errorMessage = "Failed to request permissions: ${error.message}"
                        )
                    }
                }
            )
        }
    }
    
    // Read Data
    fun loadHealthData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingData = true) }
            
            // Load all data in parallel
            launch {
                repository.readLatestHeartRate().fold(
                    onSuccess = { data ->
                        _state.update { it.copy(latestHeartRate = data) }
                    },
                    onFailure = { /* Ignore individual failures */ }
                )
            }
            
            launch {
                repository.readStepsToday().fold(
                    onSuccess = { data ->
                        _state.update { it.copy(todaySteps = data) }
                    },
                    onFailure = { /* Ignore individual failures */ }
                )
            }
            
            launch {
                repository.readCaloriesToday().fold(
                    onSuccess = { data ->
                        _state.update { it.copy(todayCalories = data) }
                    },
                    onFailure = { /* Ignore individual failures */ }
                )
            }
            
            launch {
                repository.readLatestWeight().fold(
                    onSuccess = { data ->
                        _state.update { it.copy(latestWeight = data) }
                    },
                    onFailure = { /* Ignore individual failures */ }
                )
            }
            
            launch {
                repository.readRecentWorkouts().fold(
                    onSuccess = { data ->
                        _state.update { it.copy(recentWorkouts = data) }
                    },
                    onFailure = { /* Ignore individual failures */ }
                )
            }
            
            delay(1000) // Give time for all requests to complete
            _state.update { it.copy(isLoadingData = false) }
        }
    }
    
    // Write Data
    fun updateWeightInput(value: String) {
        _state.update { it.copy(weightInput = value) }
    }
    
    fun updateHeartRateInput(value: String) {
        _state.update { it.copy(heartRateInput = value) }
    }
    
    fun updateStepsInput(value: String) {
        _state.update { it.copy(stepsInput = value) }
    }
    
    fun writeWeight() {
        val weight = _state.value.weightInput.toDoubleOrNull() ?: return
        
        viewModelScope.launch {
            _state.update { it.copy(isWritingData = true) }
            
            repository.writeWeight(weight, WeightUnit.KILOGRAMS).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isWritingData = false,
                            weightInput = "",
                            successMessage = "Weight saved successfully"
                        )
                    }
                    loadHealthData() // Reload to show new data
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isWritingData = false,
                            errorMessage = "Failed to save weight: ${error.message}"
                        )
                    }
                }
            )
        }
    }
    
    fun writeHeartRate() {
        val heartRate = _state.value.heartRateInput.toIntOrNull() ?: return
        
        viewModelScope.launch {
            _state.update { it.copy(isWritingData = true) }
            
            repository.writeHeartRate(heartRate).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isWritingData = false,
                            heartRateInput = "",
                            successMessage = "Heart rate saved successfully"
                        )
                    }
                    loadHealthData()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isWritingData = false,
                            errorMessage = "Failed to save heart rate: ${error.message}"
                        )
                    }
                }
            )
        }
    }
    
    fun writeSteps() {
        val steps = _state.value.stepsInput.toIntOrNull() ?: return
        
        viewModelScope.launch {
            _state.update { it.copy(isWritingData = true) }
            
            repository.writeSteps(steps).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isWritingData = false,
                            stepsInput = "",
                            successMessage = "Steps saved successfully"
                        )
                    }
                    loadHealthData()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isWritingData = false,
                            errorMessage = "Failed to save steps: ${error.message}"
                        )
                    }
                }
            )
        }
    }
    
    // Real-time Monitoring
    fun toggleMonitoring(dataType: HealthDataType) {
        if (dataType in _state.value.monitoringDataTypes) {
            stopMonitoring(dataType)
        } else {
            startMonitoring(dataType)
        }
    }
    
    private fun startMonitoring(dataType: HealthDataType) {
        _state.update {
            it.copy(monitoringDataTypes = it.monitoringDataTypes + dataType)
        }
        
        monitoringJobs[dataType]?.cancel()
        monitoringJobs[dataType] = viewModelScope.launch {
            when (dataType) {
                HealthDataType.HeartRate -> {
                    repository.observeHealthData<HeartRateData>(dataType)?.collect { data ->
                        _state.update { state ->
                            state.copy(
                                liveHeartRateData = (state.liveHeartRateData + data).takeLast(20)
                            )
                        }
                    }
                }
                HealthDataType.Steps -> {
                    repository.observeHealthData<StepsData>(dataType)?.collect { data ->
                        _state.update { state ->
                            state.copy(
                                liveStepsData = (state.liveStepsData + data).takeLast(20)
                            )
                        }
                    }
                }
                HealthDataType.Calories -> {
                    repository.observeHealthData<CalorieData>(dataType)?.collect { data ->
                        _state.update { state ->
                            state.copy(
                                liveCaloriesData = (state.liveCaloriesData + data).takeLast(20)
                            )
                        }
                    }
                }
                else -> { /* Not implemented for other types */ }
            }
        }
        
        _state.update { it.copy(isMonitoring = it.monitoringDataTypes.isNotEmpty()) }
    }
    
    private fun stopMonitoring(dataType: HealthDataType) {
        monitoringJobs[dataType]?.cancel()
        monitoringJobs.remove(dataType)
        
        _state.update {
            it.copy(
                monitoringDataTypes = it.monitoringDataTypes - dataType,
                isMonitoring = (it.monitoringDataTypes - dataType).isNotEmpty()
            )
        }
    }
    
    fun stopAllMonitoring() {
        monitoringJobs.values.forEach { it.cancel() }
        monitoringJobs.clear()
        
        _state.update {
            it.copy(
                monitoringDataTypes = emptySet(),
                isMonitoring = false,
                liveHeartRateData = emptyList(),
                liveStepsData = emptyList(),
                liveCaloriesData = emptyList()
            )
        }
    }
    
    // Workout Management
    fun selectWorkoutType(type: WorkoutType) {
        _state.update { it.copy(selectedWorkoutType = type) }
    }
    
    fun startWorkout() {
        // Reset workout totals for mock data generator
        workoutTotalSteps = 0
        workoutTotalCalories = 0.0
        workoutTotalDistance = 0f
        
        viewModelScope.launch {
            repository.startWorkoutSession(_state.value.selectedWorkoutType).fold(
                onSuccess = { session ->
                    val workoutSession = WorkoutSession(
                        sessionId = session.id,
                        type = _state.value.selectedWorkoutType,
                        startTime = Clock.System.now().toEpochMilliseconds()
                    )
                    _state.update {
                        it.copy(
                            activeWorkoutSession = workoutSession,
                            successMessage = "Workout started"
                        )
                    }
                    startWorkoutMonitoring()
                },
                onFailure = { error ->
                    showError("Failed to start workout: ${error.message}")
                }
            )
        }
    }
    
    private fun startWorkoutMonitoring() {
        workoutMonitoringJob?.cancel()
        workoutMonitoringJob = viewModelScope.launch {
            repository.observeActiveWorkout()?.collect { workoutData ->
                updateWorkoutMetrics(workoutData)
            }
        }
    }
    
    private fun updateWorkoutMetrics(workoutData: WorkoutData) {
        // Update workout metrics based on incoming data
        _state.update { state ->
            state.copy(
                activeWorkoutSession = state.activeWorkoutSession?.copy(
                    duration = workoutData.duration?.inWholeMilliseconds ?: state.activeWorkoutSession.duration,
                    heartRate = workoutData.averageHeartRate ?: state.activeWorkoutSession.heartRate,
                    calories = workoutData.activeCalories?.toInt() ?: state.activeWorkoutSession.calories,
                    distance = workoutData.totalDistance?.toFloat() ?: state.activeWorkoutSession.distance,
                    steps = workoutData.stepCount ?: state.activeWorkoutSession.steps
                )
            )
        }
    }
    
    fun pauseWorkout() {
        val sessionId = _state.value.activeWorkoutSession?.sessionId ?: return
        
        viewModelScope.launch {
            repository.pauseWorkoutSession(sessionId).fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(
                            activeWorkoutSession = state.activeWorkoutSession?.copy(
                                state = WorkoutState.PAUSED
                            )
                        )
                    }
                },
                onFailure = { error ->
                    showError("Failed to pause workout: ${error.message}")
                }
            )
        }
    }
    
    fun resumeWorkout() {
        val sessionId = _state.value.activeWorkoutSession?.sessionId ?: return
        
        viewModelScope.launch {
            repository.resumeWorkoutSession(sessionId).fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(
                            activeWorkoutSession = state.activeWorkoutSession?.copy(
                                state = WorkoutState.RUNNING
                            )
                        )
                    }
                },
                onFailure = { error ->
                    showError("Failed to resume workout: ${error.message}")
                }
            )
        }
    }
    
    fun endWorkout() {
        val sessionId = _state.value.activeWorkoutSession?.sessionId ?: return
        
        viewModelScope.launch {
            repository.endWorkoutSession(sessionId).fold(
                onSuccess = {
                    workoutMonitoringJob?.cancel()
                    _state.update {
                        it.copy(
                            activeWorkoutSession = null,
                            workoutMetrics = io.github.crowdedlibs.vitality_sample.state.WorkoutMetrics(),
                            successMessage = "Workout ended and saved"
                        )
                    }
                    loadHealthData() // Reload to show new workout
                },
                onFailure = { error ->
                    showError("Failed to end workout: ${error.message}")
                }
            )
        }
    }
    
    // Mock Data Generator
    fun toggleMockDataGeneration() {
        _state.update {
            it.copy(isGeneratingMockData = !it.isGeneratingMockData)
        }
        
        if (_state.value.isGeneratingMockData) {
            startMockDataGeneration()
        } else {
            stopMockDataGeneration()
        }
    }
    
    private var mockDataJob: Job? = null
    
    private var workoutTotalSteps = 0
    private var workoutTotalCalories = 0.0
    private var workoutTotalDistance = 0f
    
    private fun startMockDataGeneration() {
        mockDataJob?.cancel()
        mockDataJob = viewModelScope.launch {
            while (_state.value.isGeneratingMockData) {
                // Generate random health data
                val randomHeartRate = (60..100).random()
                val randomSteps = (10..50).random()
                val randomActiveCalories = (1..5).random().toDouble()
                val randomBasalCalories = (0.5 + (0..10).random() * 0.1)
                val randomDistance = (5..20).random().toFloat() // meters
                
                // Write general health data
                repository.writeHeartRate(randomHeartRate)
                repository.writeSteps(randomSteps)
                repository.writeCalories(randomActiveCalories, randomBasalCalories)
                
                // If there's an active workout session, update its metrics directly
                _state.value.activeWorkoutSession?.let { session ->
                    if (session.state == WorkoutState.RUNNING) {
                        workoutTotalSteps += randomSteps
                        workoutTotalCalories += randomActiveCalories
                        workoutTotalDistance += randomDistance
                        
                        // Calculate current duration
                        val currentTime = Clock.System.now().toEpochMilliseconds()
                        val duration = currentTime - session.startTime
                        
                        // Update the workout session with simulated metrics
                        _state.update { state ->
                            state.copy(
                                activeWorkoutSession = session.copy(
                                    duration = duration,
                                    heartRate = randomHeartRate,
                                    calories = workoutTotalCalories.toInt(),
                                    steps = workoutTotalSteps,
                                    distance = workoutTotalDistance
                                ),
                                // Also update workoutMetrics for consistency
                                workoutMetrics = io.github.crowdedlibs.vitality_sample.state.WorkoutMetrics(
                                    duration = duration,
                                    distance = workoutTotalDistance.toDouble(),
                                    calories = workoutTotalCalories,
                                    heartRate = randomHeartRate,
                                    pace = if (workoutTotalDistance > 0 && duration > 0) {
                                        (duration / 60000.0) / (workoutTotalDistance / 1000.0) // minutes per km
                                    } else null,
                                    cadence = null // Not simulated
                                )
                            )
                        }
                    }
                }
                
                delay(_state.value.mockDataInterval)
            }
        }
    }
    
    private fun stopMockDataGeneration() {
        mockDataJob?.cancel()
        mockDataJob = null
    }
    
    // Helper functions
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
    
    fun clearSuccess() {
        _state.update { it.copy(successMessage = null) }
    }
    
    private fun showError(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopAllMonitoring()
        stopMockDataGeneration()
        workoutMonitoringJob?.cancel()
    }
}