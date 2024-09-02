import Foundation
import Combine
import CloudKit
import CryptoKit
import Network
import PhoenixShared

fileprivate let filename = "SyncBackupManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/**
 * CloudKit hard limits (from the docs):
 *
 * Maximum number of operations in a request        :   200
 * Maximum number of records in a response          :   200
 * Maximum number of tokens in a request            :   200
 * Maximum record size (not including Asset fields) :  1 MB
 * Maximum file size of an Asset field              : 50 MB
 * Maximum number of source references to a single  :
 *         target where the action is delete self   :   750
*/

let payments_record_table_name = "payments"
let payments_record_column_data = "encryptedData"
let payments_record_column_meta = "encryptedMeta"

let contacts_record_table_name = "contacts"
let contacts_record_column_data = "encryptedData"
let contacts_record_column_photo = "photo" // CKAsset: automatically encrypted by CloudKit

struct ConsecutivePartialFailure {
	var count: Int
	var error: CKError?
}

// --------------------------------------------------------------------------------
// MARK: -
// --------------------------------------------------------------------------------

/// Encompasses the logic for syncing data with Apple's CloudKit database.
///
class SyncBackupManager {
	
	/// Access to parent for shared logic.
	///
	weak var parent: SyncManager? = nil
	
	/// The wallet info, such as nodeID, cloudKey, etc.
	///
	let walletInfo: WalletManager.WalletInfo
	
	/// Informs the user interface regarding the activities of the SyncBackupManager.
	/// This includes various errors & active upload progress.
	///
	/// Changes to this publisher will always occur on the main thread.
	///
	let statePublisher = CurrentValueSubject<SyncBackupManager_State, Never>(.initializing)
	
	/// Informs the user interface about a pending change to the SyncBackupManager's global settings.
	///
	/// Changes to this publisher will always occur on the main thread.
	/// 
	let pendingSettingsPublisher = CurrentValueSubject<SyncBackupManager_PendingSettings?, Never>(nil)
	
	/// Implements the state machine in a thread-safe actor.
	/// 
	let actor: SyncBackupManager_Actor
	
	var consecutiveErrorCount = 0
	var consecutivePartialFailures: [String: ConsecutivePartialFailure] = [:]
	
	private var _cloudKitDb: CloudKitDb? = nil // see getter method
	
	private var cancellables = Set<AnyCancellable>()
	
	init(walletInfo: WalletManager.WalletInfo) {
		log.trace("init()")
		
		self.walletInfo = walletInfo
		
		let encryptedNodeId = walletInfo.encryptedNodeId
		self.actor = SyncBackupManager_Actor(
			isEnabled: Prefs.shared.backupTransactions.isEnabled,
			recordZoneCreated: Prefs.shared.backupTransactions.recordZoneCreated(encryptedNodeId),
			hasDownloadedPayments: Prefs.shared.backupTransactions.hasDownloadedPayments(encryptedNodeId),
			hasDownloadedContacts: Prefs.shared.backupTransactions.hasDownloadedContacts(encryptedNodeId)
		)
		
		waitForDatabases()
	}
	
	var cloudKitDb: CloudKitDb {
		get { return _cloudKitDb! }
	}
	
	var cloudKey: SymmetricKey {
		let cloudKeyData = walletInfo.cloudKey.toByteArray().toSwiftData()
		return SymmetricKey(data: cloudKeyData)
	}
	
	var encryptedNodeId: String {
		return walletInfo.encryptedNodeId
	}
	
	// ----------------------------------------
	// MARK: Monitors
	// ----------------------------------------
	
	private func startPaymentsQueueCountMonitor() {
		log.trace("startPaymentsQueueCountMonitor()")
		
		// Kotlin suspend functions are currently only supported on the main thread
		assert(Thread.isMainThread, "Kotlin ahead: background threads unsupported")
		
		self.cloudKitDb.payments.queueCountPublisher().sink {[weak self] (queueCount: Int64) in
			log.debug("payments.queueCountPublisher().sink(): count = \(queueCount)")
			
			guard let self = self else {
				return
			}
			
			let count = Int(clamping: queueCount)
			
			let wait: SyncBackupManager_State_Waiting?
			if Prefs.shared.backupTransactions.useUploadDelay {
				let delay = TimeInterval.random(in: 10 ..< 900)
				wait = SyncBackupManager_State_Waiting(
					kind: .randomizedUploadDelay,
					parent: self,
					delay: delay
				)
			} else {
				wait = nil
			}
			
			Task {
				if let newState = await self.actor.paymentsQueueCountChanged(count, wait: wait) {
					self.handleNewState(newState)
				}
			}

		}.store(in: &cancellables)
	}
	
