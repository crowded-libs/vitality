@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package vitality

/**
 * Represents the read/write capabilities of a health data type on a specific platform
 */
data class HealthDataTypeCapabilities(
    val dataType: HealthDataType,
    val canRead: Boolean,
    val canWrite: Boolean,
    val platformNotes: String? = null
)

/**
 * Provides information about health data type capabilities for the current platform
 */
expect object HealthDataTypeCapabilityProvider {
    /**
     * Get the capabilities for a specific health data type
     */
    fun getCapabilities(dataType: HealthDataType): HealthDataTypeCapabilities
    
    /**
     * Get capabilities for all health data types
     */
    fun getAllCapabilities(): List<HealthDataTypeCapabilities>
    
    /**
     * Check if a data type supports read access
     */
    fun canRead(dataType: HealthDataType): Boolean
    
    /**
     * Check if a data type supports write access  
     */
    fun canWrite(dataType: HealthDataType): Boolean
}