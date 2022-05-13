import Foundation
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SyncSeedActor"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

/// This class implements the state machine in a thread-safe actor.
/// See `SyncSeedManager_State.swift` for state machine diagrams
///
actor SyncSeedManager_Actor {
	
	private var waitingForInternet = true
	private var waitingForCloudCredentials = true
	
	private var isEnabled: Bool
	private var needsUploadSeed: Bool
	private var needsDeleteSeed: Bool
	
	private var state: SyncSeedManager_State
	
	let initialState: SyncSeedManager_State
	
	var activeState: SyncSeedManager_State {
		return state
	}
	
	init(isEnabled: Bool, hasUploadedSeed: Bool) {
		self.isEnabled = isEnabled
		
		let needsUploadSeed: Bool
		let needsDeleteSeed: Bool
		if isEnabled {
			needsUploadSeed = !hasUploadedSeed
			needsDeleteSeed = false
		} else {
			needsUploadSeed = false
			needsDeleteSeed = hasUploadedSeed
		}
		
		self.needsUploadSeed = needsUploadSeed
		self.needsDeleteSeed = needsDeleteSeed
		
		if isEnabled && !needsUploadSeed {
			initialState = .synced
		} else if !isEnabled && !needsDeleteSeed {
			initialState = .disabled
		} else {
			initialState = .waiting_forInternet()
		}
		state = initialState
	}
	
	func didChangeIsEnabled(_ flag: Bool) -> SyncSeedManager_State? {
		log.trace("didChangeIsEnabled = \(flag ? "true" : "false")")

		if flag {
			if !isEnabled {
					
				// From disabled -> To enabled
				log.debug("Transitioning to enabled state")
				
				isEnabled = true
				needsUploadSeed = true
				needsDeleteSeed = false
				
				switch state {
					case .waiting(let details):
						// Careful: calling `details.skip` within `queue.sync` will cause deadlock.
						DispatchQueue.global(qos: .default).async {
							details.skip()
						}
						return nil
					case .disabled:
						return simplifiedStateFlow()
					default:
						return nil
				}
				
			} else {
				log.debug("Reqeust to transition to enabled state, but already enabled")
				return nil
			}

		} else /* if !flag */ {
			if isEnabled {
				
				// From enabled -> To disabled
				log.debug("Transitioning to disabled state")
				
				isEnabled = false
				needsUploadSeed = false
				needsDeleteSeed = true
				
				switch state {
					case .waiting(let details):
						// Careful: calling `details.skip` within `queue.sync` will cause deadlock.
						DispatchQueue.global(qos: .default).async {
							details.skip()
						}
						return nil
					case .synced:
						return simplifiedStateFlow()
					default:
						return nil
				}
				
			} else {
				log.debug("Request to transition to disabled state, but already disabled")
				return nil
			}
		}
	}
	
	func didChangeName() -> SyncSeedManager_State? {
		log.trace("didChangeName()")
		
		needsUploadSeed = true
		
		switch state {
			case .synced:
				return simplifiedStateFlow()
			default:
				return nil
		}
	}
	
	func networkStatusChanged(hasInternet: Bool) -> SyncSeedManager_State? {
		log.trace("networkStatusChanged(hasInternet: \(hasInternet))")
		
		if hasInternet {
			waitingForInternet = false

			switch state {
				case .waiting(let details):
					switch details.kind {
						case .forInternet:
							return simplifiedStateFlow()
						default:
							return nil
					}
				default:
					return nil
			}

		} else /* if !hasInternet */ {
			waitingForInternet = true

			switch state {
				case .synced:
					return simplifiedStateFlow()
				default:
					return nil
			}
		}
	}
	
	func cloudCredentialsChanged(hasCloudCredentials: Bool) -> SyncSeedManager_State? {
		log.trace("cloudCredentialsChanged(hasCloudCredentials: \(hasCloudCredentials))")
		
		if hasCloudCredentials {
			waitingForCloudCredentials = false

			switch state {
				case .waiting(let details):
					switch details.kind {
						case .forCloudCredentials:
							return simplifiedStateFlow()
						default:
							return nil
					}
				default:
					return nil
			}

		} else /* if !hasCloudCredentials */ {
			waitingForCloudCredentials = true

			switch state {
				case .synced:
					return simplifiedStateFlow()
				default:
					return nil
			}
		}
	}
	
	func didUploadSeed(needsReUpload: Bool) -> SyncSeedManager_State? {
		
		switch state {
			case .uploading:
				if needsReUpload {
					needsUploadSeed = true
					state = .uploading
					return state // starting re-upload now
				} else {
					needsUploadSeed = false
					return simplifiedStateFlow()
				}
			default:
				return nil
		}
	}
	
	func didDeleteSeed() -> SyncSeedManager_State? {
		
		switch state {
			case .deleting:
				needsDeleteSeed = false
				return simplifiedStateFlow()
			default:
				return nil
		}
	}
	
	func handleError(
		isNotAuthenticated: Bool,
		wait: SyncSeedManager_State_Waiting?
	) -> SyncSeedManager_State? {
		
		if isNotAuthenticated {
			waitingForCloudCredentials = true
		}
		
		switch state {
			case .uploading: fallthrough
			case .deleting:
				
				if let wait = wait {
					state = .waiting(details: wait)
					return state
				//	state = .waiting_exponentialBackoff(self, delay: delay, error: error)
				} else {
					return simplifiedStateFlow()
				}
				
			default:
				return nil
		}
	}
	
	func finishWaiting(_ sender: SyncSeedManager_State_Waiting) -> SyncSeedManager_State? {
		log.trace("finishWaiting()")
		
		guard case .waiting(let details) = state, details == sender else {
			// Current state doesn't match parameter.
			// So we ignore the function call.
			return nil
		}
		
		switch details.kind {
			case .exponentialBackoff:
				return simplifiedStateFlow()
			default:
				return nil
		}
	}
	
	private func simplifiedStateFlow() -> SyncSeedManager_State? {
		
		let prvState = state
		
		if isEnabled {
			if needsUploadSeed {
				if waitingForInternet {
					state = .waiting_forInternet()
				} else if waitingForCloudCredentials {
					state = .waiting_forCloudCredentials()
				} else {
					state = .uploading
				}
			} else {
				state = .synced
			}
		} else {
			if needsDeleteSeed {
				if waitingForInternet {
					state = .waiting_forInternet()
				} else if waitingForCloudCredentials {
					state = .waiting_forCloudCredentials()
				} else {
					state = .deleting
				}
			} else {
				state = .disabled
			}
		}
		
		if prvState == state {
			return nil // no changes
		} else {
			return state
		}
	}
}
