import Foundation

fileprivate let filename = "SyncBackupActor"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/// This class implements the state machine in a thread-safe actor.
/// See `SyncBackupManager_State.swift` for state machine diagrams
///
actor SyncBackupManager_Actor {
	
	private var waitingForDatabases = true
	
	private var waitingForInternet = false
	private var waitingForCloudCredentials = false
	
	private var isEnabled: Bool
	private var needsCreateRecordZone: Bool
	private var needsDeleteRecordZone: Bool
	private var needsDownloadPayments = false
	private var needsDownloadContacts = false
	
	private var paymentsQueueCount: Int = 0
	private var contactsQueueCount: Int = 0
	
	private var state: SyncBackupManager_State
	private var pendingSettings: SyncBackupManager_PendingSettings? = nil
	
	var activeState: SyncBackupManager_State {
		return state
	}
	
	var isShutdown: Bool {
		return state == .shutdown
	}
	
	init(
		isEnabled: Bool,
		recordZoneCreated: Bool,
		hasDownloadedPayments: Bool,
		hasDownloadedContacts: Bool
	) {
		self.isEnabled = isEnabled
		if isEnabled {
			needsCreateRecordZone = !recordZoneCreated
			needsDownloadPayments = !hasDownloadedPayments
			needsDownloadContacts = !hasDownloadedContacts
			needsDeleteRecordZone = false
		} else {
			needsCreateRecordZone = false
			needsDownloadPayments = false
			needsDownloadContacts = false
			needsDeleteRecordZone = recordZoneCreated
		}
		
		state = .initializing
	}
	
	// --------------------------------------------------
	// MARK: Transition Logic
	// --------------------------------------------------
	
	func markDatabasesReady() -> SyncBackupManager_State? {
		
		waitingForDatabases = false
		switch state {
			case .initializing:
				return simplifiedStateFlow()
			default:
				return nil
		}
	}
	
	func networkStatusChanged(hasInternet: Bool) -> SyncBackupManager_State? {
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
	
	func cloudCredentialsChanged(hasCloudCredentials: Bool) -> SyncBackupManager_State? {
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
	
	func paymentsQueueCountChanged(
		_ count: Int,
		wait: SyncBackupManager_State_Waiting?
	) -> SyncBackupManager_State? {
		
		log.trace("paymentsQueueCountChanged(\(count))")
		
		paymentsQueueCount = count
		guard count > 0 else {
			return nil
		}
		switch state {
			case .uploading(let details):
				details.setPayments_totalCount(count)
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
	
	func contactsQueueCountChanged(
		_ count: Int,
		wait: SyncBackupManager_State_Waiting?
	) -> SyncBackupManager_State? {
		
		log.trace("contactsQueueCountChanged(\(count))")
		
		contactsQueueCount = count
		guard count > 0 else {
			return nil
		}
		switch state {
			case .uploading(let details):
				details.setContacts_totalCount(count)
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
	
	func didCreateRecordZone() -> SyncBackupManager_State? {
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
	
	func didDeleteRecordZone() -> SyncBackupManager_State? {
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
	
	func didDownloadPayments() -> SyncBackupManager_State? {
		log.trace("didDownloadPayments()")
		
		needsDownloadPayments = false
		if needsDownloadContacts {
			return nil
		} else {
			switch state {
				case .downloading:
					return simplifiedStateFlow()
				default:
					return nil
			}
		}
	}
	
	func didDownloadContacts() -> SyncBackupManager_State? {
		log.trace("didDownloadContacts()")
		
		needsDownloadContacts = false
		if needsDownloadPayments {
			return nil
		} else {
			switch state {
				case .downloading:
					return simplifiedStateFlow()
				default:
					return nil
			}
		}
	}
	
	func didUploadItems() -> SyncBackupManager_State? {
		log.trace("didUploadItems()")
		
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
		wait: SyncBackupManager_State_Waiting?
	) -> SyncBackupManager_State? {
		
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
	
	func finishWaiting(_ sender: SyncBackupManager_State_Waiting) -> SyncBackupManager_State? {
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
	
	func enqueuePendingSettings(_ pending: SyncBackupManager_PendingSettings) -> Bool {
		
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
		_ pending: SyncBackupManager_PendingSettings,
		approved: Bool
	) -> (Bool, SyncBackupManager_State?) {
		
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
				needsDownloadPayments = true
				needsDownloadContacts = true
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
				needsDownloadPayments = false
				needsDownloadContacts = false
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
					case .downloading(_):
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
	
	func shutdown() -> SyncBackupManager_State? {
		log.trace(#function)
		
		switch state {
		case .shutdown:
			return nil // already shutdown
		default:
			state = .shutdown
			return state
		}
	}
	
	// --------------------------------------------------
	// MARK: Internal
	// --------------------------------------------------
	
	private func simplifiedStateFlow() -> SyncBackupManager_State? {
		
		let prvState = state
		
		if waitingForInternet {
			state = .waiting_forInternet()
		} else if waitingForCloudCredentials {
			state = .waiting_forCloudCredentials()
		} else if isEnabled {
			if needsCreateRecordZone {
				state = .updatingCloud_creatingRecordZone()
			} else if needsDownloadPayments || needsDownloadContacts {
				state = .downloading(details: SyncBackupManager_State_Downloading(
					needsDownloadPayments: needsDownloadPayments,
					needsDownloadContacts: needsDownloadContacts
				))
			} else if paymentsQueueCount > 0 || contactsQueueCount > 0 {
				state = .uploading(details: SyncBackupManager_State_Uploading(
					payments_totalCount: paymentsQueueCount,
					contacts_totalCount: contactsQueueCount
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