	private func startContactsQueueCountMonitor() {
		log.trace("startContactsQueueCountMonitor()")
		
		// Kotlin suspend functions are currently only supported on the main thread
		assert(Thread.isMainThread, "Kotlin ahead: background threads unsupported")
		
		self.cloudKitDb.contacts.queueCountPublisher().sink {[weak self] (queueCount: Int64) in
			log.debug("contacts.queueCountPublisher().sink(): count = \(queueCount)")
			
			guard let self = self else {
				return
			}
			
			// Note: Upload delay doesn't apply to contacts.
			
			let count = Int(clamping: queueCount)
			Task {
				if let newState = await self.actor.contactsQueueCountChanged(count, wait: nil) {
					self.handleNewState(newState)
				}
			}

		}.store(in: &cancellables)
	}
	
	private func startPreferencesMonitor() {
		log.trace("startPreferencesMonitor()")
		
		var isFirstFire = true
		Prefs.shared.backupTransactions.isEnabledPublisher.sink {[weak self](shouldEnable: Bool) in
			
			if isFirstFire {
				isFirstFire = false
				return
			}
			guard let self = self else {
				return
			}
			
			log.debug("Prefs.shared.backupTransactions_isEnabled = \(shouldEnable ? "true" : "false")")
			
			let delay = 30.seconds()
			let pendingSettings = shouldEnable ?
				SyncBackupManager_PendingSettings(self, enableSyncing: delay)
			:	SyncBackupManager_PendingSettings(self, disableSyncing: delay)
			
			Task {
				if await self.actor.enqueuePendingSettings(pendingSettings) {
					self.publishPendingSettings(pendingSettings)
				}
			}
			
		}.store(in: &cancellables)
	}
	
	// ----------------------------------------
	// MARK: Publishers
	// ----------------------------------------
	
	func publishNewState(_ state: SyncBackupManager_State) {
		log.trace("publishNewState()")
		
		// Contract: Changes to this publisher will always occur on the main thread.
		let block = {
			self.statePublisher.value = state
		}
		if Thread.isMainThread {
			block()
		} else {
			DispatchQueue.main.async { block() }
		}
	}
	
	func publishPendingSettings(_ pending: SyncBackupManager_PendingSettings?) {
		log.trace("publishPendingSettings()")
		
		// Contract: Changes to this publisher will always occur on the main thread.
		let block = {
			self.pendingSettingsPublisher.value = pending
		}
		if Thread.isMainThread {
			block()
		} else {
			DispatchQueue.main.async { block() }
		}
	}
	
	// ----------------------------------------
	// MARK: External Control
	// ----------------------------------------
	
	/// Called from SyncManager; part of SyncManagerProtocol
	///
	func networkStatusChanged(hasInternet: Bool) {
		log.trace("networkStatusChanged(hasInternet: \(hasInternet))")
		
		Task {
			if let newState = await self.actor.networkStatusChanged(hasInternet: hasInternet) {
				self.handleNewState(newState)
			}
		}
	}
	
	/// Called from SyncManager; part of SyncManagerProtocol
	///
	func cloudCredentialsChanged(hasCloudCredentials: Bool) {
		log.trace("cloudCredentialsChanged(hasCloudCredentials: \(hasCloudCredentials))")
		
		Task {
			if let newState = await self.actor.cloudCredentialsChanged(hasCloudCredentials: hasCloudCredentials) {
				self.handleNewState(newState)
			}
		}
	}
	
	/// Called from `SyncBackupManager_PendingSettings`
	///
	func dequeuePendingSettings(_ pending: SyncBackupManager_PendingSettings, approved: Bool) {
		log.trace("dequeuePendingSettings(_, approved: \(approved ? "true" : "false"))")
		
		Task {
			let (accepted, newState) = await self.actor.dequeuePendingSettings(pending, approved: approved)
			if accepted {
				self.publishPendingSettings(nil)
				if !approved {
					if pending.paymentSyncing == .willEnable {
						// We were going to enable cloud syncing.
						// But the user just changed their mind, and cancelled it.
						// So now we need to disable it again.
						Prefs.shared.backupTransactions.isEnabled = false
					} else {
						// We were going to disable cloud syncing.
						// But the user just changed their mind, and cancelled it.
						// So now we need to enable it again.
						Prefs.shared.backupTransactions.isEnabled = true
					}
				}
			}
			if let newState = newState {
				self.handleNewState(newState)
			}
		}
	}
	
