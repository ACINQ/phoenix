import Foundation
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SyncTxActor"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

/// This class implements the state machine in a thread-safe actor.
/// See `SyncTxManager_State.swift` for state machine diagrams
///
actor SyncTxManager_Actor {
	
	private var waitingForDatabases = true
	
	private var waitingForInternet = false
	private var waitingForCloudCredentials = false
	
	private var isEnabled: Bool
	private var needsCreateRecordZone: Bool
	private var needsDeleteRecordZone: Bool
	private var needsDownloadExisting = false
	
	private var paymentsQueueCount: Int = 0
	
	private var state: SyncTxManager_State
	private var pendingSettings: SyncTxManager_PendingSettings? = nil
	
	var activeState: SyncTxManager_State {
		return state
	}
	
	init(isEnabled: Bool, recordZoneCreated: Bool, hasDownloadedRecords: Bool) {
		self.isEnabled = isEnabled
		if isEnabled {
			needsCreateRecordZone = !recordZoneCreated
			needsDownloadExisting = !hasDownloadedRecords
			needsDeleteRecordZone = false
		} else {
			needsCreateRecordZone = false
			needsDownloadExisting = false
			needsDeleteRecordZone = recordZoneCreated
		}
		
		state = .initializing
	}
	
	func markDatabasesReady() -> SyncTxManager_State? {
		
		waitingForDatabases = false
		switch state {
			case .initializing:
				return simplifiedStateFlow()
			default:
				return nil
		}
	}
	
	func networkStatusChanged(hasInternet: Bool) -> SyncTxManager_State? {
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
	
	func cloudCredentialsChanged(hasCloudCredentials: Bool) -> SyncTxManager_State? {
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
	
	func queueCountChanged(_ count: Int, wait: SyncTxManager_State_Waiting?) -> SyncTxManager_State? {
		
		paymentsQueueCount = count
		guard count > 0 else {
			return nil
		}
		switch state {
			case .uploading(let details):
				details.setTotalCount(count)
				return nil
			case .synced:
				if let wait = wait {
					log.debug("state = waiting(randomizedUploadDelay)")
					state = .waiting(details: wait)
					return state
				} else {
					return simplifiedStateFlow()
				}
			default:
				return nil
		}
	}
	
	func didCreateRecordZone() -> SyncTxManager_State? {
		log.trace("didCreateRecordZone()")
		
		needsCreateRecordZone = false
		switch state {
			case .updatingCloud(let details):
				switch details.kind {
					case .creatingRecordZone:
						return simplifiedStateFlow()
					default:
						return nil
				}
			default:
				return nil
		}
	}
	
	func didDeleteRecordZone() -> SyncTxManager_State? {
		log.trace("didDeleteRecordZone()")
		
		needsDeleteRecordZone = false
		switch state {
			case .updatingCloud(let details):
				switch details.kind {
					case .deletingRecordZone:
						return simplifiedStateFlow()
					default:
						return nil
				}
			default:
				return nil
		}
	}
	
	func didDownloadPayments() -> SyncTxManager_State? {
		log.trace("didDownloadPayments()")
		
		needsDownloadExisting = false
		switch state {
			case .downloading:
				return simplifiedStateFlow()
			default:
				return nil
		}
	}
	
	func didUploadPayments() -> SyncTxManager_State? {
		log.trace("didUploadPayments()")
		
		switch state {
			case .uploading:
				return simplifiedStateFlow()
			default:
				return nil
		}
	}
	
	func handleError(
		isNotAuthenticated: Bool,
		isZoneNotFound: Bool,
		wait: SyncTxManager_State_Waiting?
	) -> SyncTxManager_State? {
		
		if isNotAuthenticated {
			waitingForCloudCredentials = true
		}
		if isZoneNotFound {
			needsCreateRecordZone = true
		}
		
		switch state {
			case .updatingCloud: fallthrough
			case .downloading: fallthrough
			case .uploading:
					
				if let wait = wait {
					state = .waiting(details: wait)
					return state
				} else {
					return simplifiedStateFlow()
				}
		
			default:
				return nil
		}
	}
	
	func finishWaiting(_ sender: SyncTxManager_State_Waiting) -> SyncTxManager_State? {
		log.trace("finishWaiting()")
		
		guard case .waiting(let details) = state, details == sender else {
			// Current state doesn't match parameter.
			// So we ignore the function call.
			return nil
		}
		
		switch details.kind {
			case .exponentialBackoff:
				return simplifiedStateFlow()
			case .randomizedUploadDelay:
				return simplifiedStateFlow()
			default:
				return nil
		}
	}
	
	func enqueuePendingSettings(_ pending: SyncTxManager_PendingSettings) -> Bool {
		
		let willEnable = pending.paymentSyncing == .willEnable
		if willEnable {
			if !isEnabled {
				log.debug("pendingSettings = \(pending))")
				pendingSettings = pending
				return true // changed
			} else {
				log.debug("pendingSettings = nil (already enabled)")
				pendingSettings = nil
				return false // ignored
			}
			
		} else /* if !willEnable */ {
			if isEnabled {
				log.debug("pendingSettings = \(pending))")
				pendingSettings = pending
				return true // changed
			} else {
				log.debug("pendingSettings = nil (already disabled)")
				pendingSettings = nil
				return false // ignored
			}
		}
	}
	
	func dequeuePendingSettings(
		_ pending: SyncTxManager_PendingSettings,
		approved: Bool
	) -> (Bool, SyncTxManager_State?) {
		
		if pendingSettings != pending {
			// Current state doesn't match parameter.
			// So we ignore the function call.
			return (false, nil)
		}
		pendingSettings = nil
		
		if !approved {
			return (true, nil)
		}
		
		if pending.paymentSyncing == .willEnable {
			
			// Transitioning to enabled state
			
			if !isEnabled {
			
				log.debug("Transitioning to enabled state")
				
				isEnabled = true
				needsCreateRecordZone = true
				needsDownloadExisting = true
				needsDeleteRecordZone = false
				
				switch state {
					case .updatingCloud(let details):
						switch details.kind {
							case .deletingRecordZone:
								details.cancel()
								return (true, nil)
							default:
								return (true, nil)
						}
					case .disabled:
						return (true, simplifiedStateFlow())
					default:
						return (true, nil)
				}
				
			} else {
				
				log.debug("Reqeust to transition to enabled state, but already enabled")
				return (true, nil)
			}
			
		} else /* if pending.paymentSyncing == .willDisable */ {
			
			// Transitioning to disabled state
			
			if isEnabled {
			
				log.debug("Transitioning to disabled state")
				
				isEnabled = false
				needsCreateRecordZone = false
				needsDownloadExisting = false
				needsDeleteRecordZone = true
				
				switch state {
					case .updatingCloud(let details):
						switch details.kind {
							case .creatingRecordZone:
								details.cancel()
								return (true, nil)
							default:
								return (true, nil)
						}
					case .downloading(let progress):
						progress.cancel()
						return (true, nil)
					case .uploading(let progress):
						progress.cancel()
						return (true, nil)
					case .waiting(let details):
						// Careful: calling `details.skip` within `queue.sync` will cause deadlock.
						DispatchQueue.global(qos: .default).async {
							details.skip()
						}
						return (true, nil)
					case .synced:
						return (true, simplifiedStateFlow())
					default:
						return (true, nil)
				}
				
			} else {
				
				log.debug("Request to transition to disabled state, but already disabled")
				return (true, nil)
			}
		}
	}
	
	private func simplifiedStateFlow() -> SyncTxManager_State? {
		
		let prvState = state
		
		if waitingForInternet {
			state = .waiting_forInternet()
		} else if waitingForCloudCredentials {
			state = .waiting_forCloudCredentials()
		} else if isEnabled {
			if needsCreateRecordZone {
				state = .updatingCloud_creatingRecordZone()
			} else if needsDownloadExisting {
				state = .downloading(details: SyncTxManager_State_Progress(
					totalCount: 0
				))
			} else if paymentsQueueCount > 0 {
				state = .uploading(details: SyncTxManager_State_Progress(
					totalCount: paymentsQueueCount
				))
			} else {
				state = .synced
			}
		} else {
			if needsDeleteRecordZone {
				state = .updatingCloud_deletingRecordZone()
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
