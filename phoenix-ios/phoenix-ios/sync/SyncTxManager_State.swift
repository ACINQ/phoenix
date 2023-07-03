import Foundation
import CloudKit

/* SyncTxManager State Machine:
 *
 * The state is always one of the following:
 *
 * - initializing
 * - updatingCloud
 *   - creatingRecordZone
 *   - deletingRecordZone
 * - downloading
 * - uploading
 * - waiting
 *   - forCloudCredentials
 *   - forInternet
 *   - exponentialBackoff
 *   - randomizedUploadDelay
 * - synced
 * - disabled
 * - shutdown
 *
 * State machine transitions:
 *
 * [initializing] --*--> (defer)
 *                  |--> [shutdown]
 *
 * [waiting_any] --*--> (defer)
 *                 |--> [shutdown]
 *
 * (defer) --*--> [waiting_forCloudCredentials]
 *           |--> [waiting_forInternet]
 *           |--> [updatingCloud_creatingRecordZone]
 *           |--> [downloading]
 *           |--> [uploading]
 *           |--> [updatingCloud_deletingRecordZone]
 *           |--> [disabled]
 *
 * [updatingCloud_any] --*--> (defer) // success or known error
 *                       |--> [waiting_exponentialBackoff]
 *                       |--> [shutdown]
 *
 * [downloading] --*--> (defer) // success or known error
 *                 |--> [waiting_exponentialBackoff]
 *                 |--> [shutdown]
 *
 * [uploading] --*--> [synced] // success
 *               |--> [waiting_exponentialBackoff]
 *               |--> (defer) // known error
 *               |--> [shutdown]
 *
 * [synced] --*--> [waiting_forInternet]
 *            |--> [waiting_forCloudCredentials]
 *            |--> [waiting_randomizedUploadDelay]
 *            |--> [disabled]
 *            |--> (defer)
 *            |--> [shutdown]
 *
 * [disabled] --*--> (defer)
 *              |--> [shutdown]
 *
 * [shutdown] --> X // stopped
 *
 *
 * Note: The term `(defer)` in the state diagram above signifies a transition to the "simplified state flow",
 * as implemented by the actor's `simplifiedStateFlow` function.
 */

enum SyncTxManager_State: Equatable, CustomStringConvertible {
	
	case initializing
	case updatingCloud(details: SyncTxManager_State_UpdatingCloud)
	case downloading(details: SyncTxManager_State_Downloading)
	case uploading(details: SyncTxManager_State_Uploading)
	case waiting(details: SyncTxManager_State_Waiting)
	case synced
	case disabled
	case shutdown
	
	var description: String {
		switch self {
			case .initializing:
				return "initializing"
			case .updatingCloud(let details):
				switch details.kind {
					case .creatingRecordZone:
						return "updatingCloud_creatingRecordZone"
					case .deletingRecordZone:
						return "updatingCloud_deletingRecordZone"
				}
			case .downloading:
				return "downloading"
			case .uploading:
				return "uploading"
			case .waiting(let details):
				switch details.kind {
					case .forInternet:
						return "waiting_forInternet"
					case .forCloudCredentials:
						return "waiting_forCloudCredentials"
					case .exponentialBackoff:
						return "waiting_exponentialBackoff(\(details.until?.delay ?? 0.0))"
					case .randomizedUploadDelay:
						return "waiting_randomizedUploadDelay(\(details.until?.delay ?? 0.0))"
				}
			case .synced:
				return "synced"
			case .disabled:
				return "disabled"
			case .shutdown:
				return "shutdown"
		}
	}
	
	// Simplified initializers:
	
	static func updatingCloud_creatingRecordZone() -> SyncTxManager_State {
		return .updatingCloud(details: SyncTxManager_State_UpdatingCloud(kind: .creatingRecordZone))
	}
	
	static func updatingCloud_deletingRecordZone() -> SyncTxManager_State {
		return .updatingCloud(details: SyncTxManager_State_UpdatingCloud(kind: .deletingRecordZone))
	}
	
	static func waiting_forInternet() -> SyncTxManager_State {
		return .waiting(details: SyncTxManager_State_Waiting(kind: .forInternet))
	}
	
	static func waiting_forCloudCredentials() -> SyncTxManager_State {
		return .waiting(details: SyncTxManager_State_Waiting(kind: .forCloudCredentials))
	}
	
	static func waiting_exponentialBackoff(
		_ parent: SyncTxManager,
		delay: TimeInterval,
		error: Error
	) -> SyncTxManager_State {
		return .waiting(details: SyncTxManager_State_Waiting(
			kind: .exponentialBackoff(error),
			parent: parent,
			delay: delay
		))
	}
	
	static func waiting_randomizedUploadDelay(
		_ parent: SyncTxManager,
		delay: TimeInterval
	) -> SyncTxManager_State {
		return .waiting(details: SyncTxManager_State_Waiting(
			kind: .randomizedUploadDelay,
			parent: parent,
			delay: delay
		))
	}
}

/// Details concerning the type of changes being made to the CloudKit container(s).
///
class SyncTxManager_State_UpdatingCloud: Equatable {
	
	enum Kind {
		case creatingRecordZone
		case deletingRecordZone
	}
	
	let kind: Kind
	var task: Task<Void, Error>? = nil
	
	private(set) var isCancelled = false
	
	init(kind: Kind) {
		self.kind = kind
	}
	
	func cancel() {
		isCancelled = true
		task?.cancel()
	}
	
	static func == (lhs: SyncTxManager_State_UpdatingCloud, rhs: SyncTxManager_State_UpdatingCloud) -> Bool {
		return lhs.kind == rhs.kind
	}
}