	/// Called from `SyncBackupManager_State_Waiting`
	///
	func finishWaiting(_ waiting: SyncBackupManager_State_Waiting) {
		log.trace("finishWaiting()")
		
		Task {
			if let newState = await self.actor.finishWaiting(waiting) {
				self.handleNewState(newState)
			}
		}
	}
	
	/// Used when closing the corresponding wallet.
	/// We transition to a terminal state.
	///
	func shutdown() {
		log.trace("shutdown()")
		
		Task {
			if let newState = await self.actor.shutdown() {
				self.handleNewState(newState)
			}
		}
		
		cancellables.removeAll()
	}
	
	// ----------------------------------------
	// MARK: Flow
	// ----------------------------------------
	
	func handleNewState(_ newState: SyncBackupManager_State) {
		
		log.trace("state = \(newState)")
		switch newState {
			case .updatingCloud(let details):
				switch details.kind {
					case .creatingRecordZone:
						createRecordZone(details)
					case .deletingRecordZone:
						deleteRecordZone(details)
				}
			case .downloading(let details):
				if details.needsDownloadPayments {
					downloadPayments(details)
				}
				if details.needsDownloadContacts {
					downloadContacts(details)
				}
			case .uploading(let details):
				if details.payments_pendingCount > 0 {
					uploadPayments(details)
				} else {
					uploadContacts(details)
				}
			default:
				break
		}
		
		publishNewState(newState)
	}
	
	/// We have to wait until the databases are setup and ready.
	/// This may take a moment if a migration is triggered.
	///
	private func waitForDatabases() {
		log.trace("waitForDatabases()")
		
		Task { @MainActor in
			
			let databaseManager = Biz.business.databaseManager
			do {
				let cloudKitDb = try await databaseManager.cloudKitDb() as! CloudKitDb
				self._cloudKitDb = cloudKitDb
				
				if let newState = await self.actor.markDatabasesReady() {
					self.handleNewState(newState)
				}
				
				DispatchQueue.main.async {
					self.startPaymentsQueueCountMonitor()
					self.startContactsQueueCountMonitor()
					self.startPreferencesMonitor()
				}
				
			} catch {
				
				assertionFailure("Unable to extract cloudKitDb")
			}
			
		} // </Task>
	}
	
	/// We create a dedicated CKRecordZone for each wallet.
	/// This allows us to properly segregate transactions between multiple wallets.
	/// Before we can interact with the RecordZone we have to explicitly create it.
	///
	private func createRecordZone(_ state: SyncBackupManager_State_UpdatingCloud) {
		log.trace("createRecordZone()")
		
		state.task = Task {
			log.trace("createRecordZone(): starting task")
			
			let privateCloudDatabase = CKContainer.default().privateCloudDatabase
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = true
			
			do {
				try await privateCloudDatabase.configuredWith(configuration: configuration) { database in
				
					log.trace("createRecordZone(): configured")
					
					if state.isCancelled {
						throw CKError(.operationCancelled)
					}
					
					let rzId = self.recordZoneID()
					let recordZone = CKRecordZone(zoneID: rzId)
					
					let (saveResults, _) = try await database.modifyRecordZones(
						saving: [recordZone],
						deleting: []
					)
					
					// saveResults: [CKRecordZone.ID : Result<CKRecordZone, Error>]
					
					if let result = saveResults[rzId] {
						if case let .failure(error) = result {
							log.warning("createRecordZone(): perZoneResult: failure")
							throw error
						}
					} else {
						log.error("createRecordZone(): result missing: recordZone")
						throw CKError(CKError.zoneNotFound)
					}
					
					log.trace("createRecordZone(): perZoneResult: success")
					
					Prefs.shared.backupTransactions.setRecordZoneCreated(true, self.encryptedNodeId)
					self.consecutiveErrorCount = 0
						
					if let newState = await self.actor.didCreateRecordZone() {
						self.handleNewState(newState)
					}
					
				} // </configuredWith>
				
			} catch {
				
				log.error("createRecordZone(): error = \(error)")
				self.handleError(error)
			}
		} // </Task>
	}
	
