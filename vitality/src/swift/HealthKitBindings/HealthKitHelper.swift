import Foundation
import HealthKit
import CoreLocation

// MARK: - Sample Metadata
@objc public class SampleMetadata: NSObject {
    @objc public let uuid: String
    @objc public let sourceRevision: SourceRevision?
    @objc public let device: DeviceInfo?
    @objc public let metadata: [String: Any]
    
    init(sample: HKSample) {
        self.uuid = sample.uuid.uuidString
        self.sourceRevision = SourceRevision(from: sample.sourceRevision)
        self.device = DeviceInfo(from: sample.device)
        self.metadata = sample.metadata ?? [:]
        super.init()
    }
}

@objc public class SourceRevision: NSObject {
    @objc public let sourceName: String
    @objc public let bundleIdentifier: String?
    @objc public let version: String?
    @objc public let productType: String?
    @objc public let systemVersion: String?
    @objc public let operatingSystemVersion: String?
    
    init(from sourceRevision: HKSourceRevision) {
        self.sourceName = sourceRevision.source.name
        self.bundleIdentifier = sourceRevision.source.bundleIdentifier
        self.version = sourceRevision.version
        self.productType = sourceRevision.productType
        self.systemVersion = nil // systemVersion is not available in HKSourceRevision
        self.operatingSystemVersion = "\(sourceRevision.operatingSystemVersion.majorVersion).\(sourceRevision.operatingSystemVersion.minorVersion).\(sourceRevision.operatingSystemVersion.patchVersion)"
        super.init()
    }
}

@objc public class DeviceInfo: NSObject {
    @objc public let name: String?
    @objc public let manufacturer: String?
    @objc public let model: String?
    @objc public let hardwareVersion: String?
    @objc public let firmwareVersion: String?
    @objc public let softwareVersion: String?
    @objc public let localIdentifier: String?
    @objc public let udiDeviceIdentifier: String?
    
    init?(from device: HKDevice?) {
        guard let device = device else { return nil }
        self.name = device.name
        self.manufacturer = device.manufacturer
        self.model = device.model
        self.hardwareVersion = device.hardwareVersion
        self.firmwareVersion = device.firmwareVersion
        self.softwareVersion = device.softwareVersion
        self.localIdentifier = device.localIdentifier
        self.udiDeviceIdentifier = device.udiDeviceIdentifier
        super.init()
    }
}

// MARK: - Delegate Protocol for Kotlin Interop
@objc public protocol WorkoutDataDelegate: AnyObject {
    @objc func workoutDidUpdateHeartRate(_ heartRate: Double, timestamp: Date, metadata: SampleMetadata?)
    @objc func workoutDidUpdateCalories(_ activeCalories: Double, basalCalories: Double, timestamp: Date)
    @objc func workoutDidUpdateDistance(_ distance: Double, timestamp: Date, metadata: SampleMetadata?)
    @objc func workoutDidUpdateElevation(_ elevation: Double, timestamp: Date, metadata: SampleMetadata?)
    @objc func workoutDidUpdateCadence(_ cadence: Double, timestamp: Date, metadata: SampleMetadata?)
    @objc func workoutDidUpdatePower(_ power: Double, timestamp: Date, metadata: SampleMetadata?)
    @objc func workoutDidUpdateSpeed(_ speed: Double, timestamp: Date, metadata: SampleMetadata?)
    @objc func workoutDidUpdateStrokeCount(_ strokeCount: Double, timestamp: Date, metadata: SampleMetadata?)
    @objc func workoutDidChangeState(_ state: String)
    @objc func workoutDidFailWithError(_ error: String)
}

// MARK: - Workout Session Manager with Live Data
@objc public class WorkoutSessionManager: NSObject {
    private let healthStore = HKHealthStore()
    private var workoutBuilder: HKWorkoutBuilder?
    
    // Live data queries
    private var heartRateQuery: HKAnchoredObjectQuery?
    private var activeCaloriesQuery: HKAnchoredObjectQuery?
    private var basalCaloriesQuery: HKAnchoredObjectQuery?
    private var distanceQuery: HKAnchoredObjectQuery?
    private var elevationQuery: HKAnchoredObjectQuery?
    private var cadenceQuery: HKAnchoredObjectQuery?
    private var powerQuery: HKAnchoredObjectQuery?
    private var speedQuery: HKAnchoredObjectQuery?
    private var strokeCountQuery: HKAnchoredObjectQuery?
    
    // Anchors for incremental updates
    private var heartRateAnchor: HKQueryAnchor?
    private var activeCaloriesAnchor: HKQueryAnchor?
    private var basalCaloriesAnchor: HKQueryAnchor?
    private var distanceAnchor: HKQueryAnchor?
    private var elevationAnchor: HKQueryAnchor?
    private var cadenceAnchor: HKQueryAnchor?
    private var powerAnchor: HKQueryAnchor?
    private var speedAnchor: HKQueryAnchor?
    private var strokeCountAnchor: HKQueryAnchor?
    
    @objc public weak var delegate: WorkoutDataDelegate?
    @objc public private(set) var sessionId: String = ""
    @objc public private(set) var isActive: Bool = false
    
    // Workout configuration
    private var workoutConfiguration: HKWorkoutConfiguration?
    private var workoutStartDate: Date?
    
    @objc public private(set) var currentHeartRate: Double = 0
    @objc public private(set) var currentCadence: Double = 0
    @objc public private(set) var currentPower: Double = 0
    @objc public private(set) var currentSpeed: Double = 0
    @objc public private(set) var currentElevation: Double = 0
    
    private var lastElevation: Double?
    
    override init() {
        super.init()
        self.sessionId = UUID().uuidString
    }
    
    // MARK: - Start Workout
    @objc public func startWorkout(activityType: HKWorkoutActivityType, locationType: HKWorkoutSessionLocationType) {
        let configuration = HKWorkoutConfiguration()
        configuration.activityType = activityType
        configuration.locationType = locationType
        self.workoutConfiguration = configuration
        
        // Create workout builder
        workoutBuilder = HKWorkoutBuilder(healthStore: healthStore, configuration: configuration, device: .local())
        
        // Start collection
        let startDate = Date()
        workoutStartDate = startDate
        
        workoutBuilder?.beginCollection(withStart: startDate) { [weak self] success, error in
            if success {
                self?.isActive = true
                self?.delegate?.workoutDidChangeState("RUNNING")
                self?.startDataCollection()
            } else if let error = error {
                self?.delegate?.workoutDidFailWithError(error.localizedDescription)
            }
        }
    }
    
    // MARK: - Start Data Collection
    private func startDataCollection() {
        // Start live data queries using HKAnchoredObjectQuery for real-time updates
        startHeartRateQuery()
        startCaloriesQuery()
        startDistanceQuery()
        startElevationQuery()
        startCadenceQuery()
        startPowerQuery()
        startSpeedQuery()
        startStrokeCountQuery()
    }
    
    // MARK: - Live Heart Rate Query
    private func startHeartRateQuery() {
        guard let startDate = workoutStartDate else { return }
        
        let heartRateType = HKQuantityType.quantityType(forIdentifier: .heartRate)!
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: nil, options: .strictStartDate)
        
