import Foundation
import CloudKit

/* SyncBackupManager State Machine:
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

enum SyncBackupManager_State: Equatable, CustomStringConvertible {
	
	case initializing
	case updatingCloud(details: SyncBackupManager_State_UpdatingCloud)
	case downloading(details: SyncBackupManager_State_Downloading)
	case uploading(details: SyncBackupManager_State_Uploading)
	case waiting(details: SyncBackupManager_State_Waiting)
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
	
	static func updatingCloud_creatingRecordZone() -> SyncBackupManager_State {
		return .updatingCloud(details: SyncBackupManager_State_UpdatingCloud(kind: .creatingRecordZone))
	}
	
	static func updatingCloud_deletingRecordZone() -> SyncBackupManager_State {
		return .updatingCloud(details: SyncBackupManager_State_UpdatingCloud(kind: .deletingRecordZone))
	}
	
	static func waiting_forInternet() -> SyncBackupManager_State {
		return .waiting(details: SyncBackupManager_State_Waiting(kind: .forInternet))
	}
	
	static func waiting_forCloudCredentials() -> SyncBackupManager_State {
		return .waiting(details: SyncBackupManager_State_Waiting(kind: .forCloudCredentials))
	}
	
	static func waiting_exponentialBackoff(
		_ parent: SyncBackupManager,
		delay: TimeInterval,
		error: Error
	) -> SyncBackupManager_State {
		return .waiting(details: SyncBackupManager_State_Waiting(
			kind: .exponentialBackoff(error),
			parent: parent,
			delay: delay
		))
	}
	
	static func waiting_randomizedUploadDelay(
		_ parent: SyncBackupManager,
		delay: TimeInterval
	) -> SyncBackupManager_State {
		return .waiting(details: SyncBackupManager_State_Waiting(
			kind: .randomizedUploadDelay,
			parent: parent,
			delay: delay
		))
	}
}

/// Details concerning the type of changes being made to the CloudKit container(s).
///
class SyncBackupManager_State_UpdatingCloud: Equatable {
	
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
	
	static func == (lhs: SyncBackupManager_State_UpdatingCloud,
	                rhs: SyncBackupManager_State_UpdatingCloud
	) -> Bool {
		return lhs.kind == rhs.kind
	}
}

/// Exposes an ObservableObject that can be used by the UI for various purposes.
/// All changes to `@Published` properties will be made on the UI thread.
///
class SyncBackupManager_State_Downloading: ObservableObject, Equatable {
	
	let needsDownloadPayments: Bool
	let needsDownloadContacts: Bool
	
	@Published private(set) var payments_completedCount: Int = 0
	@Published private(set) var payments_oldestCompletedDownload: Date? = nil
	
	@Published private(set) var contacts_completedCount: Int = 0
	@Published private(set) var contacts_oldestCompletedDownload: Date? = nil
	
	init(needsDownloadPayments: Bool, needsDownloadContacts: Bool) {
		self.needsDownloadPayments = needsDownloadPayments
		self.needsDownloadContacts = needsDownloadContacts
	}
	
	var completedCount: Int {
		return payments_completedCount + contacts_completedCount
	}
	
	private func updateOnMainThread(_ block: @escaping () -> Void) {
		if Thread.isMainThread {
			block()
		} else {
			DispatchQueue.main.async { block() }
		}
	}
	
	func setPayments_oldestCompletedDownload(_ date: Date?) {
		updateOnMainThread {
			self.payments_oldestCompletedDownload = date
		}
	}
	
	func setContacts_oldestCompletedDownload(_ date: Date?) {
		updateOnMainThread {
			self.contacts_oldestCompletedDownload = date
		}
	}
	
	func payments_finishBatch(completed: Int, oldest: Date?) {
		updateOnMainThread {
			self.payments_completedCount += completed
			
			if let oldest = oldest {
				if let prv = self.payments_oldestCompletedDownload {
					if oldest < prv {
						self.payments_oldestCompletedDownload = oldest
					}
				} else {
					self.payments_oldestCompletedDownload = oldest
				}
			}
		}
	}
	
	func contacts_finishBatch(completed: Int, oldest: Date?) {
		updateOnMainThread {
			self.contacts_completedCount += completed
			
			if let oldest = oldest {
				if let prv = self.contacts_oldestCompletedDownload {
					if oldest < prv {
						self.contacts_oldestCompletedDownload = oldest
					}
				} else {
					self.contacts_oldestCompletedDownload = oldest
				}
			}
		}
	}
	
	static func == (lhs: SyncBackupManager_State_Downloading,
	                rhs: SyncBackupManager_State_Downloading
	) -> Bool {
		// Equality for this class is is based on pointers
		return lhs === rhs
	}
}

/// Exposes an ObservableObject that can be used by the UI to display progress information.
/// All changes to `@Published` properties will be made on the UI thread.
///
class SyncBackupManager_State_Uploading: ObservableObject, Equatable {
	
	@Published private(set) var payments_totalCount: Int
	@Published private(set) var payments_completedCount: Int = 0
	@Published private(set) var payments_inFlightCount: Int = 0
	@Published private(set) var payments_inFlightProgress: Progress? = nil
	
	@Published private(set) var contacts_totalCount: Int
	@Published private(set) var contacts_completedCount: Int = 0
	@Published private(set) var contacts_inFlightCount: Int = 0
	@Published private(set) var contacts_inFlightProgress: Progress? = nil
	
	private(set) var isCancelled = false
	private(set) var operation: CKOperation? = nil
	
	var totalCount: Int {
		return payments_totalCount + contacts_totalCount
	}
	
	var completedCount: Int {
		return payments_completedCount + contacts_completedCount
	}
	
	var inFlightCount: Int {
		return payments_inFlightCount + contacts_inFlightCount
	}
	
	var inFlightProgress: Progress? {
		// Note: we only perform one upload at a time (either payments or contacts)
		return payments_inFlightProgress ?? contacts_inFlightProgress
	}
	
	var payments_pendingCount: Int {
		return payments_totalCount - payments_completedCount
	}
	
	var contacts_pendingCount: Int {
		return contacts_totalCount - contacts_completedCount
	}
	
	init(payments_totalCount: Int, contacts_totalCount: Int) {
		self.payments_totalCount = payments_totalCount
		self.contacts_totalCount = contacts_totalCount
	}
	
	private func updateOnMainThread(_ block: @escaping () -> Void) {
		if Thread.isMainThread {
			block()
		} else {
			DispatchQueue.main.async { block() }
		}
	}
	
	func setPayments_totalCount(_ value: Int) {
		updateOnMainThread {
			self.payments_totalCount = value
		}
	}
	
	func setContacts_totalCount(_ value: Int) {
		updateOnMainThread {
			self.contacts_totalCount = value
		}
	}
	
	func setPayments_inFlight(count: Int, progress: Progress) {
		updateOnMainThread {
			self.payments_inFlightCount = count
			self.payments_inFlightProgress = progress
		}
	}
	
	func completePayments_inFlight(_ completed: Int) {
		updateOnMainThread {
			self.payments_completedCount += completed
			self.payments_inFlightCount = 0
			self.payments_inFlightProgress = nil
		}
	}
	
	func completeContacts_inFlight(_ completed: Int) {
		updateOnMainThread {
			self.contacts_completedCount += completed
			self.contacts_inFlightCount = 0
			self.contacts_inFlightProgress = nil
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
	
	static func == (lhs: SyncBackupManager_State_Uploading,
	                rhs: SyncBackupManager_State_Uploading
	) -> Bool {
		// Equality for this class is is based on pointers
		return lhs === rhs
	}
}

/// Details concerning what/why the SyncBackupManager is temporarily paused.
/// Sometimes these delays can be manually cancelled by the user.
///
class SyncBackupManager_State_Waiting: Equatable {
	
	enum Kind: Equatable {
		case forInternet
		case forCloudCredentials
		case exponentialBackoff(Error)
		case randomizedUploadDelay
		
		static func == (lhs: SyncBackupManager_State_Waiting.Kind,
		                rhs: SyncBackupManager_State_Waiting.Kind
		) -> Bool {
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
		weak var parent: SyncBackupManager?
		let delay: TimeInterval
		let startDate: Date
		let fireDate: Date
		
		static func == (lhs: SyncBackupManager_State_Waiting.WaitingUntil,
		                rhs: SyncBackupManager_State_Waiting.WaitingUntil
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
	
	init(kind: Kind, parent: SyncBackupManager, delay: TimeInterval) {
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
	
	static func == (lhs: SyncBackupManager_State_Waiting,
						 rhs: SyncBackupManager_State_Waiting
	) -> Bool {
		return (lhs.kind == rhs.kind) && (lhs.until == rhs.until)
	}
}
