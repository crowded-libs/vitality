package vitality.helpers

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.time.Clock
import kotlin.time.Instant
import vitality.WorkoutState
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaInstant

/**
 * Generic polling observer for Health Connect records.
 * Continuously polls for new records at the specified interval and emits converted data.
 *
 * @param T The type of Health Connect record to read
 * @param R The type of data to emit
 * @param samplingInterval How often to poll for new data
 * @param lookbackDuration How far back to look for records (from current time)
 * @param converter Function to convert Health Connect records to the desired data type
 */
internal inline fun <reified T : Record, R> HealthConnectClient.observeRecords(
    samplingInterval: Duration,
    lookbackDuration: Duration = 5.minutes,
    crossinline converter: (T) -> R
): Flow<R> = flow {
    while (currentCoroutineContext().isActive) {
        try {
            val endTime = Clock.System.now()
            val startTime = endTime - lookbackDuration
            
            val response = readRecords(
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startTime.toJavaInstant(),
                        endTime.toJavaInstant()
                    )
                )
            )
            
            response.records.forEach { record ->
                emit(converter(record))
            }
        } catch (_: Exception) {
            // Silently ignore errors during polling
            // This matches existing behavior in HealthConnectConnector
        }
        delay(samplingInterval)
    }
}

/**
 * Specialized polling observer for workout session records.
 * Only polls while the workout is in RUNNING state and emits the latest record.
 *
 * @param T The type of Health Connect record to read
 * @param R The type of data to emit
 * @param startTime The start time of the workout session
 * @param workoutState Function to get the current workout state
 * @param pollingInterval How often to poll for new data (in milliseconds)
 * @param extractor Function to extract data from the latest record
 */
internal inline fun <reified T : Record, R> HealthConnectClient.observeWorkoutRecords(
    startTime: Instant,
    crossinline workoutState: () -> WorkoutState,
    pollingInterval: Long = 30.seconds.inWholeMilliseconds,
    crossinline extractor: (T) -> R?
): Flow<R> = flow {
    while (workoutState() == WorkoutState.RUNNING) {
        try {
            val records = readRecords(
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = TimeRangeFilter.after(startTime.toJavaInstant())
                )
            )
            
            records.records.lastOrNull()?.let { record ->
                extractor(record)?.let { emit(it) }
            }
        } catch (_: Exception) {
            // Silently ignore errors during polling
        }
        delay(pollingInterval)
    }
}

/**
 * Polling observer with aggregation support.
 * Useful for records that need to be aggregated (like calories from multiple sources).
 *
 * @param samplingInterval How often to poll for new data
 * @param lookbackDuration How far back to look for records
 * @param recordTypes List of record types to read
 * @param aggregator Function to aggregate records into the desired data type
 */
internal fun <R> HealthConnectClient.observeAggregatedRecords(
    samplingInterval: Duration,
    lookbackDuration: Duration = 5.minutes,
    recordTypes: List<KClass<out Record>>,
    aggregator: suspend (startTime: Instant, endTime: Instant, records: Map<KClass<out Record>, List<Record>>) -> List<R>
): Flow<R> = flow {
    while (currentCoroutineContext().isActive) {
        try {
            val endTime = Clock.System.now()
            val startTime = endTime - lookbackDuration
            
            val allRecords = mutableMapOf<KClass<out Record>, List<Record>>()
            
            recordTypes.forEach { recordType ->
                val response = readRecords(
                    ReadRecordsRequest(
                        recordType = recordType,
                        timeRangeFilter = TimeRangeFilter.between(
                            startTime.toJavaInstant(),
                            endTime.toJavaInstant()
                        )
                    )
                )
                allRecords[recordType] = response.records
            }
            
            val aggregatedData = aggregator(startTime, endTime, allRecords)
            aggregatedData.forEach { emit(it) }
            
        } catch (_: Exception) {
            // Silently ignore errors during polling
        }
        delay(samplingInterval)
    }
}