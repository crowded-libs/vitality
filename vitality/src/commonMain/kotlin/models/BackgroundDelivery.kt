package vitality.models

/**
 * Background delivery frequency options
 */
enum class BackgroundDeliveryFrequency {
    IMMEDIATE,  // As soon as new data is available
    HOURLY,     // At least once per hour
    DAILY,      // At least once per day
    WEEKLY      // At least once per week
}