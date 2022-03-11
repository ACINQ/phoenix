import Foundation

/* SyncSeedManager State Machine:
 *
 * The state is always one of the following:
 *
 * - uploading
 * - deleting
 * - waiting
 *   - forCloudCredentials
 *   - forInternet
 *   - exponentialBackoff
 * - synced
 * - disabled
 *
 * The state is modified via the `updateState` functions:
 *
 * Note that `(defer)` in the state diagram below signifies a transition to the "simplified state flow",
 * as implemented by the `updateState` function.
 *
 * (init) --*--> [synced]
 *          |--> [disabled]
 *          |--> [waiting_forInternet]
 *
 * [uploading] --*--> (defer)
 *               |--> [waiting_exponentialBackoff]
 *
 * [deleting] --*--> (defer)
 *              |--> [waiting_exponentialBackoff]
 *
 * [waiting_any] --> (defer)
 *
 * [synced] -> (defer)
 *
 * [disabled] -> (defer)
 */

enum SyncSeedManager_State: Equatable, CustomStringConvertible {
	
	case uploading
	case deleting
	case waiting(details: SyncSeedManager_State_Waiting)
	case synced
	case disabled
	
	var description: String {
		switch self {
			case .uploading:
				return "uploading"
			case .deleting:
				return "deleting"
			case .waiting(let details):
				switch details.kind {
					case .forInternet:
						return "waiting_forInternet"
					case .forCloudCredentials:
						return "waiting_forCloudCredentials"
					case .exponentialBackoff:
						return "waiting_exponentialBackoff(\(details.until?.delay ?? 0.0))"
				}
			case .synced:
				return "synced"
			case .disabled:
				return "disabled"
		}
	}
	
	static func waiting_forInternet() -> SyncSeedManager_State {
		return .waiting(details: SyncSeedManager_State_Waiting(kind: .forInternet))
	}
	
	static func waiting_forCloudCredentials() -> SyncSeedManager_State {
		return .waiting(details: SyncSeedManager_State_Waiting(kind: .forCloudCredentials))
	}
	
	static func waiting_exponentialBackoff(
		_ parent: SyncSeedManager,
		delay: TimeInterval,
		error: Error
	) -> SyncSeedManager_State {
		return .waiting(details: SyncSeedManager_State_Waiting(
			kind: .exponentialBackoff(error),
			parent: parent,
			delay: delay
		))
	}
}

/// Details concerning what/why the SyncSeedManager is temporarily paused.
/// Sometimes these delays can be manually cancelled by the user.
///
class SyncSeedManager_State_Waiting: Equatable {
	
	enum Kind: Equatable {
		case forInternet
		case forCloudCredentials
		case exponentialBackoff(Error)
		
		static func == (lhs: SyncSeedManager_State_Waiting.Kind, rhs: SyncSeedManager_State_Waiting.Kind) -> Bool {
			switch (lhs, rhs) {
				case (.forInternet, .forInternet): return true
				case (.forCloudCredentials, .forCloudCredentials): return true
				case (.exponentialBackoff(let lhe), .exponentialBackoff(let rhe)):
					return lhe.localizedDescription == rhe.localizedDescription
				default:
					return false
			}
		}
	}
	
	struct WaitingUntil: Equatable {
		weak var parent: SyncSeedManager?
		let delay: TimeInterval
		let startDate: Date
		let fireDate: Date
		
		static func == (lhs: SyncSeedManager_State_Waiting.WaitingUntil,
							 rhs: SyncSeedManager_State_Waiting.WaitingUntil
		) -> Bool {
			return (lhs.parent === rhs.parent) &&
					 (lhs.delay == rhs.delay) &&
					 (lhs.startDate == rhs.startDate) &&
					 (lhs.fireDate == rhs.fireDate)
		}
	}
	
	let kind: Kind
	let until: WaitingUntil?
	
	init(kind: Kind) {
		self.kind = kind
		self.until = nil
	}
	
	init(kind: Kind, parent: SyncSeedManager, delay: TimeInterval) {
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
	
	static func == (lhs: SyncSeedManager_State_Waiting, rhs: SyncSeedManager_State_Waiting) -> Bool {
		
		return (lhs.kind == rhs.kind) && (lhs.until == rhs.until)
	}
}