        // Create anchored object query for live updates
        let query = HKAnchoredObjectQuery(
            type: heartRateType,
            predicate: predicate,
            anchor: heartRateAnchor,
            limit: HKObjectQueryNoLimit
        ) { [weak self] query, samples, deletedObjects, anchor, error in
            // Initial results handler
            self?.heartRateAnchor = anchor
            self?.processHeartRateSamples(samples)
        }
        
        // Set up update handler for continuous updates
        query.updateHandler = { [weak self] query, samples, deletedObjects, anchor, error in
            self?.heartRateAnchor = anchor
            self?.processHeartRateSamples(samples)
        }
        
        heartRateQuery = query
        healthStore.execute(query)
    }
    
    private func processHeartRateSamples(_ samples: [HKSample]?) {
        guard let samples = samples as? [HKQuantitySample], !samples.isEmpty else { return }
        
        // Process all new samples
        for sample in samples {
            let heartRate = sample.quantity.doubleValue(for: HKUnit.count().unitDivided(by: .minute()))
            let metadata = SampleMetadata(sample: sample)
            
            DispatchQueue.main.async { [weak self] in
                self?.currentHeartRate = heartRate
                // Use actual sample timestamp
                self?.delegate?.workoutDidUpdateHeartRate(heartRate, timestamp: sample.startDate, metadata: metadata)
            }
        }
    }
    
    // MARK: - Live Calories Query
    private func startCaloriesQuery() {
        guard let startDate = workoutStartDate else { return }
        
        let activeType = HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned)!
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: nil, options: .strictStartDate)
        
        // Create anchored object query for active calories
        let activeQuery = HKAnchoredObjectQuery(
            type: activeType,
            predicate: predicate,
            anchor: activeCaloriesAnchor,
            limit: HKObjectQueryNoLimit
        ) { [weak self] query, samples, deletedObjects, anchor, error in
            self?.activeCaloriesAnchor = anchor
            self?.processActiveCaloriesSamples(samples)
        }
        
        activeQuery.updateHandler = { [weak self] query, samples, deletedObjects, anchor, error in
            self?.activeCaloriesAnchor = anchor
            self?.processActiveCaloriesSamples(samples)
        }
        
        activeCaloriesQuery = activeQuery
        healthStore.execute(activeQuery)
        
        // Also query basal calories
        let basalType = HKQuantityType.quantityType(forIdentifier: .basalEnergyBurned)!
        let basalQuery = HKAnchoredObjectQuery(
            type: basalType,
            predicate: predicate,
            anchor: basalCaloriesAnchor,
            limit: HKObjectQueryNoLimit
        ) { [weak self] query, samples, deletedObjects, anchor, error in
            self?.basalCaloriesAnchor = anchor
            self?.processBasalCaloriesSamples(samples)
        }
        
        basalQuery.updateHandler = { [weak self] query, samples, deletedObjects, anchor, error in
            self?.basalCaloriesAnchor = anchor
            self?.processBasalCaloriesSamples(samples)
        }
        
        basalCaloriesQuery = basalQuery
        healthStore.execute(basalQuery)
    }
    
    private func processActiveCaloriesSamples(_ samples: [HKSample]?) {
        guard let samples = samples as? [HKQuantitySample], !samples.isEmpty else { return }
        
        // Process each sample individually to get proper timestamps
        for sample in samples {
            let calories = sample.quantity.doubleValue(for: .kilocalorie())
            
            DispatchQueue.main.async { [weak self] in
                self?.delegate?.workoutDidUpdateCalories(
                    calories,
                    basalCalories: 0,
                    timestamp: sample.startDate
                )
            }
        }
    }
    
    private func processBasalCaloriesSamples(_ samples: [HKSample]?) {
        guard let samples = samples as? [HKQuantitySample], !samples.isEmpty else { return }
        
        // Process each sample individually to get proper timestamps
        for sample in samples {
            let calories = sample.quantity.doubleValue(for: .kilocalorie())
            
            DispatchQueue.main.async { [weak self] in
                self?.delegate?.workoutDidUpdateCalories(
                    0,
                    basalCalories: calories,
                    timestamp: sample.startDate
                )
            }
        }
    }
    
    // MARK: - Live Distance Query
    private func startDistanceQuery() {
        guard let startDate = workoutStartDate,
              let configuration = workoutConfiguration else { return }
        
        // Select appropriate distance type based on workout activity
        let distanceIdentifier: HKQuantityTypeIdentifier
        switch configuration.activityType {
        case .cycling, .handCycling:
            distanceIdentifier = .distanceCycling
        case .swimming:
            distanceIdentifier = .distanceSwimming
        case .wheelchairWalkPace, .wheelchairRunPace:
            distanceIdentifier = .distanceWheelchair
        case .downhillSkiing, .crossCountrySkiing, .snowboarding:
            distanceIdentifier = .distanceDownhillSnowSports
        default:
            distanceIdentifier = .distanceWalkingRunning
        }
        
        guard let distanceType = HKQuantityType.quantityType(forIdentifier: distanceIdentifier) else { return }
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: nil, options: .strictStartDate)
        
        let query = HKAnchoredObjectQuery(
            type: distanceType,
            predicate: predicate,
            anchor: distanceAnchor,
            limit: HKObjectQueryNoLimit
        ) { [weak self] query, samples, deletedObjects, anchor, error in
            self?.distanceAnchor = anchor
            self?.processDistanceSamples(samples)
        }
        
        query.updateHandler = { [weak self] query, samples, deletedObjects, anchor, error in
            self?.distanceAnchor = anchor
            self?.processDistanceSamples(samples)
        }
        
        distanceQuery = query
        healthStore.execute(query)
    }
    
    private func processDistanceSamples(_ samples: [HKSample]?) {
        guard let samples = samples as? [HKQuantitySample], !samples.isEmpty else { return }
        
        for sample in samples {
            let distance = sample.quantity.doubleValue(for: .meter())
            let metadata = SampleMetadata(sample: sample)
            
            DispatchQueue.main.async { [weak self] in
                self?.delegate?.workoutDidUpdateDistance(distance, timestamp: sample.startDate, metadata: metadata)
            }
        }
    }
    
    // MARK: - Live Elevation Query
    private func startElevationQuery() {
        guard let startDate = workoutStartDate else { return }
        
        guard let elevationType = HKQuantityType.quantityType(forIdentifier: .flightsClimbed) else { return }
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: nil, options: .strictStartDate)
        
        let query = HKAnchoredObjectQuery(
            type: elevationType,
            predicate: predicate,
            anchor: elevationAnchor,
            limit: HKObjectQueryNoLimit
        ) { [weak self] query, samples, deletedObjects, anchor, error in
            self?.elevationAnchor = anchor
            self?.processElevationSamples(samples)
        }
        
        query.updateHandler = { [weak self] query, samples, deletedObjects, anchor, error in
            self?.elevationAnchor = anchor
            self?.processElevationSamples(samples)
        }
        
        elevationQuery = query
        healthStore.execute(query)
    }
    
    private func processElevationSamples(_ samples: [HKSample]?) {
        guard let samples = samples as? [HKQuantitySample], !samples.isEmpty else { return }
        
        for sample in samples {
            let elevation = sample.quantity.doubleValue(for: .meter())
            let metadata = SampleMetadata(sample: sample)
            
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                
                self.currentElevation = elevation
                self.lastElevation = elevation
                
                self.delegate?.workoutDidUpdateElevation(elevation, timestamp: sample.startDate, metadata: metadata)
            }
        }
    }
    
    // MARK: - Live Cadence Query
    private func startCadenceQuery() {
        guard let startDate = workoutStartDate,
              let configuration = workoutConfiguration else { return }
        
        let cadenceIdentifier: HKQuantityTypeIdentifier?
        switch configuration.activityType {
        case .running, .walking:
            if #available(iOS 16.0, *) {
                cadenceIdentifier = .runningStrideLength
            } else {
                cadenceIdentifier = nil
            }
        case .cycling, .handCycling:
            if #available(iOS 17.0, *) {
                cadenceIdentifier = .cyclingCadence
            } else {
                cadenceIdentifier = nil
            }
        default:
            cadenceIdentifier = nil
        }
        
        guard let identifier = cadenceIdentifier,
              let cadenceType = HKQuantityType.quantityType(forIdentifier: identifier) else { return }
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: nil, options: .strictStartDate)
        
        let query = HKAnchoredObjectQuery(
            type: cadenceType,
            predicate: predicate,
            anchor: cadenceAnchor,
            limit: HKObjectQueryNoLimit
        ) { [weak self] query, samples, deletedObjects, anchor, error in
            self?.cadenceAnchor = anchor
            self?.processCadenceSamples(samples)
        }
        
        query.updateHandler = { [weak self] query, samples, deletedObjects, anchor, error in
            self?.cadenceAnchor = anchor
            self?.processCadenceSamples(samples)
        }
        
        cadenceQuery = query
        healthStore.execute(query)
    }
    
    private func processCadenceSamples(_ samples: [HKSample]?) {
        guard let samples = samples as? [HKQuantitySample], !samples.isEmpty else { return }
        
        for sample in samples {
            let cadence = sample.quantity.doubleValue(for: HKUnit.count().unitDivided(by: .minute()))
            let metadata = SampleMetadata(sample: sample)
            
            DispatchQueue.main.async { [weak self] in
                self?.currentCadence = cadence
                self?.delegate?.workoutDidUpdateCadence(cadence, timestamp: sample.startDate, metadata: metadata)
            }
        }
    }
    
    // MARK: - Live Power Query
    private func startPowerQuery() {
        guard let startDate = workoutStartDate else { return }
        
        guard #available(iOS 17.0, *) else { return }
        guard let powerType = HKQuantityType.quantityType(forIdentifier: .cyclingPower) else { return }
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: nil, options: .strictStartDate)
        
        let query = HKAnchoredObjectQuery(
            type: powerType,
            predicate: predicate,
            anchor: powerAnchor,
            limit: HKObjectQueryNoLimit
        ) { [weak self] query, samples, deletedObjects, anchor, error in
            self?.powerAnchor = anchor
            self?.processPowerSamples(samples)
        }
        
        query.updateHandler = { [weak self] query, samples, deletedObjects, anchor, error in
            self?.powerAnchor = anchor
            self?.processPowerSamples(samples)
        }
        
        powerQuery = query
        healthStore.execute(query)
    }
    
    private func processPowerSamples(_ samples: [HKSample]?) {
        guard let samples = samples as? [HKQuantitySample], !samples.isEmpty else { return }
        
        for sample in samples {
            let power: Double
            if #available(iOS 16.0, *) {
                power = sample.quantity.doubleValue(for: .watt())
            } else {
                power = sample.quantity.doubleValue(for: HKUnit.joule().unitDivided(by: .second()))
            }
            let metadata = SampleMetadata(sample: sample)
            
            DispatchQueue.main.async { [weak self] in
                self?.currentPower = power
                self?.delegate?.workoutDidUpdatePower(power, timestamp: sample.startDate, metadata: metadata)
            }
        }
    }
    
    // MARK: - Live Speed Query
    private func startSpeedQuery() {
        guard let startDate = workoutStartDate else { return }
        
        guard #available(iOS 16.0, *) else { return }
        guard let speedType = HKQuantityType.quantityType(forIdentifier: .runningSpeed) else { return }
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: nil, options: .strictStartDate)
        
        let query = HKAnchoredObjectQuery(
            type: speedType,
            predicate: predicate,
            anchor: speedAnchor,
            limit: HKObjectQueryNoLimit
        ) { [weak self] query, samples, deletedObjects, anchor, error in
            self?.speedAnchor = anchor
            self?.processSpeedSamples(samples)
        }
        
        query.updateHandler = { [weak self] query, samples, deletedObjects, anchor, error in
            self?.speedAnchor = anchor
            self?.processSpeedSamples(samples)
        }
        
        speedQuery = query
        healthStore.execute(query)
    }
    
    private func processSpeedSamples(_ samples: [HKSample]?) {
        guard let samples = samples as? [HKQuantitySample], !samples.isEmpty else { return }
        
        for sample in samples {
            let speed = sample.quantity.doubleValue(for: HKUnit.meter().unitDivided(by: .second()))
            let metadata = SampleMetadata(sample: sample)
            
            DispatchQueue.main.async { [weak self] in
                self?.currentSpeed = speed
                self?.delegate?.workoutDidUpdateSpeed(speed, timestamp: sample.startDate, metadata: metadata)
            }
        }
    }
    
    // MARK: - Live Stroke Count Query
    private func startStrokeCountQuery() {
        guard let startDate = workoutStartDate else { return }
        
        guard let strokeType = HKQuantityType.quantityType(forIdentifier: .swimmingStrokeCount) else { return }
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: nil, options: .strictStartDate)
        
        let query = HKAnchoredObjectQuery(
            type: strokeType,
            predicate: predicate,
            anchor: strokeCountAnchor,
            limit: HKObjectQueryNoLimit
        ) { [weak self] query, samples, deletedObjects, anchor, error in
            self?.strokeCountAnchor = anchor
            self?.processStrokeCountSamples(samples)
        }
        
        query.updateHandler = { [weak self] query, samples, deletedObjects, anchor, error in
            self?.strokeCountAnchor = anchor
            self?.processStrokeCountSamples(samples)
        }
        
        strokeCountQuery = query
        healthStore.execute(query)
    }
    
    private func processStrokeCountSamples(_ samples: [HKSample]?) {
        guard let samples = samples as? [HKQuantitySample], !samples.isEmpty else { return }
        
        for sample in samples {
            let strokes = sample.quantity.doubleValue(for: .count())
            let metadata = SampleMetadata(sample: sample)
            
            DispatchQueue.main.async { [weak self] in
                self?.delegate?.workoutDidUpdateStrokeCount(strokes, timestamp: sample.startDate, metadata: metadata)
            }
        }
    }
    
    // MARK: - Control Methods
    @objc public func pauseWorkout() {
        // Stop all live queries
        stopQueries()
        isActive = false
        delegate?.workoutDidChangeState("PAUSED")
    }
    
    @objc public func resumeWorkout() {
        isActive = true
        startDataCollection()
        delegate?.workoutDidChangeState("RUNNING")
    }
    
    @objc public func endWorkout(completion: @escaping (Bool, Error?) -> Void) {
        guard let builder = workoutBuilder else {
            completion(false, nil)
            return
        }
        
        // Stop all queries
        stopQueries()
        
        builder.endCollection(withEnd: Date()) { [weak self] success, error in
            if success {
                builder.finishWorkout { workout, error in
                    self?.isActive = false
                    self?.delegate?.workoutDidChangeState("ENDED")
                    self?.cleanup()
                    completion(workout != nil, error)
                }
            } else {
                completion(false, error)
            }
        }
    }
    
    // MARK: - Workout Event Management
    @objc public func addWorkoutEvent(eventType: HKWorkoutEventType, date: Date, metadata: [String: Any]?, completion: @escaping (Bool, Error?) -> Void) {
        guard let builder = workoutBuilder else {
            completion(false, NSError(domain: "WorkoutSession", code: 1, userInfo: [NSLocalizedDescriptionKey: "Workout builder not initialized"]))
            return
        }
        
        let event = HKWorkoutEvent(
            type: eventType,
            dateInterval: DateInterval(start: date, duration: 0),
            metadata: metadata
        )
        
        builder.addWorkoutEvents([event]) { success, error in
            completion(success, error)
        }
    }
    
    // MARK: - Stop Queries
    private func stopQueries() {
        // Stop all active queries
        if let query = heartRateQuery {
            healthStore.stop(query)
            heartRateQuery = nil
        }
        if let query = activeCaloriesQuery {
            healthStore.stop(query)
            activeCaloriesQuery = nil
        }
        if let query = basalCaloriesQuery {
            healthStore.stop(query)
            basalCaloriesQuery = nil
        }
        if let query = distanceQuery {
            healthStore.stop(query)
            distanceQuery = nil
        }
        if let query = elevationQuery {
            healthStore.stop(query)
            elevationQuery = nil
        }
        if let query = cadenceQuery {
            healthStore.stop(query)
            cadenceQuery = nil
        }
        if let query = powerQuery {
            healthStore.stop(query)
            powerQuery = nil
        }
        if let query = speedQuery {
            healthStore.stop(query)
            speedQuery = nil
        }
        if let query = strokeCountQuery {
            healthStore.stop(query)
            strokeCountQuery = nil
        }
    }
    
    // MARK: - Cleanup
    private func cleanup() {
        stopQueries()
        workoutBuilder = nil
        
        // Reset anchors
        heartRateAnchor = nil
        activeCaloriesAnchor = nil
        basalCaloriesAnchor = nil
        distanceAnchor = nil
        elevationAnchor = nil
        cadenceAnchor = nil
        powerAnchor = nil
        speedAnchor = nil
        strokeCountAnchor = nil
        
        // Reset current values
        currentHeartRate = 0
        currentCadence = 0
        currentPower = 0
        currentSpeed = 0
        currentElevation = 0
        lastElevation = nil
    }
}

