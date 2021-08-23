import Foundation
import CloudKit

/* SyncManager State Machine:
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
 *
 * The state is modified via the `updateState` function:
 *
 * Note that `(defer)` in the state diagram below signifies a transition to the "simplified state flow",
 * as implemented by the `updateState` function.
 *
 * [initializing] --*--> [waiting_forCloudCredentials]
 *                  |--> (defer)
 *
 * [waiting_any] --> (defer)
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
 *
 * [downloading] --*--> (defer) // success or known error
 *                 |--> [waiting_exponentialBackoff]
 *
 * [uploading] --*--> [synced] // success
 *               |--> [waiting_exponentialBackoff]
 *               |--> (defer) // known error
 *
 * [synced] --*--> [waiting_forInternet]
 *            |--> [waiting_forCloudCredentials]
 *            |--> [waiting_randomizedUploadDelay]
 *            |--> [disabled]
 *            |--> (defer)
 *
 * [disabled] --> (defer)
 */

enum SyncManagerState: Equatable, CustomStringConvertible {
	
	case initializing
	case updatingCloud(details: SyncManagerState_UpdatingCloud)
	case downloading(details: SyncManagerState_Progress)
	case uploading(details: SyncManagerState_Progress)
	case waiting(details: SyncManagerState_Waiting)
	case synced
	case disabled
	
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
		}
	}
	
	// Simplified initializers:
	
	static func updatingCloud_creatingRecordZone() -> SyncManagerState {
		return .updatingCloud(details: SyncManagerState_UpdatingCloud(kind: .creatingRecordZone))
	}
	
	static func updatingCloud_deletingRecordZone() -> SyncManagerState {
		return .updatingCloud(details: SyncManagerState_UpdatingCloud(kind: .deletingRecordZone))
	}
	
	static func waiting_forInternet() -> SyncManagerState {
		return .waiting(details: SyncManagerState_Waiting(kind: .forInternet))
	}
	
	static func waiting_forCloudCredentials() -> SyncManagerState {
		return .waiting(details: SyncManagerState_Waiting(kind: .forCloudCredentials))
	}
	
	static func waiting_exponentialBackoff(
		_ parent: SyncManager,
		delay: TimeInterval,
		error: Error
	) -> SyncManagerState {
		return .waiting(details: SyncManagerState_Waiting(
			kind: .exponentialBackoff(error),
			parent: parent,
			delay: delay
		))
	}
	
	static func waiting_randomizedUploadDelay(
		_ parent: SyncManager,
		delay: TimeInterval
	) -> SyncManagerState {
		return .waiting(details: SyncManagerState_Waiting(
			kind: .randomizedUploadDelay,
			parent: parent,
			delay: delay
		))
	}
}

/// Details concerning the type of changes being made to the CloudKit container(s).
///
class SyncManagerState_UpdatingCloud: Equatable {
	
	enum Kind {
		case creatingRecordZone
		case deletingRecordZone
	}
	
	let kind: Kind
	
	private(set) var isCancelled = false
	private(set) var operation: CKOperation? = nil
	
	init(kind: Kind) {
		self.kind = kind
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
	
	static func == (lhs: SyncManagerState_UpdatingCloud, rhs: SyncManagerState_UpdatingCloud) -> Bool {
		return lhs.kind == rhs.kind
	}
}

/// Exposes an ObservableObject that can be used by the UI to display progress information.
/// All changes to `@Published` properties will be made on the UI thread.
///
class SyncManagerState_Progress: ObservableObject, Equatable {
	
	@Published var totalCount: Int
	@Published var completedCount: Int = 0
	@Published var inFlightCount: Int = 0
	@Published var inFlightProgress: Progress? = nil
	
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
	
	static func == (lhs: SyncManagerState_Progress, rhs: SyncManagerState_Progress) -> Bool {
		// Equality for this class is is based on pointers
		return lhs === rhs
	}
}

/// Details concerning what/why the SyncManager is temporarily paused.
/// Sometimes these delays can be manually cancelled by the user.
///
class SyncManagerState_Waiting: Equatable {
	
	enum Kind: Equatable {
		case forInternet
		case forCloudCredentials
		case exponentialBackoff(Error)
		case randomizedUploadDelay
		
		static func == (lhs: SyncManagerState_Waiting.Kind, rhs: SyncManagerState_Waiting.Kind) -> Bool {
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
		weak var parent: SyncManager?
		let delay: TimeInterval
		let startDate: Date
		let fireDate: Date
		
		static func == (lhs: SyncManagerState_Waiting.WaitingUntil,
		                rhs: SyncManagerState_Waiting.WaitingUntil
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
	
	init(kind: Kind, parent: SyncManager, delay: TimeInterval) {
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
		until?.parent?.updateState(finishing: self)
	}
	
	/// Allows the user to terminate the delay early.
	///
	func skip() {
		timerFire()
	}
	
	static func == (lhs: SyncManagerState_Waiting, rhs: SyncManagerState_Waiting) -> Bool {
		
		return (lhs.kind == rhs.kind) && (lhs.until == rhs.until)
	}
}