	private func deleteRecordZone(_ state: SyncBackupManager_State_UpdatingCloud) {
		log.trace("deleteRecordZone()")
		
		state.task = Task {
			log.trace("deleteRecordZone(): starting task")
			
			let privateCloudDatabase = CKContainer.default().privateCloudDatabase
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = true
			
			do {
				try await privateCloudDatabase.configuredWith(configuration: configuration) { database in
				
					log.trace("deleteRecordZone(): configured")
					
					if state.isCancelled {
						throw CKError(.operationCancelled)
					}
					
					// Step 1 of 2:
					
					let rzId = recordZoneID()
					
					let (_, deleteResults) = try await database.modifyRecordZones(
						saving: [],
						deleting: [rzId]
					)
					
					// deleteResults: [CKRecordZone.ID : Result<Void, Error>]
					
					if let result = deleteResults[rzId] {
						if case let .failure(error) = result {
							log.warning("deleteRecordZone(): perZoneResult: failure")
							throw error
						}
					} else {
						log.error("deleteRecordZone(): result missing: recordZone")
						throw CKError(CKError.zoneNotFound)
					}
					
					log.trace("deleteRecordZone(): perZoneResult: success")
					
					// Step 2 of 2:
					
					try await Task { @MainActor in
						try await self.cloudKitDb.payments.clearDatabaseTables()
						try await self.cloudKitDb.contacts.clearDatabaseTables()
					}.value
					
					// Done !
					
					Prefs.shared.backupTransactions.setRecordZoneCreated(false, self.encryptedNodeId)
					self.consecutiveErrorCount = 0
					
					if let newState = await self.actor.didDeleteRecordZone() {
						self.handleNewState(newState)
					}
					
				} // </configuredWith>
				
			} catch {
				
				log.error("deleteRecordZone(): error = \(error)")
				self.handleError(error)
			}
		} // </Task>
	}
	
	// ----------------------------------------
	// MARK: Record Zones
	// ----------------------------------------
	
	func recordZoneName() -> String {
		return self.encryptedNodeId
	}
	
	func recordZoneID() -> CKRecordZone.ID {
		
		return CKRecordZone.ID(
			zoneName: recordZoneName(),
			ownerName: CKCurrentUserDefaultName
		)
	}
	
	// ----------------------------------------
	// MARK: Utilities
	// ----------------------------------------
	
	func metadataForRecord(_ record: CKRecord) -> Data {
		
		// Source: CloudKit Tips and Tricks - WWDC 2015
		
		let archiver = NSKeyedArchiver(requiringSecureCoding: true)
		record.encodeSystemFields(with: archiver)
		
		return archiver.encodedData
	}
	
	func recordFromMetadata(_ data: Data) -> CKRecord? {
		
		var record: CKRecord? = nil
		do {
			let unarchiver = try NSKeyedUnarchiver(forReadingFrom: data)
			unarchiver.requiresSecureCoding = true
			record = CKRecord(coder: unarchiver)
			
		} catch {
			log.error("Error decoding CKRecord: \(String(describing: error))")
		}
		
		return record
	}
	
	func dateToMillis(_ date: Date) -> Int64 {
		
		return Int64(date.timeIntervalSince1970 * 1_000)
	}
	
	func genRandomBytes(_ count: Int) -> Data {

		var data = Data(count: count)
		let _ = data.withUnsafeMutableBytes { (ptr: UnsafeMutableRawBufferPointer) in
			SecRandomCopyBytes(kSecRandomDefault, count, ptr.baseAddress!)
		}
		return data
	}
	
	// ----------------------------------------
	// MARK: Errors
	// ----------------------------------------
	