// MARK: - Mobility and Movement Queries
@objc public class MobilityDataReader: NSObject {
    private let healthStore = HKHealthStore()
    
    @objc public func readWalkingAsymmetry(startDate: Date, endDate: Date, completion: @escaping ([HKQuantitySample]?, Error?) -> Void) {
        guard #available(iOS 14.0, *) else {
            completion(nil, NSError(domain: "HealthKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "Walking asymmetry requires iOS 14.0+"]))
            return
        }
        
        guard let type = HKQuantityType.quantityType(forIdentifier: .walkingAsymmetryPercentage) else {
            completion(nil, NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Walking asymmetry type not available"]))
            return
        }
        
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: [])
        let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { query, samples, error in
            completion(samples as? [HKQuantitySample], error)
        }
        
        healthStore.execute(query)
    }
    
    @objc public func readWalkingSpeed(startDate: Date, endDate: Date, completion: @escaping ([HKQuantitySample]?, Error?) -> Void) {
        guard #available(iOS 14.0, *) else {
            completion(nil, NSError(domain: "HealthKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "Walking speed requires iOS 14.0+"]))
            return
        }
        
        guard let type = HKQuantityType.quantityType(forIdentifier: .walkingSpeed) else {
            completion(nil, NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Walking speed type not available"]))
            return
        }
        
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: [])
        let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { query, samples, error in
            completion(samples as? [HKQuantitySample], error)
        }
        
        healthStore.execute(query)
    }
    
    @objc public func observeWalkingAsymmetry(handler: @escaping ([HKQuantitySample]?) -> Void) -> HKObserverQuery? {
        guard #available(iOS 14.0, *) else { return nil }
        guard let type = HKQuantityType.quantityType(forIdentifier: .walkingAsymmetryPercentage) else { return nil }
        
        let query = HKObserverQuery(sampleType: type, predicate: nil) { query, completionHandler, error in
            if error == nil {
                let endDate = Date()
                let startDate = Calendar.current.date(byAdding: .day, value: -1, to: endDate)!
                self.readWalkingAsymmetry(startDate: startDate, endDate: endDate) { samples, _ in
                    handler(samples)
                }
            }
            completionHandler()
        }
        
        healthStore.execute(query)
        return query
    }
}


// MARK: - Advanced Workout Metrics Queries
@objc public class AdvancedWorkoutDataReader: NSObject {
    private let healthStore = HKHealthStore()
    
    @objc public func readRunningStrideLength(startDate: Date, endDate: Date, completion: @escaping ([HKQuantitySample]?, Error?) -> Void) {
        guard #available(iOS 16.0, *) else {
            completion(nil, NSError(domain: "HealthKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "Running stride length requires iOS 16.0+"]))
            return
        }
        
        guard let type = HKQuantityType.quantityType(forIdentifier: .runningStrideLength) else {
            completion(nil, NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Running stride length type not available"]))
            return
        }
        
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: [])
        let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { query, samples, error in
            completion(samples as? [HKQuantitySample], error)
        }
        
        healthStore.execute(query)
    }
    
    @objc public func readCyclingPower(startDate: Date, endDate: Date, completion: @escaping ([HKQuantitySample]?, Error?) -> Void) {
        guard #available(iOS 17.0, *) else {
            completion(nil, NSError(domain: "HealthKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "Cycling power requires iOS 17.0+"]))
            return
        }
        
        guard let type = HKQuantityType.quantityType(forIdentifier: .cyclingPower) else {
            completion(nil, NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Cycling power type not available"]))
            return
        }
        
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: [])
        let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { query, samples, error in
            completion(samples as? [HKQuantitySample], error)
        }
        
        healthStore.execute(query)
    }
}

// MARK: - Irregular Rhythm Notification Support
@available(iOS 14.0, *)
@objc public class IrregularRhythmReader: NSObject {
    private let healthStore = HKHealthStore()
    
    @objc public func readIrregularRhythmEvents(startDate: Date, endDate: Date, completion: @escaping ([HKCategorySample]?, Error?) -> Void) {
        guard let type = HKCategoryType.categoryType(forIdentifier: .irregularHeartRhythmEvent) else {
            completion(nil, NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Irregular rhythm type not available"]))
            return
        }
        
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: [])
        let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { query, samples, error in
            completion(samples as? [HKCategorySample], error)
        }
        
        healthStore.execute(query)
    }
    
    @objc public func observeIrregularRhythmEvents(handler: @escaping ([HKCategorySample]?) -> Void) -> HKObserverQuery? {
        guard let type = HKCategoryType.categoryType(forIdentifier: .irregularHeartRhythmEvent) else { return nil }
        
        let query = HKObserverQuery(sampleType: type, predicate: nil) { query, completionHandler, error in
            if error == nil {
                let endDate = Date()
                let startDate = Calendar.current.date(byAdding: .day, value: -30, to: endDate)!
                self.readIrregularRhythmEvents(startDate: startDate, endDate: endDate) { samples, _ in
                    handler(samples)
                }
            }
            completionHandler()
        }
        
        healthStore.execute(query)
        return query
    }
}

// MARK: - Metadata Extraction Helpers
@objc public class HealthKitMetadataExtractor: NSObject {
    
    @objc public static func extractMetadata(from sample: HKSample) -> [String: Any] {
        var metadata: [String: Any] = [:]
        
        // Basic sample info
        metadata["uuid"] = sample.uuid.uuidString
        metadata["sampleType"] = sample.sampleType.identifier
        metadata["startDate"] = sample.startDate.timeIntervalSince1970
        metadata["endDate"] = sample.endDate.timeIntervalSince1970
        
        // Source info
        let sourceRevision = sample.sourceRevision
        metadata["sourceName"] = sourceRevision.source.name
        metadata["sourceBundleIdentifier"] = sourceRevision.source.bundleIdentifier
        
        if let version = sourceRevision.version {
            metadata["sourceVersion"] = version
        }
        
        if let productType = sourceRevision.productType {
            metadata["sourceProductType"] = productType
        }
        
        metadata["sourceOperatingSystemVersion"] = "\(sourceRevision.operatingSystemVersion.majorVersion).\(sourceRevision.operatingSystemVersion.minorVersion).\(sourceRevision.operatingSystemVersion.patchVersion)"
        
        // Device info
        if let device = sample.device {
            var deviceInfo: [String: Any] = [:]
            
            if let name = device.name {
                deviceInfo["name"] = name
            }
            if let manufacturer = device.manufacturer {
                deviceInfo["manufacturer"] = manufacturer
            }
            if let model = device.model {
                deviceInfo["model"] = model
            }
            if let hardwareVersion = device.hardwareVersion {
                deviceInfo["hardwareVersion"] = hardwareVersion
            }
            if let firmwareVersion = device.firmwareVersion {
                deviceInfo["firmwareVersion"] = firmwareVersion
            }
            if let softwareVersion = device.softwareVersion {
                deviceInfo["softwareVersion"] = softwareVersion
            }
            if let localIdentifier = device.localIdentifier {
                deviceInfo["localIdentifier"] = localIdentifier
            }
            if let udiDeviceIdentifier = device.udiDeviceIdentifier {
                deviceInfo["udiDeviceIdentifier"] = udiDeviceIdentifier
            }
            
            metadata["device"] = deviceInfo
        }
        
        // Sample-specific metadata
        if let sampleMetadata = sample.metadata {
            metadata["customMetadata"] = sampleMetadata
        }
        
        // Determine source type
        let isFromAppleWatch = sourceRevision.productType?.contains("Watch") ?? false
        let isFromPhone = sourceRevision.productType?.contains("iPhone") ?? false
        let isFromHealthApp = sourceRevision.source.bundleIdentifier.contains("com.apple.health")
        
        metadata["isFromAppleWatch"] = isFromAppleWatch
        metadata["isFromPhone"] = isFromPhone
        metadata["isFromHealthApp"] = isFromHealthApp
        metadata["isFromThirdPartyApp"] = !isFromHealthApp
        
        return metadata
    }
    
    // Extract DataSource info suitable for Kotlin model
    @objc public static func extractDataSource(from sample: HKSample) -> [String: Any] {
        var dataSource: [String: Any] = [:]
        
        let sourceRevision = sample.sourceRevision
        dataSource["name"] = sourceRevision.source.name
        dataSource["bundleIdentifier"] = sourceRevision.source.bundleIdentifier
        
        // Determine source type
        let bundleId = sourceRevision.source.bundleIdentifier
        let productType = sourceRevision.productType ?? ""
        
        let sourceType: String
        if bundleId.contains("com.apple.health") || sample.device != nil {
            sourceType = "device"
        } else {
            sourceType = "application"
        }
        dataSource["type"] = sourceType
        
        // Device info if available
        if let device = sample.device {
            var deviceInfo: [String: Any] = [:]
            
            deviceInfo["manufacturer"] = device.manufacturer ?? "Unknown"
            deviceInfo["model"] = device.model ?? "Unknown"
            
            // Determine device type
            let deviceType: String
            if let model = device.model {
                if model.contains("Watch") {
                    deviceType = "watch"
                } else if model.contains("iPhone") {
                    deviceType = "phone"
                } else {
                    deviceType = "other"
                }
            } else if productType.contains("Watch") {
                deviceType = "watch"
            } else if productType.contains("iPhone") {
                deviceType = "phone"
            } else {
                deviceType = "other"
            }
            
            deviceInfo["type"] = deviceType
            deviceInfo["softwareVersion"] = device.softwareVersion
            
            dataSource["device"] = deviceInfo
        }
        
        return dataSource
    }
}

// MARK: - Workout Route Support
@objc public class WorkoutRouteBuilder: NSObject {
    private let healthStore = HKHealthStore()
    private var routeBuilder: HKWorkoutRouteBuilder?
    
    @objc public func createRouteBuilder(workout: HKWorkout) -> Bool {
        routeBuilder = HKWorkoutRouteBuilder(healthStore: healthStore, device: nil)
        return routeBuilder != nil
    }
    
    @objc public func addRoutePoint(latitude: Double, longitude: Double, altitude: NSNumber?, timestamp: Date, accuracy: NSNumber?, altitudeAccuracy: NSNumber?) {
        guard let builder = routeBuilder else { return }
        
        let coordinate = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        var location: CLLocation
        
        if let altitude = altitude, let accuracy = accuracy, let altitudeAccuracy = altitudeAccuracy {
            location = CLLocation(
                coordinate: coordinate,
                altitude: altitude.doubleValue,
                horizontalAccuracy: accuracy.doubleValue,
                verticalAccuracy: altitudeAccuracy.doubleValue,
                timestamp: timestamp
            )
        } else if let accuracy = accuracy {
            location = CLLocation(
                coordinate: coordinate,
                altitude: 0,
                horizontalAccuracy: accuracy.doubleValue,
                verticalAccuracy: -1,
                timestamp: timestamp
            )
        } else {
            location = CLLocation(
                coordinate: coordinate,
                altitude: 0,
                horizontalAccuracy: 5.0,
                verticalAccuracy: -1,
                timestamp: timestamp
            )
        }
        
        builder.insertRouteData([location]) { success, error in
            if let error = error {
                print("Failed to add route point: \(error.localizedDescription)")
            }
        }
    }
    
    @objc public func finishRoute(workout: HKWorkout, completion: @escaping (Bool, Error?) -> Void) {
        guard let builder = routeBuilder else {
            completion(false, NSError(domain: "WorkoutRoute", code: 1, userInfo: [NSLocalizedDescriptionKey: "Route builder not initialized"]))
            return
        }
        
        builder.finishRoute(with: workout, metadata: nil) { route, error in
            if route != nil {
                completion(true, nil)
            } else {
                completion(false, error)
            }
        }
    }
}

// MARK: - ECG Support
@available(iOS 14.0, *)
@objc public class ECGReader: NSObject {
    private let healthStore = HKHealthStore()
    
    @objc public func readLatestECG(completion: @escaping (ECGData?, Error?) -> Void) {
        guard HKHealthStore.isHealthDataAvailable() else {
            completion(nil, NSError(domain: "HealthKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "HealthKit is not available"]))
            return
        }
        
        let ecgType = HKObjectType.electrocardiogramType()
        
        let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)
        let query = HKSampleQuery(sampleType: ecgType, predicate: nil, limit: 1, sortDescriptors: [sortDescriptor]) { query, samples, error in
            guard let samples = samples as? [HKElectrocardiogram], let ecg = samples.first else {
                completion(nil, error ?? NSError(domain: "HealthKit", code: 3, userInfo: [NSLocalizedDescriptionKey: "No ECG data found"]))
                return
            }
            
            // Extract ECG data
            let data = ECGData(ecg: ecg)
            completion(data, nil)
        }
        
        healthStore.execute(query)
    }
    
    @objc public func observeECGReadings(handler: @escaping (ECGData?) -> Void) -> HKObserverQuery? {
        let ecgType = HKObjectType.electrocardiogramType()
        
        let query = HKObserverQuery(sampleType: ecgType, predicate: nil) { query, completionHandler, error in
            if error == nil {
                self.readLatestECG { data, _ in
                    handler(data)
                }
            }
            completionHandler()
        }
        
        healthStore.execute(query)
        return query
    }
}

// MARK: - ECG Data Model
@available(iOS 14.0, *)
@objc public class ECGData: NSObject {
    @objc public let uuid: String
    @objc public let startDate: Date
    @objc public let samplingFrequency: Double
    @objc public let classification: String
    @objc public let averageHeartRate: NSNumber?
    @objc public let numberOfVoltageMeasurements: Int
    @objc public let symptomsStatus: String
    @objc public let symptoms: [String]
    @objc public let metadata: [String: Any]
    private let ecgSample: HKElectrocardiogram
    private let healthStore = HKHealthStore()
    
    init(ecg: HKElectrocardiogram) {
        self.ecgSample = ecg
        self.uuid = ecg.uuid.uuidString
        self.startDate = ecg.startDate
        self.samplingFrequency = ecg.samplingFrequency?.doubleValue(for: .hertz()) ?? 0
        self.numberOfVoltageMeasurements = ecg.numberOfVoltageMeasurements
        
        // Classification
        switch ecg.classification {
        case .notSet:
            self.classification = "NOT_SET"
        case .sinusRhythm:
            self.classification = "SINUS_RHYTHM"
        case .atrialFibrillation:
            self.classification = "ATRIAL_FIBRILLATION"
        case .inconclusiveLowHeartRate:
            self.classification = "INCONCLUSIVE_LOW_HEART_RATE"
        case .inconclusiveHighHeartRate:
            self.classification = "INCONCLUSIVE_HIGH_HEART_RATE"
        case .inconclusivePoorReading:
            self.classification = "INCONCLUSIVE_POOR_READING"
        case .inconclusiveOther:
            self.classification = "INCONCLUSIVE_OTHER"
        case .unrecognized:
            self.classification = "UNRECOGNIZED"
        @unknown default:
            self.classification = "UNKNOWN"
        }
        
        // Average heart rate
        if let avgHeartRate = ecg.averageHeartRate {
            self.averageHeartRate = NSNumber(value: avgHeartRate.doubleValue(for: HKUnit.count().unitDivided(by: .minute())))
        } else {
            self.averageHeartRate = nil
        }
        
        // Symptoms status
        switch ecg.symptomsStatus {
        case .notSet:
            self.symptomsStatus = "NOT_SET"
        case .none:
            self.symptomsStatus = "NONE"
        case .present:
            self.symptomsStatus = "PRESENT"
        @unknown default:
            self.symptomsStatus = "UNKNOWN"
        }
        
        var extractedSymptoms: [String] = []
        if let metadata = ecg.metadata {
            // Check for each symptom key in metadata
            if metadata[HKMetadataKeyHeartRateEventThreshold] != nil {
                extractedSymptoms.append("HEART_PALPITATIONS")
            }
            
            // Check for user-entered symptoms (Apple Health app allows users to log symptoms)
            if let userEnteredSymptoms = metadata["HKUserEnteredSymptoms"] as? [String] {
                for symptom in userEnteredSymptoms {
                    switch symptom.lowercased() {
                    case "chest tightness", "chest pain":
                        extractedSymptoms.append("CHEST_TIGHTNESS_OR_PAIN")
                    case "shortness of breath":
                        extractedSymptoms.append("SHORTNESS_OF_BREATH")
                    case "lightheadedness", "dizziness":
                        extractedSymptoms.append("LIGHTHEADEDNESS_OR_DIZZINESS")
                    case "heart palpitations", "palpitations":
                        extractedSymptoms.append("HEART_PALPITATIONS")
                    case "rapid heartbeat", "pounding heartbeat", "fluttering", "skipped heartbeat":
                        extractedSymptoms.append("RAPID_POUNDING_FLUTTERING_OR_SKIPPED_HEARTBEAT")
                    default:
                        extractedSymptoms.append("OTHER")
                    }
                }
            }
        }
        
        self.symptoms = extractedSymptoms
        self.metadata = ecg.metadata ?? [:]
        
        super.init()
    }
    
    // Method to get voltage measurements with time offsets
    @objc public func getVoltageMeasurements(completion: @escaping ([[String: Double]]?, Error?) -> Void) {
        var voltageMeasurements: [[String: Double]] = []
        
        let query = HKElectrocardiogramQuery(self.ecgSample) { (query, result) in
            switch result {
            case .error(let error):
                // Return error
                completion(nil, error)
            case .measurement(let measurement):
                // Get voltage value in volts
                if let voltageQuantity = measurement.quantity(for: .appleWatchSimilarToLeadI) {
                    let voltageValue = voltageQuantity.doubleValue(for: HKUnit.volt())
                    let timeOffset = measurement.timeSinceSampleStart
                    
                    voltageMeasurements.append([
                        "voltage": voltageValue,
                        "timeOffset": timeOffset
                    ])
                }
            case .done:
                // All measurements collected, return results
                completion(voltageMeasurements, nil)
            @unknown default:
                completion(nil, NSError(domain: "ECGData", code: 2, userInfo: [NSLocalizedDescriptionKey: "Unknown ECG query result"]))
            }
        }
        
        self.healthStore.execute(query)
    }
}

// MARK: - Workout Segments and Laps
@objc public class WorkoutSegmentBuilder: NSObject {
    private var segments: [WorkoutSegment] = []
    private var currentSegment: WorkoutSegment?
    
    @objc public func startSegment(activityType: HKWorkoutActivityType, startDate: Date) {
        let segment = WorkoutSegment(activityType: activityType, startDate: startDate)
        currentSegment = segment
    }
    
    @objc public func endSegment(endDate: Date, totalEnergyBurned: NSNumber?, totalDistance: NSNumber?) {
        guard let segment = currentSegment else { return }
        
        segment.endDate = endDate
        segment.totalEnergyBurned = totalEnergyBurned
        segment.totalDistance = totalDistance
        
        segments.append(segment)
        currentSegment = nil
    }
    
    @objc public func getSegments() -> [WorkoutSegment] {
        return segments
    }
    
    @objc public func createWorkoutEvents() -> [HKWorkoutEvent] {
        var events: [HKWorkoutEvent] = []
        
        for (index, segment) in segments.enumerated() {
            let metadata: [String: Any] = [
                "segmentIndex": index,
                "activityType": segment.activityType.rawValue
            ]
            
            let event = HKWorkoutEvent(
                type: .segment,
                dateInterval: DateInterval(start: segment.startDate, end: segment.endDate ?? Date()),
                metadata: metadata
            )
            events.append(event)
        }
        
        return events
    }
}

// MARK: - Workout Segment Model
@objc public class WorkoutSegment: NSObject {
    @objc public let activityType: HKWorkoutActivityType
    @objc public let startDate: Date
    @objc public var endDate: Date?
    @objc public var totalEnergyBurned: NSNumber?
    @objc public var totalDistance: NSNumber?
    
    init(activityType: HKWorkoutActivityType, startDate: Date) {
        self.activityType = activityType
        self.startDate = startDate
        super.init()
    }
}

// MARK: - Helper Methods for Type Conversion
@objc public class HealthKitHelper: NSObject {
    
    // Convert workout type string to HKWorkoutActivityType
    @objc public static func workoutActivityType(from typeString: String) -> HKWorkoutActivityType {
        switch typeString {
        case "RUNNING": return .running
        case "WALKING": return .walking
        case "CYCLING": return .cycling
        case "SWIMMING": return .swimming
        case "YOGA": return .yoga
        case "PILATES": return .pilates
        case "DANCE": return .dance
        case "MARTIAL_ARTS": return .martialArts
        case "ROWING": return .rowing
        case "ELLIPTICAL": return .elliptical
        case "STAIR_CLIMBING": return .stairClimbing
        case "HIGH_INTENSITY_INTERVAL_TRAINING": return .highIntensityIntervalTraining
        case "FUNCTIONAL_TRAINING": return .functionalStrengthTraining
        case "CORE_TRAINING": return .coreTraining
        case "CROSS_TRAINING": return .crossTraining
        case "FLEXIBILITY": return .flexibility
        case "MIXED_CARDIO": return .mixedCardio
        case "STRENGTH_TRAINING": return .traditionalStrengthTraining
        case "SOCCER": return .soccer
        case "BASKETBALL": return .basketball
        case "TENNIS": return .tennis
        case "GOLF": return .golf
        case "HIKING": return .hiking
        case "SKIING": return .snowSports
        case "SNOWBOARDING": return .snowSports
        case "SKATING": return .other  // No specific skating type
        case "SURFING": return .waterSports
        case "CLIMBING": return .climbing
        case "MEDITATION": return .mindAndBody
        default: return .other
        }
    }
    
    // Check if HealthKit is available
    @objc public static func isHealthKitAvailable() -> Bool {
        return HKHealthStore.isHealthDataAvailable()
    }
    
    // Request authorization for workout-related data types
    @objc public static func requestWorkoutAuthorization(completion: @escaping (Bool, Error?) -> Void) {
        guard isHealthKitAvailable() else {
            completion(false, NSError(domain: "HealthKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "HealthKit is not available"]))
            return
        }
        
        let healthStore = HKHealthStore()
        
        let typesToRead: Set<HKObjectType> = [
            HKObjectType.quantityType(forIdentifier: .heartRate)!,
            HKObjectType.quantityType(forIdentifier: .activeEnergyBurned)!,
            HKObjectType.quantityType(forIdentifier: .basalEnergyBurned)!,
            HKObjectType.quantityType(forIdentifier: .distanceWalkingRunning)!,
            HKObjectType.workoutType()
        ]
        
        let typesToWrite: Set<HKSampleType> = [
            HKObjectType.quantityType(forIdentifier: .heartRate)!,
            HKObjectType.quantityType(forIdentifier: .activeEnergyBurned)!,
            HKObjectType.quantityType(forIdentifier: .basalEnergyBurned)!,
            HKObjectType.quantityType(forIdentifier: .distanceWalkingRunning)!,
            HKObjectType.workoutType()
        ]
        
        healthStore.requestAuthorization(toShare: typesToWrite, read: typesToRead) { success, error in
            completion(success, error)
        }
    }
    
    // Request authorization for new data types
    @objc public static func requestExtendedAuthorization(completion: @escaping (Bool, Error?) -> Void) {
        guard isHealthKitAvailable() else {
            completion(false, NSError(domain: "HealthKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "HealthKit is not available"]))
            return
        }
        
        let healthStore = HKHealthStore()
        
        var typesToRead: Set<HKObjectType> = [
            HKObjectType.quantityType(forIdentifier: .heartRate)!,
            HKObjectType.quantityType(forIdentifier: .activeEnergyBurned)!,
            HKObjectType.quantityType(forIdentifier: .basalEnergyBurned)!,
            HKObjectType.quantityType(forIdentifier: .distanceWalkingRunning)!,
            HKObjectType.quantityType(forIdentifier: .distanceCycling)!,
            HKObjectType.quantityType(forIdentifier: .distanceSwimming)!,
            HKObjectType.quantityType(forIdentifier: .stepCount)!,
            HKObjectType.quantityType(forIdentifier: .flightsClimbed)!,
            HKObjectType.quantityType(forIdentifier: .appleStandTime)!,
            HKObjectType.workoutType(),
            HKObjectType.categoryType(forIdentifier: .sleepAnalysis)!,
            HKObjectType.categoryType(forIdentifier: .mindfulSession)!
        ]
        
        let typesToWrite: Set<HKSampleType> = Set(typesToRead.compactMap { $0 as? HKSampleType })
        
        // Add iOS 14+ types
        if #available(iOS 14.0, *) {
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .walkingAsymmetryPercentage)!)
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .walkingDoubleSupportPercentage)!)
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .walkingSpeed)!)
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .walkingStepLength)!)
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .sixMinuteWalkTestDistance)!)
            typesToRead.insert(HKObjectType.electrocardiogramType())
            typesToRead.insert(HKObjectType.categoryType(forIdentifier: .irregularHeartRhythmEvent)!)
        }
        
        // Add iOS 16+ types
        if #available(iOS 16.0, *) {
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .runningStrideLength)!)
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .runningVerticalOscillation)!)
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .runningGroundContactTime)!)
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .runningSpeed)!)
        }
        
        // Add iOS 17+ types
        if #available(iOS 17.0, *) {
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .cyclingCadence)!)
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .cyclingPower)!)
            typesToRead.insert(HKObjectType.quantityType(forIdentifier: .cyclingFunctionalThresholdPower)!)
        }
        
        // Add audio exposure types
        typesToRead.insert(HKObjectType.quantityType(forIdentifier: .environmentalAudioExposure)!)
        typesToRead.insert(HKObjectType.quantityType(forIdentifier: .headphoneAudioExposure)!)
        
        healthStore.requestAuthorization(toShare: typesToWrite, read: typesToRead) { success, error in
            completion(success, error)
        }
    }
}

