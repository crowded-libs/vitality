package vitality.helpers

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.HealthKit.*
import platform.Foundation.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Generic observer for HealthKit category types.
 * Sets up HKObserverQuery for real-time updates and queries for category samples.
 *
 * @param T The type of data to emit
 * @param categoryTypeIdentifier The HealthKit category type identifier string
 * @param lookbackDuration How far back to look for samples
 * @param valueExtractor Function to extract data from HKCategorySample
 */
internal fun <T> observeCategoryType(
    categoryTypeIdentifier: String?,
    lookbackDuration: Duration = 1.hours,
    valueExtractor: (HKCategorySample) -> T
): Flow<T> = callbackFlow {
    val categoryType = categoryTypeIdentifier?.let { 
        HKCategoryType.categoryTypeForIdentifier(it) 
    } ?: return@callbackFlow
    
    val healthStore = HKHealthStore()
    
    val observerQuery = HKObserverQuery(
        sampleType = categoryType,
        predicate = null
    ) { _, completionHandler, error ->
        if (error == null) {
            val now = NSDate()
            val startTime = now.dateByAddingTimeInterval(-lookbackDuration.inWholeSeconds.toDouble())
            val predicate = HKQuery.predicateForSamplesWithStartDate(
                startTime, 
                endDate = now, 
                options = HKQueryOptionNone
            )
            
            val sortDescriptor = NSSortDescriptor.sortDescriptorWithKey("startDate", ascending = true)
            val sampleQuery = HKSampleQuery(
                categoryType,
                predicate,
                HKObjectQueryNoLimit,
                listOf(sortDescriptor)
            ) { _, samples, _ ->
                samples?.forEach { sample ->
                    (sample as? HKCategorySample)?.let {
                        trySend(valueExtractor(it))
                    }
                }
            }
            
            healthStore.executeQuery(sampleQuery)
        }
        completionHandler?.invoke()
    }
    
    healthStore.executeQuery(observerQuery)
    
    awaitClose {
        healthStore.stopQuery(observerQuery)
    }
}