/// Exposes an ObservableObject that can be used by the UI for various purposes.
/// All changes to `@Published` properties will be made on the UI thread.
///
class SyncTxManager_State_Downloading: ObservableObject, Equatable {
	
	@Published private(set) var completedCount: Int = 0
	@Published private(set) var oldestCompletedDownload: Date? = nil
	
	private func updateOnMainThread(_ block: @escaping () -> Void) {
		if Thread.isMainThread {
			block()
		} else {
			DispatchQueue.main.async { block() }
		}
	}
	
	func setOldestCompletedDownload(_ date: Date?) {
		updateOnMainThread {
			self.oldestCompletedDownload = date
		}
	}
	
	func finishBatch(completed: Int, oldest: Date?) {
		updateOnMainThread {
			self.completedCount += completed
			
			if let oldest = oldest {
				if let prv = self.oldestCompletedDownload {
					if oldest < prv {
						self.oldestCompletedDownload = oldest
					}
				} else {
					self.oldestCompletedDownload = oldest
				}
			}
		}
	}
	
	static func == (lhs: SyncTxManager_State_Downloading, rhs: SyncTxManager_State_Downloading) -> Bool {
		// Equality for this class is is based on pointers
		return lhs === rhs
	}
}

/// Exposes an ObservableObject that can be used by the UI to display progress information.
/// All changes to `@Published` properties will be made on the UI thread.
///
class SyncTxManager_State_Uploading: ObservableObject, Equatable {
	
	@Published private(set) var totalCount: Int
	@Published private(set) var completedCount: Int = 0
	@Published private(set) var inFlightCount: Int = 0
	@Published private(set) var inFlightProgress: Progress? = nil
	
	private(set) var isCancelled = false
	private(set) var operation: CKOperation? = nil
	
	init(totalCount: Int) {
		self.totalCount = totalCount
	}
	
	private func updateOnMainThread(_ block: @escaping () -> Void) {
		if Thread.isMainThread {
			block()
		} else {
			DispatchQueue.main.async { block() }
		}
	}
	
	func setTotalCount(_ value: Int) {
		updateOnMainThread {
			self.totalCount = value
		}
	}
	
	func setInFlight(count: Int, progress: Progress) {
		updateOnMainThread {
			self.inFlightCount = count
			self.inFlightProgress = progress
		}
	}
	
	func completeInFlight(completed: Int) {
		updateOnMainThread {
			self.completedCount += completed
			self.inFlightCount = 0
			self.inFlightProgress = nil
		}
	}
	
	func cancel() {
		isCancelled = true
		operation?.cancel()
	}
	
	func register(_ operation: CKOperation) -> Bool {
		if isCancelled {
			return false
		} else {
			self.operation = operation
			return true
		}
	}
	
	static func == (lhs: SyncTxManager_State_Uploading, rhs: SyncTxManager_State_Uploading) -> Bool {
		// Equality for this class is is based on pointers
		return lhs === rhs
	}
}

/// Details concerning what/why the SyncTxManager is temporarily paused.
/// Sometimes these delays can be manually cancelled by the user.
///
class SyncTxManager_State_Waiting: Equatable {
	
	enum Kind: Equatable {
		case forInternet
		case forCloudCredentials
		case exponentialBackoff(Error)
		case randomizedUploadDelay
		
		static func == (lhs: SyncTxManager_State_Waiting.Kind, rhs: SyncTxManager_State_Waiting.Kind) -> Bool {
			switch (lhs, rhs) {
				case (.forInternet, .forInternet): return true
				case (.forCloudCredentials, .forCloudCredentials): return true
				case (.randomizedUploadDelay, .randomizedUploadDelay): return true
				case (.exponentialBackoff(let lhe), .exponentialBackoff(let rhe)):
					return lhe.localizedDescription == rhe.localizedDescription
				default:
					return false
			}
		}
	}
	
	let kind: Kind
	
	struct WaitingUntil: Equatable {
		weak var parent: SyncTxManager?
		let delay: TimeInterval
		let startDate: Date
		let fireDate: Date
		
		static func == (lhs: SyncTxManager_State_Waiting.WaitingUntil,
		                rhs: SyncTxManager_State_Waiting.WaitingUntil
		) -> Bool {
			return (lhs.parent === rhs.parent) &&
			       (lhs.delay == rhs.delay) &&
			       (lhs.startDate == rhs.startDate) &&
			       (lhs.fireDate == rhs.fireDate)
		}
	}
	
	let until: WaitingUntil?
	
	init(kind: Kind) {
		self.kind = kind
		self.until = nil
	}
	
	init(kind: Kind, parent: SyncTxManager, delay: TimeInterval) {
		self.kind = kind
		
		let now = Date()
		self.until = WaitingUntil(
			parent: parent,
			delay: delay,
			startDate: now,
			fireDate: now + delay
		)
		
		let deadline: DispatchTime = DispatchTime.now() + delay
		DispatchQueue.global(qos: .utility).asyncAfter(deadline: deadline) {[weak self] in
			self?.timerFire()
		}
	}
	
	private func timerFire() {
		until?.parent?.finishWaiting(self)
	}
	
	/// Allows the user to terminate the delay early.
	///
	func skip() {
		timerFire()
	}
	
	static func == (lhs: SyncTxManager_State_Waiting, rhs: SyncTxManager_State_Waiting) -> Bool {
		
		return (lhs.kind == rhs.kind) && (lhs.until == rhs.until)
	}
}