// MARK: - Clinical Records Reader
@objc public class ClinicalRecordReader: NSObject {
    private let healthStore = HKHealthStore()
    
    // MARK: Read Clinical Records
    @objc public func readClinicalRecords(
        clinicalType: String,
        startDate: Date? = nil,
        endDate: Date? = nil,
        completion: @escaping ([String]?, String?) -> Void
    ) {
        // Map string type to HKClinicalTypeIdentifier
        let clinicalTypeIdentifier: HKClinicalTypeIdentifier
        switch clinicalType {
        case "allergyRecord":
            clinicalTypeIdentifier = .allergyRecord
        case "conditionRecord":
            clinicalTypeIdentifier = .conditionRecord
        case "immunizationRecord":
            clinicalTypeIdentifier = .immunizationRecord
        case "labResultRecord":
            clinicalTypeIdentifier = .labResultRecord
        case "medicationRecord":
            clinicalTypeIdentifier = .medicationRecord
        case "procedureRecord":
            clinicalTypeIdentifier = .procedureRecord
        case "vitalSignRecord":
            clinicalTypeIdentifier = .vitalSignRecord
        default:
            completion(nil, "Unknown clinical record type: \(clinicalType)")
            return
        }
        
        guard let clinicalRecordType = HKObjectType.clinicalType(forIdentifier: clinicalTypeIdentifier) else {
            completion(nil, "Clinical record type not available")
            return
        }
        
        // Create predicate with optional date range
        var predicate: NSPredicate? = nil
        if let start = startDate, let end = endDate {
            predicate = HKQuery.predicateForSamples(
                withStart: start,
                end: end,
                options: .strictEndDate
            )
        }
        
        // Create sort descriptor (newest first)
        let sortDescriptor = NSSortDescriptor(
            key: HKSampleSortIdentifierStartDate,
            ascending: false
        )
        
        // Create query
        let query = HKSampleQuery(
            sampleType: clinicalRecordType,
            predicate: predicate,
            limit: HKObjectQueryNoLimit,
            sortDescriptors: [sortDescriptor]
        ) { query, samples, error in
            if let error = error {
                completion(nil, error.localizedDescription)
                return
            }
            
            let fhirJsonStrings = samples?.compactMap { sample -> String? in
                guard let clinicalRecord = sample as? HKClinicalRecord else { return nil }
                guard let fhirData = clinicalRecord.fhirResource?.data else { return nil }
                return String(data: fhirData, encoding: .utf8)
            } ?? []
            
            completion(fhirJsonStrings, nil)
        }
        
        healthStore.execute(query)
    }
    