	/// Standardized error handling routine for various async operations.
	///
	func handleError(_ error: Error) {
		log.trace("handleError()")
		
		var isOperationCancelled = false
		var isNotAuthenticated = false
		var isZoneNotFound = false
		var minDelay: Double? = nil
		
		if let ckerror = error as? CKError {
			
			switch ckerror.errorCode {
				case CKError.operationCancelled.rawValue:
					isOperationCancelled = true
				
				case CKError.notAuthenticated.rawValue:
					isNotAuthenticated = true
			
				case CKError.accountTemporarilyUnavailable.rawValue:
					isNotAuthenticated = true
				
				case CKError.userDeletedZone.rawValue: fallthrough
				case CKError.zoneNotFound.rawValue:
					isZoneNotFound = true
				
				default: break
			}
			
			// Sometimes a `notAuthenticated` error is hidden in a partial error.
			if let partialErrorsByZone = ckerror.partialErrorsByItemID {
				
				for (_, perZoneError) in partialErrorsByZone {
					let errCode = (perZoneError as NSError).code
					
					if errCode == CKError.notAuthenticated.rawValue {
						isNotAuthenticated = true
					} else if errCode == CKError.accountTemporarilyUnavailable.rawValue {
						isNotAuthenticated = true
					}
				}
			}
			
			// If the error was `requestRateLimited`, then `retryAfterSeconds` may be non-nil.
			// The value may also be set for other errors, such as `zoneBusy`.
			//
			minDelay = ckerror.retryAfterSeconds
		}
		
		let useExponentialBackoff: Bool
		if isOperationCancelled || isNotAuthenticated || isZoneNotFound {
			// There are edge cases to consider.
			// I've witnessed the following:
			// - CKAccountStatus is consistently reported as `.available`
			// - Attempt to create zone consistently fails with "Not Authenticated"
			//
			// This seems to be the case when, for example,
			// the account needs to accept a new "terms of service".
			//
			// After several consecutive failures, the server starts sending us a minDelay value.
			// We should interpret this as a signal to start using exponential backoff.
			//
			if let delay = minDelay, delay > 0.0 {
				useExponentialBackoff = true
			} else {
				useExponentialBackoff = false
			}
		} else {
			useExponentialBackoff = true
		}
		
		let wait: SyncBackupManager_State_Waiting?
		if useExponentialBackoff {
			self.consecutiveErrorCount += 1
			var delay = self.exponentialBackoff()
			if let minDelay = minDelay, delay < minDelay {
				delay = minDelay
			}
			wait = SyncBackupManager_State_Waiting(
				kind   : .exponentialBackoff(error),
				parent : self,
				delay  : delay
			)
		} else {
			wait = nil
		}
		
		Task { [isNotAuthenticated, isZoneNotFound] in
			if let newState = await self.actor.handleError(
				isNotAuthenticated: isNotAuthenticated,
				isZoneNotFound: isZoneNotFound,
				wait: wait
			) {
				self.handleNewState(newState)
			}
		}
		
		if isNotAuthenticated {
			DispatchQueue.main.async {
				self.parent?.checkForCloudCredentials()
			}
		}
	}
	
	private func exponentialBackoff() -> TimeInterval {
		
		assert(consecutiveErrorCount > 0, "Invalid state")
		
		switch consecutiveErrorCount {
			case  1 : return 250.milliseconds()
			case  2 : return 500.milliseconds()
			case  3 : return 1.seconds()
			case  4 : return 2.seconds()
			case  5 : return 4.seconds()
			case  6 : return 8.seconds()
			case  7 : return 16.seconds()
			case  8 : return 32.seconds()
			case  9 : return 64.seconds()
			case 10 : return 128.seconds()
			case 11 : return 256.seconds()
			default : return 512.seconds()
		}
	}
	
	/// Incorporates failures from the last CKModifyRecordsOperation,
	/// and returns a list of permanently failed items.
	///
	func updateConsecutivePartialFailures(
		_ partialFailures: [String: CKError?]
	) -> [String] {
		
		// The rules are:
		// - if an operation fails 2 times in a row with the same error, then we drop the operation
		// - unless the failure was serverChangeError,
		//   which must fail 3 times in a row before being dropped
		
		var permanentFailures: [String] = []
		
		for (id, ckerror) in partialFailures {
			
			guard var cpf = consecutivePartialFailures[id] else {
				consecutivePartialFailures[id] = ConsecutivePartialFailure(
					count: 1,
					error: ckerror
				)
				continue
			}
			
			let isSameError: Bool
			if let lastError = cpf.error {
				if let thisError = ckerror {
					isSameError = lastError.errorCode == thisError.errorCode
				} else {
					isSameError = false
				}
			} else {
				isSameError = (ckerror == nil)
			}
			
			if isSameError {
				cpf.count += 1
				
				var isPermanentFailure: Bool
				if let ckerror = ckerror,
					ckerror.errorCode == CKError.serverRecordChanged.rawValue {
					isPermanentFailure = cpf.count >= 3
				} else {
					isPermanentFailure = cpf.count >= 2
				}
				
				if isPermanentFailure {
					log.debug(
						"""
						Permanent failure: \(id), count=\(cpf.count): \
						\( ckerror == nil ? "<nil>" : String(describing: ckerror!) )
						"""
					)
					
					permanentFailures.append(id)
					self.consecutivePartialFailures[id] = nil
				} else {
					self.consecutivePartialFailures[id] = cpf
				}
				
			} else {
				self.consecutivePartialFailures[id] = ConsecutivePartialFailure(
					count: 1,
					error: ckerror
				)
			}
		}
		
		return permanentFailures
	}
}