    // MARK: Read All Allergies
    @objc public func readAllergies(
        includeInactive: Bool,
        completion: @escaping ([String]?, String?) -> Void
    ) {
        readClinicalRecords(
            clinicalType: "allergyRecord",
            completion: completion
        )
    }
    
    // MARK: Read All Conditions
    @objc public func readConditions(
        includeResolved: Bool,
        completion: @escaping ([String]?, String?) -> Void
    ) {
        readClinicalRecords(
            clinicalType: "conditionRecord",
            completion: completion
        )
    }
    
    // MARK: Read Immunizations
    @objc public func readImmunizations(
        startDate: Date? = nil,
        endDate: Date? = nil,
        completion: @escaping ([String]?, String?) -> Void
    ) {
        readClinicalRecords(
            clinicalType: "immunizationRecord",
            startDate: startDate,
            endDate: endDate,
            completion: completion
        )
    }
    
    // MARK: Read Lab Results
    @objc public func readLabResults(
        startDate: Date? = nil,
        endDate: Date? = nil,
        completion: @escaping ([String]?, String?) -> Void
    ) {
        readClinicalRecords(
            clinicalType: "labResultRecord",
            startDate: startDate,
            endDate: endDate,
            completion: completion
        )
    }
    
    // MARK: Read Medications
    @objc public func readMedications(
        startDate: Date? = nil,
        endDate: Date? = nil,
        completion: @escaping ([String]?, String?) -> Void
    ) {
        readClinicalRecords(
            clinicalType: "medicationRecord",
            startDate: startDate,
            endDate: endDate,
            completion: completion
        )
    }
    
    // MARK: Read Procedures
    @objc public func readProcedures(
        startDate: Date? = nil,
        endDate: Date? = nil,
        completion: @escaping ([String]?, String?) -> Void
    ) {
        readClinicalRecords(
            clinicalType: "procedureRecord",
            startDate: startDate,
            endDate: endDate,
            completion: completion
        )
    }
    
    // MARK: Check Clinical Records Availability
    @objc public func areClinicalRecordsAvailable() -> Bool {
        // Clinical records are available on iOS 12.0+
        if #available(iOS 12.0, *) {
            return HKHealthStore.isHealthDataAvailable()
        }
        return false
    }
    
    // MARK: Get Clinical Record Types for Authorization
    @objc public func getClinicalRecordTypes() -> Set<HKClinicalType> {
        var types = Set<HKClinicalType>()
        
        if let allergyType = HKObjectType.clinicalType(forIdentifier: .allergyRecord) {
            types.insert(allergyType)
        }
        if let conditionType = HKObjectType.clinicalType(forIdentifier: .conditionRecord) {
            types.insert(conditionType)
        }
        if let immunizationType = HKObjectType.clinicalType(forIdentifier: .immunizationRecord) {
            types.insert(immunizationType)
        }
        if let labResultType = HKObjectType.clinicalType(forIdentifier: .labResultRecord) {
            types.insert(labResultType)
        }
        if let medicationType = HKObjectType.clinicalType(forIdentifier: .medicationRecord) {
            types.insert(medicationType)
        }
        if let procedureType = HKObjectType.clinicalType(forIdentifier: .procedureRecord) {
            types.insert(procedureType)
        }
        if let vitalSignType = HKObjectType.clinicalType(forIdentifier: .vitalSignRecord) {
            types.insert(vitalSignType)
        }
        
        return types
    }
}

// MARK: - Delegate Protocol
@objc public protocol WatchConnectivityDelegate: AnyObject {
    @objc func watchConnectivityActivated()
    @objc func watchConnectivityBecameInactive()
    @objc func watchReachabilityChanged(isReachable: Bool)
    
    @objc func didReceiveMessage(_ message: [String: Any])
    @objc func didReceiveMessageWithReplyHandler(_ message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void)
    @objc func didReceiveApplicationContext(_ context: [String: Any])
    @objc func didReceiveUserInfo(_ userInfo: [String: Any])
    
    @objc func fileTransferCompleted(fileURL: URL, error: Error?)
}

// MARK: - Support Types
@objc public class ConnectivityStatus: NSObject {
    @objc public let isPaired: Bool
    @objc public let isWatchAppInstalled: Bool
    @objc public let isReachable: Bool
    @objc public let isComplicationEnabled: Bool
    @objc public let watchDirectoryURL: URL?
    
    init(isPaired: Bool, isWatchAppInstalled: Bool, isReachable: Bool, isComplicationEnabled: Bool, watchDirectoryURL: URL?) {
        self.isPaired = isPaired
        self.isWatchAppInstalled = isWatchAppInstalled
        self.isReachable = isReachable
        self.isComplicationEnabled = isComplicationEnabled
        self.watchDirectoryURL = watchDirectoryURL
    }
}

// MARK: - Error Types
enum WatchConnectivityError: LocalizedError {
    case watchNotReachable
    case invalidReply
    case transferFailed
    
    var errorDescription: String? {
        switch self {
        case .watchNotReachable:
            return "Apple Watch is not reachable"
        case .invalidReply:
            return "Invalid reply from Apple Watch"
        case .transferFailed:
            return "File transfer failed"
        }
    }
}