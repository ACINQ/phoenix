import Foundation
import Combine
import CloudKit
import CryptoKit
import Network
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SyncManager"
)
#else
fileprivate var log = Logger(OSLog.disabled)
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

fileprivate let record_table_name = "payments"
fileprivate let record_column_data = "encryptedData"
fileprivate let record_column_meta = "encryptedMeta"

/// See `SyncManagerState.swift` for state machine diagrams
/// 
fileprivate struct AtomicState {
	var needsDatabases = true
	
	var waitingForInternet = false
	var waitingForCloudCredentials = false
	
	var isEnabled: Bool
	var needsCreateRecordZone: Bool
	var needsDeleteRecordZone: Bool
	
	var needsDownloadExisting = false
	
	var paymentsQueueCount: Int = 0
	
	var active: SyncManagerState
	var pendingSettings: PendingSettings? = nil
	
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
		
		active = .initializing
	}
}

fileprivate struct DownloadedItem {
	let record: CKRecord
	let unpaddedSize: Int
	let payment: Lightning_kmpWalletPayment
	let metadata: WalletPaymentMetadataRow?
}

fileprivate struct UploadOperationInfo {
	let batch: FetchQueueBatchResult
	
	var reverseMap: [CKRecord.ID: WalletPaymentId] = [:]
	var unpaddedMap: [WalletPaymentId: Int] = [:]
	
	var completedRowids: [Int64] = []
	
	var partialFailures: [WalletPaymentId: CKError?] = [:]
	
	var savedRecords: [CKRecord] = []
	var deletedRecordIds: [CKRecord.ID] = []
}

fileprivate struct ConsecutivePartialFailure {
	var count: Int
	var error: CKError?
}

// --------------------------------------------------------------------------------
// MARK: -
// --------------------------------------------------------------------------------

/// Encompasses all the logic for syncing data with Apple's CloudKit database.
///
class SyncManager {
	
	/// The cloudKey is derived from the user's seed.
	/// It's used to encrypt data before uploading to the cloud.
	/// The data stored in the cloud is an encrypted blob, and requires the cloudKey for decryption.
	///
	private let cloudKey: SymmetricKey
	
	/// The encryptedNodeId is created via: Hash(cloudKey + nodeID)
	///
	/// All data from a user's wallet is stored in the user's CKContainer.default().privateCloudDatabase.
	/// Within the privateCloudDatabase, we create a dedicated CKRecordZone for each wallet,
	/// where recordZone.name == encryptedNodeId
	///
	private let encryptedNodeId: String
	
	/// Informs the user interface regarding the activities of the SyncManager.
	/// This includes various errors & active upload progress.
	///
	/// Changes to this publisher will always occur on the main thread.
	///
	public let statePublisher = CurrentValueSubject<SyncManagerState, Never>(.initializing)
	
	/// Informs the user interface about a pending change to the SyncManager's global settings.
	/// 
	public let pendingSettingsPublisher = CurrentValueSubject<PendingSettings?, Never>(nil)
	
	private let queue = DispatchQueue(label: "SyncManager")
	private var state: AtomicState // must be read/modified within queue
	
	private let networkMonitor: NWPathMonitor
	
	private var consecutiveErrorCount = 0
	private var consecutivePartialFailures: [WalletPaymentId: ConsecutivePartialFailure] = [:]
	
	private var _paymentsDb: SqlitePaymentsDb? = nil // see getter method
	private var _cloudKitDb: CloudKitDb? = nil       // see getter method
	
	private var cancellables = Set<AnyCancellable>()
	
	init(cloudKey: Bitcoin_kmpByteVector32, encryptedNodeId: String) {
		log.trace("init()")
		
		self.cloudKey = SymmetricKey(data: cloudKey.toByteArray().toSwiftData())
		self.encryptedNodeId = encryptedNodeId
		
		self.state = AtomicState(
			isEnabled: Prefs.shared.backupTransactions_isEnabled,
			recordZoneCreated: Prefs.shared.recordZoneCreated(encryptedNodeId: encryptedNodeId),
			hasDownloadedRecords: Prefs.shared.hasDownloadedRecords(encryptedNodeId: encryptedNodeId)
		)
		
		networkMonitor = NWPathMonitor()
		waitForDatabases()
	}
	
	private var paymentsDb: SqlitePaymentsDb {
		get { return _paymentsDb! }
	}
	private var cloudKitDb: CloudKitDb {
		get { return _cloudKitDb! }
	}
	
	// ----------------------------------------
	// MARK: Monitors
	// ----------------------------------------
	
	private func startNetworkMonitor() {
		log.trace("startNetworkMonitor()")
		
		networkMonitor.pathUpdateHandler = {[weak self] (path: NWPath) -> Void in
			
			let hasInternet: Bool
			switch path.status {
				case .satisfied:
					log.debug("nwpath.status.satisfied")
					hasInternet = true
					
				case .unsatisfied:
					log.debug("nwpath.status.unsatisfied")
					hasInternet = false
					
				case .requiresConnection:
					log.debug("nwpath.status.requiresConnection")
					hasInternet = true
					
				@unknown default:
					log.debug("nwpath.status.unknown")
					hasInternet = false
			}
			
			self?.updateState { state, deferToSimplifiedStateFlow in
				
				if hasInternet {
					state.waitingForInternet = false
					
					switch state.active {
						case .waiting(let details):
							switch details.kind {
								case .forInternet:
									deferToSimplifiedStateFlow = true
							
								default: break
							}
							
						default: break
					}
					
				} else {
					state.waitingForInternet = true
					
					switch state.active {
						case .synced:
							log.debug("state.active = waiting(forInternet)")
							state.active = .waiting_forInternet()
					
						default: break
					}
				}
			}
		}
		
		networkMonitor.start(queue: DispatchQueue.main)
	}
	
	private func startCloudStatusMonitor() {
		log.trace("startCloudStatusMonitor()")
		
		NotificationCenter.default.publisher(for: Notification.Name.CKAccountChanged)
			.sink {[weak self] _ in
			
			log.debug("CKAccountChanged")
			DispatchQueue.main.async {
				self?.checkForCloudCredentials()
			}
			
		}.store(in: &cancellables)
	}
	
	private func startQueueCountMonitor() {
		log.trace("setupQueueCountMonitor()")
		
		self.cloudKitDb.fetchQueueCountPublisher().sink {[weak self] (queueCount: Int64) in
			log.debug("fetchQueueCountPublisher().sink(): count = \(queueCount)")
			
			let count = Int(clamping: queueCount)
			
			var delay: TimeInterval? = nil
			if Prefs.shared.backupTransactions_useUploadDelay {
				delay = TimeInterval.random(in: 10 ..< 900)
			}
				
			self?.updateState { state, deferToSimplifiedStateFlow in
				state.paymentsQueueCount = count
				if count > 0 {
					switch state.active {
						case .uploading(let details):
							details.setTotalCount(count)
						case .synced:
							if let delay = delay {
								log.debug("state.active = waiting(randomizedUploadDelay)")
								state.active = .waiting_randomizedUploadDelay(self!, delay: delay)
							} else {
								deferToSimplifiedStateFlow = true
							}
				
						default: break
					}
				}
			}

		}.store(in: &cancellables)
	}
	
	private func startPreferencesMonitor() {
		log.trace("startPreferencesMonitor()")
		
		var isFirstFire = true
		Prefs.shared.backupTransactions_isEnabledPublisher.sink {[weak self](shouldEnable: Bool) in
			
			guard let self = self else {
				return
			}
			if isFirstFire {
				isFirstFire = false
				return
			}
			
			log.debug("Prefs.shared.backupTransactions_isEnabled = \(shouldEnable ? "true" : "false")")
			
			let delay = 30.seconds()
			let pendingSettings = shouldEnable ?
				PendingSettings(self, enableSyncing: delay)
			:	PendingSettings(self, disableSyncing: delay)
			
			var publishMe: PendingSettings? = pendingSettings
			self.updateState { state, _ in
				
				if shouldEnable {
					if !state.isEnabled {
						log.debug("state.pendingSettings = \(pendingSettings))")
						state.pendingSettings = pendingSettings
					} else {
						log.debug("state.pendingSettings = nil (already enabled)")
						state.pendingSettings = nil
						publishMe = nil
					}
					
				} else /* if !shouldEnable */ {
					if state.isEnabled {
						log.debug("state.pendingSettings = \(pendingSettings))")
						state.pendingSettings = pendingSettings
					} else {
						log.debug("state.pendingSettings = nil (already disabled)")
						state.pendingSettings = nil
						publishMe = nil
					}
				}
			}
			
			self.publishPendingSettings(publishMe)
			
		}.store(in: &cancellables)
	}
	
	// ----------------------------------------
	// MARK: Publishers
	// ----------------------------------------
	
	private func publishNewState(_ state: SyncManagerState) {
		log.trace("publishNewState()")
		
		let block = {
			self.statePublisher.value = state
		}
		
		if Thread.isMainThread {
			block()
		} else {
			DispatchQueue.main.async { block() }
		}
	}
	
	private func publishPendingSettings(_ pending: PendingSettings?) {
		log.trace("publishPendingSettings()")
		
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
	// MARK: State Machine
	// ----------------------------------------
	
	func updateState(pending: PendingSettings, approved: Bool) {
		log.trace("updateState(pending: approved: \(approved ? "true" : "false"))")
	
		var shouldPublish = false
		
		updateState { state, deferToSimplifiedStateFlow in
			
			if state.pendingSettings != pending {
				// Current state doesn't match parameter.
				// So we ignore the function call.
				return
			}
			
			state.pendingSettings = nil
			shouldPublish = true
			
			if approved {
				if pending.paymentSyncing == .willEnable {
					
					if !state.isEnabled {
					
						log.debug("Transitioning to enabled state")
						
						state.isEnabled = true
						state.needsCreateRecordZone = true
						state.needsDownloadExisting = true
						state.needsDeleteRecordZone = false
						
						switch state.active {
							case .updatingCloud(let details):
								switch details.kind {
									case .deletingRecordZone:
										details.cancel()
									default: break
								}
							case .disabled:
								deferToSimplifiedStateFlow = true // defer to updateState()
							default: break
						}
						
					} else {
						
						log.debug("Reqeust to transition to enabled state, but already enabled")
					}
					
					// Transitioning to enabled state
					
					
				} else /* if pending.paymentSyncing == .willDisable */ {
					
					if state.isEnabled {
					
						log.debug("Transitioning to disabled state")
						
						state.isEnabled = false
						state.needsCreateRecordZone = false
						state.needsDownloadExisting = false
						state.needsDeleteRecordZone = true
						
						switch state.active {
							case .updatingCloud(let details):
								switch details.kind {
									case .creatingRecordZone:
										details.cancel()
									default: break
								}
							case .downloading(let progress):
								progress.cancel()
							case .uploading(let progress):
								progress.cancel()
							case .waiting(let details):
								// Careful: calling `details.skip` within `queue.sync` will cause deadlock.
								DispatchQueue.global(qos: .default).async {
									details.skip()
								}
							case .synced:
								deferToSimplifiedStateFlow = true // defer to updateState()
							default: break
						}
						
					} else {
						
						log.debug("Request to transition to disabled state, but already disabled")
					}
				}
			} // </if approved>
			
		} // </updateState>
		
		if shouldPublish {
			publishPendingSettings(nil)
			if !approved {
				if pending.paymentSyncing == .willEnable {
					// We were going to enable cloud syncing.
					// But the user just changed their mind, and cancelled it.
					// So now we need to disable it again.
					Prefs.shared.backupTransactions_isEnabled = false
				} else {
					// We were going to disable cloud syncing.
					// But the user just changed their mind, and cancelled it.
					// So now we need to enable it again.
					Prefs.shared.backupTransactions_isEnabled = true
				}
			}
		}
	}
	
	func updateState(finishing waiting: SyncManagerState_Waiting) {
		log.trace("updateState(finishing waiting)")
		
		updateState { state, deferToSimplifiedStateFlow in
			
			guard case .waiting(let details) = state.active, details == waiting else {
				// Current state doesn't match parameter.
				// So we ignore the function call.
				return
			}
			
			switch details.kind {
				case .exponentialBackoff:
					deferToSimplifiedStateFlow = true // defer to updateState()
				case .randomizedUploadDelay:
					deferToSimplifiedStateFlow = true // defer to updateState()
				default:
					break
			}
		}
	}
	
	private func updateState(_ modifyStateBlock: (inout AtomicState, inout Bool) -> Void) {
		
		var changedState: SyncManagerState? = nil
		queue.sync {
			let prvActive = state.active
			var deferToSimplifiedStateFlow = false
			modifyStateBlock(&state, &deferToSimplifiedStateFlow)
			
			if deferToSimplifiedStateFlow {
				// State management deferred to this function.
				// Executing simplified state flow.
				
				if state.waitingForCloudCredentials {
					state.active = .waiting_forCloudCredentials()
				} else if state.waitingForInternet {
					state.active = .waiting_forInternet()
				} else if state.isEnabled {
					if state.needsCreateRecordZone {
						state.active = .updatingCloud_creatingRecordZone()
					} else if state.needsDownloadExisting {
						state.active = .downloading(details: SyncManagerState_Progress(
							totalCount: 0
						))
					} else if state.paymentsQueueCount > 0 {
						state.active = .uploading(details: SyncManagerState_Progress(
							totalCount: state.paymentsQueueCount
						))
					} else {
						state.active = .synced
					}
				} else {
					if state.needsDeleteRecordZone {
						state.active = .updatingCloud_deletingRecordZone()
					} else {
						state.active = .disabled
					}
				}
			
			} // </simplified_state_flow>
			
			if prvActive != state.active {
				changedState = state.active
			}
		
		} // </queue.sync>
		
		if let newState = changedState {
			log.debug("state.active = \(newState)")
			switch newState {
				case .updatingCloud(let details):
					switch details.kind {
						case .creatingRecordZone:
							createRecordZone(details)
						case .deletingRecordZone:
							deleteRecordZone(details)
					}
				case .downloading(let progress):
					downloadPayments(progress)
					break
				case .uploading(let progress):
					uploadPayments(progress)
				default:
					break
			}
			
			publishNewState(newState)
		}
	}
	
	// ----------------------------------------
	// MARK: Flow
	// ----------------------------------------
	
	/// We have to wait until the databases are setup and ready.
	/// This may take a moment if a migration is triggered.
	///
	private func waitForDatabases() {
		log.trace("waitForDatabases()")
		
		var cancellables = Set<AnyCancellable>()
		let finish = {
			log.trace("waitForDatabases(): finish()")
			
			cancellables.removeAll()
			self.updateState { state, deferToSimplifiedStateFlow in
				state.needsDatabases = false
			}
			self.startNetworkMonitor()
			self.startCloudStatusMonitor()
			self.startQueueCountMonitor()
			self.startPreferencesMonitor()
			self.checkForCloudCredentials() // initialization step 2
		}
		
		// Kotlin will crash if we try to use multiple threads (like a real app)
		DispatchQueue.main.async {
			
			let databaseManager = AppDelegate.get().business.databaseManager
			databaseManager.getDatabases().sink { databases in
				
				if let paymentsDb = databases.payments as? SqlitePaymentsDb,
					let cloudKitDb = paymentsDb.cloudKitDb as? CloudKitDb
				{
					self._paymentsDb = paymentsDb
					self._cloudKitDb = cloudKitDb
					
					finish()
					
				} else {
					assertionFailure("Unable to extract paymentsDb ")
				}
				
			}.store(in: &cancellables)
		}
	}
	
	private func checkForCloudCredentials() {
		log.trace("checkForCloudCredentials")
		
		CKContainer.default().accountStatus {[weak self] (accountStatus: CKAccountStatus, error: Error?) in
			
			if let error = error {
				log.warning("Error fetching CKAccountStatus: \(String(describing: error))")
			}
			
			var hasCloudCredentials: Bool
			switch accountStatus {
				case .available:
					log.debug("CKAccountStatus.available")
					hasCloudCredentials = true
					
				case .noAccount:
					log.debug("CKAccountStatus.noAccount")
					hasCloudCredentials = false
					
				case .restricted:
					log.debug("CKAccountStatus.restricted")
					hasCloudCredentials = false
					
				case .couldNotDetermine:
					log.debug("CKAccountStatus.couldNotDetermine")
					hasCloudCredentials = false
					
				case .temporarilyUnavailable:
					log.debug("CKAccountStatus.temporarilyUnavailable")
					hasCloudCredentials = false
				
				@unknown default:
					log.debug("CKAccountStatus.unknown")
					hasCloudCredentials = false
			}
			
			self?.updateState { state, deferToSimplifiedStateFlow in
				
				if hasCloudCredentials {
					state.waitingForCloudCredentials = false
					
					switch state.active {
						case .initializing:
							deferToSimplifiedStateFlow = true
							
						case .waiting(let details):
							switch details.kind {
								case .forCloudCredentials:
									deferToSimplifiedStateFlow = true
									
								default: break
							}
							
						default: break
					}
					
				} else {
					state.waitingForCloudCredentials = true
					
					switch state.active {
						case .initializing: fallthrough
						case .synced:
							log.debug("state.active = waiting(forCloudCredentials)")
							state.active = .waiting_forCloudCredentials()
							
						default: break
					}
				}
			}
		}
	}
	
	/// We create a dedicated CKRecordZone for each wallet.
	/// This allows us to properly segregate transactions between multiple wallets.
	/// Before we can interact with the RecordZone we have to explicitly create it.
	///
	private func createRecordZone(_ updatingCloud: SyncManagerState_UpdatingCloud) {
		log.trace("createRecordZone()")
		
		let finish = { (result: Result<Void, Error>) in
			
			switch result {
			case .success:
				log.trace("createZone(): finish(): success")
				
				Prefs.shared.setRecordZoneCreated(true, encryptedNodeId: self.encryptedNodeId)
				self.consecutiveErrorCount = 0
				self.updateState { state, deferToSimplifiedStateFlow in
					state.needsCreateRecordZone = false
					switch state.active {
					case .updatingCloud(let details):
						switch details.kind {
							case .creatingRecordZone:
								deferToSimplifiedStateFlow = true
							default: break
						}
						default: break
					}
				}
				
			case .failure(let error):
				log.trace("createZone(): finish(): failure")
				self.handleError(error)
			}
		}
		
		let recordZone = CKRecordZone(zoneName: encryptedNodeId)
		
		let operation = CKModifyRecordZonesOperation(
			recordZonesToSave: [recordZone],
			recordZoneIDsToDelete: nil
		)
		
		operation.modifyRecordZonesCompletionBlock =
		{ (added: [CKRecordZone]?, deleted: [CKRecordZone.ID]?, error: Error?) in
			
			log.trace("operation.modifyRecordZonesCompletionBlock()")
			
			if let error = error {
				log.error("Error creating CKRecordZone: \(String(describing: error))")
				finish(.failure(error))
				
			} else {
				log.error("Success creating CKRecordZone")
				finish(.success)
			}
		}
		
		let configuration = CKOperation.Configuration()
		configuration.allowsCellularAccess = true
		
		operation.configuration = configuration
		
		if updatingCloud.register(operation) {
			CKContainer.default().privateCloudDatabase.add(operation)
		} else {
			finish(.failure(CKError(.operationCancelled)))
		}
	}
	
	private func deleteRecordZone(_ updatingCloud: SyncManagerState_UpdatingCloud) {
		log.debug("deleteRecordZone()")
		
		let finish = { (result: Result<Void, Error>) in
			
			switch result {
			case .success:
				log.trace("deleteRecordZone(): finish(): success")
				
				Prefs.shared.setRecordZoneCreated(false, encryptedNodeId: self.encryptedNodeId)
				self.consecutiveErrorCount = 0
				self.updateState { state, deferToSimplifiedStateFlow in
					state.needsDeleteRecordZone = false
					switch state.active {
						case .updatingCloud(let details):
							switch details.kind {
								case .deletingRecordZone:
									deferToSimplifiedStateFlow = true
								default: break
							}
						default: break
					}
				}
				
			case .failure(let error):
				log.trace("deleteRecordZone(): finish(): failure")
				self.handleError(error)
			}
		}
		
		var step1 : (() -> Void)!
		var step2 : (() -> Void)!
		
		step1 = {
			log.trace("deleteRecordZone(): step1()")
		
			let recordZone = CKRecordZone(zoneName: self.encryptedNodeId)
			
			let operation = CKModifyRecordZonesOperation(
				recordZonesToSave: nil,
				recordZoneIDsToDelete: [recordZone.zoneID]
			)
			
			operation.modifyRecordZonesCompletionBlock =
			{ (added: [CKRecordZone]?, deleted: [CKRecordZone.ID]?, error: Error?) in
				
				log.trace("operation.modifyRecordZonesCompletionBlock()")
				
				if let error = error {
					log.error("Error deleting CKRecordZone: \(String(describing: error))")
					finish(.failure(error))
					
				} else {
					log.error("Success deleting CKRecordZone")
					DispatchQueue.main.async {
						step2()
					}
				}
			}
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = true
		
			operation.configuration = configuration
			
			if updatingCloud.register(operation) {
				CKContainer.default().privateCloudDatabase.add(operation)
			} else {
				finish(.failure(CKError(.operationCancelled)))
			}
			
		} // </step1>
		
		step2 = {
			log.trace("deleteRecordZone(): step2()")
			
			// Kotlin will crash if we try to use multiple threads (like a real app)
			assert(Thread.isMainThread, "Kotlin ahead: background threads unsupported")
			
			self.cloudKitDb.clearDatabaseTables { (_, error) in
				
				if let error = error {
					log.error("Error clearing database tables: \(String(describing: error))")
					finish(.failure(error))
					
				} else {
					finish(.success)
				}
			}
			
		} // </step2>
		
		// Go!
		step1()
	}
	
	private func downloadPayments(_ downloadProgress: SyncManagerState_Progress) {
		log.trace("downloadPayments()")
		
		let finish = { (result: Result<Void, Error>) in
			
			switch result {
			case .success:
				log.trace("downloadPayments(): finish(): success")
				
				Prefs.shared.setHasDownloadedRecords(true, encryptedNodeId: self.encryptedNodeId)
				self.consecutiveErrorCount = 0
				self.updateState { state, deferToSimplifiedStateFlow in
					state.needsDownloadExisting = false
					
					switch state.active {
						case .downloading:
							deferToSimplifiedStateFlow = true
						default:
							break
					}
				}
				
			case .failure(let error):
				log.trace("downloadPayments(): finish(): failure")
				self.handleError(error)
			}
		}
		
		var checkDatabase     : (() -> Void)!
		var fetchTotalCount   : ((Date?) -> Void)!
		var startBatchFetch   : ((Date?) -> Void)!
		var performBatchFetch : ((CKQueryOperation, Int) -> Void)!
		var updateDatabase    : (([DownloadedItem], CKQueryOperation.Cursor?, Int) -> Void)!
		var enqueueMissing    : (() -> Void)!
		
		// Step 1 of 6:
		//
		//
		checkDatabase = {
			log.trace("downloadPayments(): checkDatabase()")
			
			// Kotlin will crash if we try to use multiple threads (like a real app)
			assert(Thread.isMainThread, "Kotlin ahead: background threads unsupported")
			
			self.cloudKitDb.fetchOldestCreation() { (millis: KotlinLong?, error: Error?) in
				
				if let error = error {
					finish(.failure(error))
				} else {
					
					let oldestCreationDate = self.millisToDate(millis)
					DispatchQueue.global(qos: .utility).async {
						fetchTotalCount(oldestCreationDate)
					}
				}
			}
		}
		
		// Step 2 of 6:
		//
		// In order to properly track the progress, we need to know the total count.
		// So we start the process by sending a querying for the count.
		//
		fetchTotalCount = { (oldestCreationDate: Date?) in
			log.trace("downloadPayments(): fetchTotalCount()")
			
			// If we want to report proper progress (via `SyncManagerState_UpdatingCloud`),
			// then we need to know the total number of records to be downloaded from the cloud.
			//
			// However, there's a minor problem here:
			// CloudKit doesn't support aggregate queries !
			//
			// So we cannot simply say: SELECT COUNT(*)
			//
			// Our only option (as far as I'm aware of),
			// is to fetch the metadata for every record in the cloud.
			// We would have to do this via recursive batch fetching,
			// and counting the downloaded records as they stream in.
			//
			// The big downfall of this approach is that we end up downloading
			// the CKRecord metadata 2 times for every record :(
			//
			// - first just to count the number of records
			// - and again when we fetch the full record (with encrypted blob)
			//
			// Given this bad situation (Bad Apple),
			// our current choice is to sacrifice the progress details.
			
			startBatchFetch(oldestCreationDate)
			
		} // </fetchTotalCount>
		
		// Step 3 of 6:
		//
		// Prepares the first CKQueryOperation to download a batch of payments from the cloud.
		// There may be multiple batches available for download.
		//
		startBatchFetch = { (oldestCreationDate: Date?) in
			log.trace("downloadPayments(): startBatchFetch()")
			
			let predicate: NSPredicate
			if let oldestCreationDate = oldestCreationDate {
				predicate = NSPredicate(format: "creationDate < %@", oldestCreationDate as CVarArg)
			} else {
				predicate = NSPredicate(format: "TRUEPREDICATE")
			}
			
			let query = CKQuery(
				recordType: record_table_name,
				predicate: predicate
			)
			query.sortDescriptors = [
				NSSortDescriptor(key: "creationDate", ascending: false)
			]
			
			let operation = CKQueryOperation(query: query)
			operation.zoneID = self.recordZoneID()
			
			performBatchFetch(operation, 0)
		
		} // </startBatchFetch>
		
		// Step 4 of 6:
		//
		// Perform the CKQueryOperation to download a batch of payments from the cloud.
		//
		performBatchFetch = { (operation: CKQueryOperation, batch: Int) in
			log.trace("downloadPayments(): performBatchFetch()")
			
			var items: [DownloadedItem] = []
			
			// For the first batch, we want to quickly fetch an item from the cloud,
			// and add it to the database. The faster the better, this way the user
			// knows the app is restoring his/her transactions.
			//
			// After that, we can slowly increase the batch size,
			// as the user becomes aware of what's happening.
			switch batch {
				case 0  : operation.resultsLimit = 1
				case 1  : operation.resultsLimit = 2
				case 2  : operation.resultsLimit = 3
				case 3  : operation.resultsLimit = 4
				default : operation.resultsLimit = 8
			}
			
			operation.recordFetchedBlock = { (record: CKRecord) in
				
				log.debug("Received record:")
				log.debug(" - recordID: \(record.recordID)")
				log.debug(" - creationDate: \(record.creationDate ?? Date.distantPast)")
				
				var unpaddedSize: Int = 0
				var payment: Lightning_kmpWalletPayment? = nil
				var metadata: WalletPaymentMetadataRow? = nil
				
				// Reminder: Kotlin-MPP has horrible multithreading support.
				// It basically doesn't allow you to use multiple threads.
				// Any Kotlin objects we create here will be tied to this background thread.
				// And when we attempt to use them from another thread,
				// Kotlin will throw an exception:
				//
				// > kotlin.native.IncorrectDereferenceException:
				// >   illegal attempt to access non-shared
				// >   fr.acinq.phoenix.db.WalletPaymentId.IncomingPaymentId@2881e28 from other thread
				//
				// We are working around this restriction by freezing objects via `doCopyAndFreeze()`.
				
				if let ciphertext = record[record_column_data] as? Data {
					log.debug(" - data.count: \(ciphertext.count)")
					
					do {
						let box = try ChaChaPoly.SealedBox(combined: ciphertext)
						let cleartext = try ChaChaPoly.open(box, using: self.cloudKey)
						
						let cleartext_kotlin = cleartext.toKotlinByteArray()
						let wrapper = CloudData.companion.cborDeserialize(blob: cleartext_kotlin)
						
						if let wrapper = wrapper {
							
							#if DEBUG
						//	let jsonData = wrapper.jsonSerialize().toSwiftData()
						//	let jsonStr = String(data: jsonData, encoding: .utf8)
						//	log.debug("Downloaded record (JSON representation):\n\(jsonStr ?? "<nil>")")
							#endif
							
							let paddedSize = cleartext.count
							let paddingSize = Int(wrapper.padding?.size ?? 0)
							unpaddedSize = paddedSize - paddingSize
							
							payment = wrapper.unwrap()?.doCopyAndFreeze()
						}
						
					} catch {
						log.error("data decryption error: \(String(describing: error))")
					}
				}
				
				if let asset = record[record_column_meta] as? CKAsset {
					
					var ciphertext: Data? = nil
					if let fileURL = asset.fileURL {
						
						do {
							ciphertext = try Data(contentsOf: fileURL)
						} catch {
							log.error("asset read error: \(String(describing: error))")
						}
						
						if let ciphertext = ciphertext {
							do {
								let box = try ChaChaPoly.SealedBox(combined: ciphertext)
								let cleartext = try ChaChaPoly.open(box, using: self.cloudKey)
								
								let cleartext_kotlin = cleartext.toKotlinByteArray()
								let row = CloudAsset.companion.cloudDeserialize(blob: cleartext_kotlin)
								
								metadata = row?.doCopyAndFreeze()
								
							} catch {
								log.error("meta decryption error: \(String(describing: error))")
							}
						}
					}
				}
				
				if let payment = payment {
					items.append(DownloadedItem(
						record: record,
						unpaddedSize: unpaddedSize,
						payment: payment,
						metadata: metadata
					))
				}
				
			}
			
			operation.queryCompletionBlock = { (cursor: CKQueryOperation.Cursor?, error: Error?) in
				
				if let error = error {
					log.debug("downloadPayments(): performBatchFetch(): error: \(String(describing: error))")
					finish(.failure(error))
					
				} else {
					log.debug("downloadPayments(): performBatchFetch(): complete")
					DispatchQueue.main.async {
						updateDatabase(items, cursor, batch)
					}
				}
			}
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = true
			
			operation.configuration = configuration
			
			CKContainer.default().privateCloudDatabase.add(operation)
		
		} // </performBatchFetch>
		
		// Step 5 of 6:
		//
		// Save the downloaded results to the database.
		//
		updateDatabase = { (
			items: [DownloadedItem],
			cursor: CKQueryOperation.Cursor?,
			batch: Int
		) -> Void in
			log.trace("downloadPayments(): updateDatabase()")
			
			// Kotlin will crash if we try to use multiple threads (like a real app)
			assert(Thread.isMainThread, "Kotlin ahead: background threads unsupported")
			
			var paymentRows: [Lightning_kmpWalletPayment] = []
			var paymentMetadataRows: [WalletPaymentId: WalletPaymentMetadataRow] = [:]
			var metadataMap: [WalletPaymentId: CloudKitDb.MetadataRow] = [:]
			
			for item in items {
				
				paymentRows.append(item.payment)
				
				let paymentId = item.payment.walletPaymentId()
				paymentMetadataRows[paymentId] = item.metadata
				
				let creation = self.dateToMillis(item.record.creationDate ?? Date())
				let metadata = self.metadataForRecord(item.record)
				
				metadataMap[paymentId] = CloudKitDb.MetadataRow(
					unpaddedSize: Int64(item.unpaddedSize),
					recordCreation: creation,
					recordBlob: metadata.toKotlinByteArray()
				)
			}
			
			self.cloudKitDb.updateRows(
				downloadedPayments: paymentRows,
				downloadedPaymentsMetadata: paymentMetadataRows,
				updateMetadata: metadataMap
			) { (_: KotlinUnit?, error: Error?) in
		
				log.trace("downloadPayments(): updateDatabase(): completion")
		
				if let error = error {
					log.error("downloadPayments(): updateDatabase(): error: \(String(describing: error))")
					finish(.failure(error))
				} else {
					downloadProgress.completeInFlight(completed: items.count)
					
					if let cursor = cursor {
						log.debug("downloadPayments(): updateDatabase(): moreInCloud = true")
						performBatchFetch(CKQueryOperation(cursor: cursor), batch+1)
						
					} else {
						log.debug("downloadPayments(): updateDatabase(): moreInCloud = false")
						enqueueMissing()
					}
				}
			}
		} // </updateDatabase>
		
		enqueueMissing = { () -> Void in
			log.trace("downloadPayments(): enqueueMissing()")
			
			// Kotlin will crash if we try to use multiple threads (like a real app)
			assert(Thread.isMainThread, "Kotlin ahead: background threads unsupported")
			
			self.cloudKitDb.enqueueMissingItems { (_, error) in
				
				if let error = error {
					log.error("downloadPayments(): enqueueMissingItems(): error: \(String(describing: error))")
					finish(.failure(error))
				} else {
					finish(.success)
				}
			}
		}
		
		// Go!
		DispatchQueue.main.async {
			checkDatabase()
		}
	}
	
	/// The upload task performs the following tasks:
	/// - extract rows from the database that need to be uploaded
	/// - serialize & encrypt the data
	/// - upload items to the user's private cloud database
	/// - remove the uploaded items from the queue
	/// - repeat as needed
	///
	private func uploadPayments(_ uploadProgress: SyncManagerState_Progress) {
		log.trace("uploadPayments()")
		
		let finish = { (result: Result<Void, Error>) in
			
			switch result {
			case .success:
				log.trace("uploadPayments(): finish(): success")
				
				self.consecutiveErrorCount = 0
				self.updateState { state, deferToSimplifiedStateFlow in
					switch state.active {
						case .uploading:
							if state.paymentsQueueCount == 0 {
								state.active = .synced
							} else {
								deferToSimplifiedStateFlow = true
							}
						default:
							break
					}
				}
				
			case .failure(let error):
				log.trace("uploadPayments(): finish(): failure")
				self.handleError(error)
			}
		}
		
		var checkDatabase      : (() -> Void)!
		var prepareUpload      : ((UploadOperationInfo) -> Void)!
		var performUpload      : ((UploadOperationInfo, CKModifyRecordsOperation) -> Void)!
		var handlePartialError : ((UploadOperationInfo, CKError) -> Void)!
		var updateDatabase     : ((UploadOperationInfo) -> Void)!
		
		// Step 1 of 4:
		//
		// Check the `cloudkit_payments_queue` table, to see if there's anything we need to upload.
		// If the queue is non-empty, we will also receive:
		// - the corresponding payment information that needs to be uploaded
		// - the corresponding CKRecord metadata from previous upload for the payment (if present)
		//
		checkDatabase = { () -> Void in
			log.trace("uploadPayments(): checkDatabase()")
			
			// Kotlin will crash if we try to use multiple threads (like a real app)
			assert(Thread.isMainThread, "Kotlin ahead: background threads unsupported")
			
			self.cloudKitDb.fetchQueueBatch(limit: 20) {
				(result: CloudKitDb.FetchQueueBatchResult?, error: Error?) in
				
				if let error = error {
					log.error("uploadPayments(): checkDatabase(): error: \(String(describing: error))")
					finish(.failure(error))
					
				} else {
					let batch = result?.convertToSwift() ?? FetchQueueBatchResult.empty()
					log.trace("uploadPayments(): checkDatabase(): success: \(batch.rowids.count)")
					
					if batch.rowids.count == 0 {
						// There's nothing queued for upload, so we're done.
						finish(.success)
					} else {
						// Perform serialization & encryption on a background thread.
						DispatchQueue.global(qos: .utility).async {
							prepareUpload(UploadOperationInfo(batch: batch))
						}
					}
				}
			}
			
		} // </checkDatabase>
		
		
		// Step 2 of 4:
		//
		// Serialize and encrypt the payment information.
		// Then encapsulate the encrypted blob into a CKRecord.
		// And prepare a full CKModifyRecordsOperation for upload.
		//
		prepareUpload = { (opInfo: UploadOperationInfo) -> Void in
			log.trace("uploadPayments(): prepareUpload()")
			
			let batch = opInfo.batch
			
			log.debug("batch.rowids.count = \(batch.rowids.count)")
			log.debug("batch.rowidMap.count = \(batch.rowidMap.count)")
			log.debug("batch.rowMap.count = \(batch.rowMap.count)")
			log.debug("batch.metadataMap.count = \(batch.metadataMap.count)")
			
			var recordsToSave = [CKRecord]()
			var recordIDsToDelete = [CKRecord.ID]()
			
			var reverseMap = [CKRecord.ID: WalletPaymentId]()
			var unpaddedMap = [WalletPaymentId: Int]()
			
			// NB: batch.rowidMap may contain the same paymentRowId multiple times.
			// And if we include the same record multiple times in the CKModifyRecordsOperation,
			// then the operation will fail.
			//
			for paymentId in batch.uniquePaymentIds() {
				
				var existingRecord: CKRecord? = nil
				if let metadata = batch.metadataMap[paymentId] {
					
					let data = metadata.toSwiftData()
					existingRecord = self.recordFromMetadata(data)
				}
				
				if let row = batch.rowMap[paymentId] {
					
					if let (ciphertext, unpaddedSize) = self.serializeAndEncryptPayment(row.payment, batch) {
						
						let record = existingRecord ?? CKRecord(
							recordType: record_table_name,
							recordID: self.recordID(for: paymentId)
						)
						
						record[record_column_data] = ciphertext
						
						if let fileUrl = self.serializeAndEncryptMetadata(row.metadata) {
							record[record_column_meta] = CKAsset(fileURL: fileUrl)
						}
						
						recordsToSave.append(record)
						reverseMap[record.recordID] = paymentId
						unpaddedMap[paymentId] = unpaddedSize
					}
					
				} else {
					
					// The payment has been deleted from the local database.
					// So we're going to delete it from the cloud database (if it exists there).
					
					let recordID = existingRecord?.recordID ?? self.recordID(for: paymentId)
					
					recordIDsToDelete.append(recordID)
					reverseMap[recordID] = paymentId
				}
			}
			
			let operation = CKModifyRecordsOperation(
				recordsToSave: recordsToSave,
				recordIDsToDelete: recordIDsToDelete
			)
			
			var nextOpInfo = opInfo
			nextOpInfo.reverseMap = reverseMap
			nextOpInfo.unpaddedMap = unpaddedMap
			
			if recordsToSave.count == 0 && recordIDsToDelete.count == 0 {
			
				// CKModifyRecordsOperation will fail if given an empty task.
				// So we need to skip ahead.
				DispatchQueue.main.async {
					updateDatabase(nextOpInfo)
				}
				
			} else {
				performUpload(nextOpInfo, operation)
			}
		
		} // </prepareUpload>
		
		
		// Step 3 of 4:
		//
		// Perform the upload to the cloud.
		//
		performUpload = { (opInfo: UploadOperationInfo, operation: CKModifyRecordsOperation) -> Void in
			log.trace("uploadPayments(): performUpload()")
			
			log.debug("operation.recordsToSave.count = \(operation.recordsToSave?.count ?? 0)")
			log.debug("operation.recordIDsToDelete.count = \(operation.recordIDsToDelete?.count ?? 0)")
			
			operation.isAtomic = false
			operation.savePolicy = .ifServerRecordUnchanged
			
			let (parentProgress, childrenProgress) = self.createProgress(for: operation)
			
			operation.perRecordProgressBlock = { (record: CKRecord, percent: Double) -> Void in
				
				if let child = childrenProgress[record.recordID] {
					let completed = Double(child.totalUnitCount) * percent
					child.completedUnitCount = Int64(completed)
				}
			}
			
			var inFlightCount = 0
			inFlightCount += operation.recordsToSave?.count ?? 0
			inFlightCount += operation.recordIDsToDelete?.count ?? 0
			
			uploadProgress.setInFlight(count: inFlightCount, progress: parentProgress)
			
			operation.modifyRecordsCompletionBlock = {
				(savedRecords: [CKRecord]?, deletedRecordIds: [CKRecord.ID]?, error: Error?) -> Void in
				
				log.trace("operation.modifyRecordsCompletionBlock()")
				
				var partialFailure: CKError? = nil
				
				if let ckerror = error as? CKError,
				   ckerror.errorCode == CKError.partialFailure.rawValue
				{
					// If we receive a partial error, it's because we've already uploaded these items before.
					// We need to handle this case differently.
					partialFailure = ckerror
				}
				
				if let error = error, partialFailure == nil {
					log.error("Error modifying records: \(String(describing: error))")
					finish(.failure(error))
					
				} else {
					
					var nextOpInfo = opInfo
					nextOpInfo.savedRecords = savedRecords ?? []
					nextOpInfo.deletedRecordIds = deletedRecordIds ?? []
					
					if let partialFailure = partialFailure {
						// Not every payment in the batch was successful.
						// We may have to fetch metadata & redo some operations.
						handlePartialError(nextOpInfo, partialFailure)
						
					} else {
						// Every payment in the batch was successful.
						parentProgress.completedUnitCount = parentProgress.totalUnitCount
						for (_, paymentRowId) in opInfo.reverseMap {
							for rowid in opInfo.batch.rowidsMatching(paymentRowId) {
								nextOpInfo.completedRowids.append(rowid)
							}
						}
						DispatchQueue.main.async {
							updateDatabase(nextOpInfo)
						}
					}
				}
			}
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = Prefs.shared.backupTransactions_useCellular
			
			operation.configuration = configuration
			
			if uploadProgress.register(operation) {
				CKContainer.default().privateCloudDatabase.add(operation)
			} else {
				finish(.failure(CKError(.operationCancelled)))
			}
		
		} // </performUpload>
		
		// Step 3.B of 4:
		//
		// If the upload encounters a partial error,
		// we have special handling we need to perform.
		//
		handlePartialError = { (opInfo: UploadOperationInfo, ckerror: CKError) -> Void in
			log.trace("uploadPayments(): handlePartialError()")
			
			var nextOpInfo = opInfo
			var recordIDsToFetch: [CKRecord.ID] = []
			
			if let map = ckerror.partialErrorsByItemID {
				
				// Defined as:
				// - map: [AnyHashable: Error]
				//
				// Expected types:
				// - key: CKRecord.ID
				// - value: CKError
				
				for (key, value) in map {
					
					if let recordID = key as? CKRecord.ID,
					   let paymentRowId = opInfo.reverseMap[recordID]
					{
						// Remove corresponding rowid(s) from list of completed
						for rowid in opInfo.batch.rowidsMatching(paymentRowId) {
							nextOpInfo.completedRowids.removeAll(where: { $0 == rowid })
						}
						
						// Add to list of failed
						nextOpInfo.partialFailures[paymentRowId] = value as? CKError
						
						// If this is a standard your-changetag-was-out-of-date message from the server,
						// then we just need to fetch the latest CKRecord metadata from the cloud,
						// and then re-try our upload.
						if let recordError = value as? CKError,
							recordError.errorCode == CKError.serverRecordChanged.rawValue
						{
							recordIDsToFetch.append(recordID)
						}
					}
				}
			}
			
			if recordIDsToFetch.count == 0 {
				log.debug("uploadPayments(): handlePartialError(): No outdated records")
				
				return DispatchQueue.main.async {
					updateDatabase(nextOpInfo)
				}
			}
			
			let operation = CKFetchRecordsOperation(recordIDs: recordIDsToFetch)
			
			operation.fetchRecordsCompletionBlock = { (results: [CKRecord.ID : CKRecord]?, error: Error?) in
				
				log.trace("operation.fetchRecordsCompletionBlock()")
				
				if let error = error {
					log.debug("Error fetching records: \(String(describing: error))")
					finish(.failure(error))
					
				} else {
					log.debug("results.count = \(results?.count ?? 0)")
					
					for (_, record) in (results ?? [:]) {
						
						// We successfully fetched the latest CKRecord from the server.
						// We add to nextOpInfo.savedRecords, which will write the CKRecord to the database.
						// So on the next upload attempt, we should have the latest version.
						//
						nextOpInfo.savedRecords.append(record)
					}
					
					DispatchQueue.main.async {
						updateDatabase(nextOpInfo)
					}
				}
			}
			
			operation.desiredKeys = [] // fetch only basic CKRecord metadata
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = true
			
			operation.configuration = configuration
			
			if uploadProgress.register(operation) {
				CKContainer.default().privateCloudDatabase.add(operation)
			} else {
				finish(.failure(CKError(.operationCancelled)))
			}
		
		} // </handlePartialError>
		
		
		// Step 4 of 4:
		//
		// Process the upload results.
		updateDatabase = { (opInfo: UploadOperationInfo) -> Void in
			log.trace("uploadPayments(): updateDatabase()")
			
			// Kotlin will crash if we try to use multiple threads (like a real app)
			assert(Thread.isMainThread, "Kotlin ahead: background threads unsupported")
			
			var deleteFromQueue = [KotlinLong]()
			var deleteFromMetadata = [WalletPaymentId]()
			var updateMetadata = [WalletPaymentId: CloudKitDb.MetadataRow]()
			
			for (rowid) in opInfo.completedRowids {
				deleteFromQueue.append(KotlinLong(longLong: rowid))
			}
			for recordId in opInfo.deletedRecordIds {
				if let paymentRowId = opInfo.reverseMap[recordId] {
					deleteFromMetadata.append(paymentRowId)
				}
			}
			for record in opInfo.savedRecords {
				if let paymentRowId = opInfo.reverseMap[record.recordID] {
					
					let unpaddedSize = opInfo.unpaddedMap[paymentRowId] ?? 0
					let creation = self.dateToMillis(record.creationDate ?? Date())
					let metadata = self.metadataForRecord(record)
					
					updateMetadata[paymentRowId] = CloudKitDb.MetadataRow(
						unpaddedSize: Int64(unpaddedSize),
						recordCreation: creation,
						recordBlob:  metadata.toKotlinByteArray()
					)
				}
			}
			
			// Handle partial failures
			for paymentRowId in self.updateConsecutivePartialFailures(opInfo.partialFailures) {
				for rowid in opInfo.batch.rowidsMatching(paymentRowId) {
					deleteFromQueue.append(KotlinLong(longLong: rowid))
				}
			}
			
			log.debug("deleteFromQueue.count = \(deleteFromQueue.count)")
			log.debug("deleteFromMetadata.count = \(deleteFromMetadata.count)")
			log.debug("updateMetadata.count = \(updateMetadata.count)")
			log.debug("updateMetadata: \(updateMetadata)")
			
			self.cloudKitDb.updateRows(
				deleteFromQueue: deleteFromQueue,
				deleteFromMetadata: deleteFromMetadata,
				updateMetadata: updateMetadata
			) { (_: KotlinUnit?, error: Error?) in
				
				log.trace("cloudKitDb.updateRows().completion()")
				
				if let error = error {
					log.error("cloudKitDb.updateRows(): error: \(String(describing: error))")
					finish(.failure(error))
				} else {
					uploadProgress.completeInFlight(completed: deleteFromQueue.count)
					
					// Check to see if there are more items to upload.
					// Perhaps items were added to the database while we were uploading.
					checkDatabase()
				}
			}
			
		} // </updateDatabase>
		
		
		// Go!
		DispatchQueue.main.async {
			checkDatabase()
		}
	}
	
	// ----------------------------------------
	// MARK: Debugging
	// ----------------------------------------
	#if DEBUG
	
	private func listAllItems() -> Void {
		log.trace("listAllItems()")
		
		let query = CKQuery(
			recordType: record_table_name,
			predicate: NSPredicate(format: "TRUEPREDICATE")
		)
		query.sortDescriptors = [
			NSSortDescriptor(key: "creationDate", ascending: false)
		]
		
		let operation = CKQueryOperation(query: query)
		operation.zoneID = recordZoneID()
		
		recursiveListBatch(operation: operation)
	}
	
	private func recursiveListBatch(operation: CKQueryOperation) -> Void {
		log.trace("recursiveListBatch()")
		
		operation.recordFetchedBlock = { (record: CKRecord) in
			
			log.debug("Received record:")
			log.debug(" - recordID: \(record.recordID)")
			log.debug(" - creationDate: \(record.creationDate ?? Date.distantPast)")
			
			if let data = record[record_column_data] as? Data {
				log.debug(" - data.count: \(data.count)")
			} else {
				log.debug(" - data: ?")
			}
		}
		
		operation.queryCompletionBlock = { (cursor: CKQueryOperation.Cursor?, error: Error?) in
			
			if let error = error {
				log.debug("Error fetching batch: \(String(describing: error))")
				
			} else if let cursor = cursor {
				log.debug("Fetch batch complete. Continuing with cursor...")
				self.recursiveListBatch(operation: CKQueryOperation(cursor: cursor))
				
			} else {
				log.debug("Fetch batch complete.")
			}
		}
		
		let configuration = CKOperation.Configuration()
		configuration.allowsCellularAccess = true
		
		operation.configuration = configuration
		
		CKContainer.default().privateCloudDatabase.add(operation)
	}
	
	#endif
	// ----------------------------------------
	// MARK: Utilities
	// ----------------------------------------
	
	/// Performs all of the following:
	/// - serializes incoming/outgoing payment into JSON
	/// - adds randomized padding to obfuscate payment type
	/// - encrypts the blob using the cloudKey
	///
	private func serializeAndEncryptPayment(
		_ row: Lightning_kmpWalletPayment,
		_ batch: FetchQueueBatchResult
	) -> (Data, Int)? {
		
		var wrapper: CloudData? = nil
		
		if let incoming = row as? Lightning_kmpIncomingPayment {
			wrapper = CloudData(incoming: incoming, version: CloudDataVersion.v0)
			
		} else if let outgoing = row as? Lightning_kmpOutgoingPayment {
			wrapper = CloudData(outgoing: outgoing, version: CloudDataVersion.v0)
		}
		
		var cleartext: Data? = nil
		var unpaddedSize: Int = 0
		
		if let wrapper = wrapper {
			
			let cbor = wrapper.cborSerialize().toSwiftData()
			cleartext = cbor
			unpaddedSize = cbor.count
			
			#if DEBUG
		//	let jsonData = wrapper.jsonSerialize().toSwiftData()
		//	let jsonStr = String(data: jsonData, encoding: .utf8)
		//	log.debug("Uploading record (JSON representation):\n\(jsonStr ?? "<nil>")")
			#endif
			
			// We want to add padding to obfuscate the payment type. That is,
			// it should not be possible to determine the type of payment (incoming vs outgoing),
			// simply by inspecting the size of the encrypted blob.
			//
			// Generally speaking, an IncomingPayment is smaller than an OutgoingPayment.
			// But this depends on many factors, including the size of the PaymentRequest.
			// And this may change over time, as the Phoenix codebase evolves.
			//
			// So our technique is dynamic and adaptive:
			// For every payment stored in the cloud, we track (in the local database)
			// the raw/unpadded size of the serialized payment.
			//
			// This allows us to generate the {mean, standardDeviation} for each payment type.
			// Using this information, we generate a target range for the size of the encrypted blob.
			// And then we and a random amount of padding the reach our target range.
			
			let makeRange = { (stats: CloudKitDb.MetadataStats) -> (Int, Int) in
				
				var rangeMin: Int = 0
				var rangeMax: Int = 0
				
				if stats.mean > 0 {
					
					let deviation = stats.standardDeviation * 1.5
					
					let minRange = stats.mean - deviation
					let maxRange = stats.mean + deviation
					
					if minRange > 0 {
						rangeMin = Int(minRange.rounded(.toNearestOrAwayFromZero))
						rangeMax = Int(maxRange.rounded(.up))
					}
				}
				
				return (rangeMin, rangeMax)
			}
			
			let (rangeMin_incoming, rangeMax_incoming) = makeRange(batch.incomingStats)
			let (rangeMin_outgoing, rangeMax_outgoing) = makeRange(batch.outgoingStats)
			
			var rangeMin = 0
			var rangeMax = 0
			
			if rangeMax_outgoing > rangeMax_incoming {
				rangeMin = rangeMin_outgoing
				rangeMax = rangeMax_outgoing
				
			} else if rangeMax_incoming > rangeMax_outgoing {
				rangeMin = rangeMin_incoming
				rangeMax = rangeMax_incoming
			}
			
			var padSize = 0
			if rangeMin > 0, rangeMax > 0, rangeMin < rangeMax, unpaddedSize < rangeMin {
				
				padSize = (rangeMin - unpaddedSize)
				padSize += Int.random(in: 0 ..< (rangeMax - rangeMin))
					
			} else {
				// Add a smaller amount of padding
				padSize += Int.random(in: 0 ..< 256)
			}
			
			let padding: Data
			if padSize > 0 {
				padding = genRandomBytes(padSize)
			} else {
				padding = Data()
			}
			
			let padded = wrapper.doCopyWithPadding(padding: padding.toKotlinByteArray())
			
			let paddedCbor = padded.cborSerialize().toSwiftData()
			cleartext = paddedCbor
			
			log.debug("unpadded=\(unpaddedSize), padded=\(cleartext!.count)")
		}
		
		var ciphertext: Data? = nil
		
		if let cleartext = cleartext {
			
			do {
				let box = try ChaChaPoly.seal(cleartext, using: self.cloudKey)
				ciphertext = box.combined
				
			} catch {
				log.error("Error encrypting row with ChaChaPoly: \(String(describing: error))")
			}
		}
		
		if let ciphertext = ciphertext {
			return (ciphertext, unpaddedSize)
		} else {
			return nil
		}
	}
	
	private func serializeAndEncryptMetadata(
		_ metadata: WalletPaymentMetadata
	) -> URL? {
		
		guard let lnurlPay = metadata.lnurl?.pay else {
			return nil
		}
		
		let cleartext = WalletPaymentMetadataRow.companion.serialize(
			pay: lnurlPay,
			successAction: metadata.lnurl?.successAction
		)
		.cloudSerialize()
		.toSwiftData()
		
		let ciphertext: Data
		do {
			let box = try ChaChaPoly.seal(cleartext, using: self.cloudKey)
			ciphertext = box.combined
			
		} catch {
			log.error("Error encrypting assets with ChaChaPoly: \(String(describing: error))")
			return nil
		}
		
		let tempDir = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
		let fileName = UUID().uuidString
		let filePath = tempDir.appendingPathComponent(fileName, isDirectory: false)
		
		do {
			try ciphertext.write(to: filePath)
		} catch {
			log.error("Error encrypting assets with ChaChaPoly: \(String(describing: error))")
			return nil
		}
		
		return filePath
	}
	
	func createProgress(for operation: CKModifyRecordsOperation) -> (Progress, [CKRecord.ID: Progress]) {
		
		// A CKModifyRecordsOperation consists of uploading:
		// - zero or more CKRecords (with a serialized & encrypted payment)
		// - zero or more CKRecord.ID's
		//
		// From the perspective of monitoring the upload progress,
		// we only have visibility concerning the upload of the CKRecords.
		// So that will be the basis of our progress.
		
		var totalUnitCount: Int64 = 0
		var children: [CKRecord.ID: Progress] = [:]
		
		for record in (operation.recordsToSave ?? []) {
			
			if let blob = record[record_column_data] as? Data {
				
				let numBytes = Int64(blob.count)
				
				children[record.recordID] = Progress(totalUnitCount: numBytes)
				totalUnitCount += numBytes
			}
		}
		
		if totalUnitCount == 0 {
			totalUnitCount = 1
		}
		
		let parentProgress = Progress(totalUnitCount: totalUnitCount)
		for (_, child) in children {
			
			parentProgress.addChild(child, withPendingUnitCount: child.totalUnitCount)
		}
		
		return (parentProgress, children)
	}
	
	func genRandomBytes(_ count: Int) -> Data {

		var data = Data(count: count)
		let _ = data.withUnsafeMutableBytes { (ptr: UnsafeMutableRawBufferPointer) in
			SecRandomCopyBytes(kSecRandomDefault, count, ptr.baseAddress!)
		}
		return data
	}
	
	/// Incorporates failures from the last CKModifyRecordsOperation,
	/// and returns a list of permanently failed items.
	///
	func updateConsecutivePartialFailures(_ partialFailures: [WalletPaymentId: CKError?]) -> [WalletPaymentId] {
		
		// Handle partial failures.
		// The rules are:
		// - if an operation fails 2 times in a row with the same error, then we drop the operation
		// - unless the failure was serverChangeError,
		//   which must fail 3 times in a row before being dropped
		
		var permanentFailures: [WalletPaymentId] = []
		
		for (paymentId, ckerror) in partialFailures {
			
			guard var cpf = consecutivePartialFailures[paymentId] else {
				consecutivePartialFailures[paymentId] = ConsecutivePartialFailure(
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
						Permanent failure: \(paymentId), count=\(cpf.count): \
						\( ckerror == nil ? "<nil>" : String(describing: ckerror!) )
						"""
					)
					
					permanentFailures.append(paymentId)
					self.consecutivePartialFailures[paymentId] = nil
				} else {
					self.consecutivePartialFailures[paymentId] = cpf
				}
				
			} else {
				self.consecutivePartialFailures[paymentId] = ConsecutivePartialFailure(
					count: 1,
					error: ckerror
				)
			}
		}
		
		return permanentFailures
	}
	
	private func recordZoneID() -> CKRecordZone.ID {
		
		return CKRecordZone.ID(
			zoneName: self.encryptedNodeId,
			ownerName: CKCurrentUserDefaultName
		)
	}
	
	private func recordID(for paymentId: WalletPaymentId) -> CKRecord.ID {
		
		// The recordID is:
		// - deterministic => by hashing the paymentId
		// - secure => by mixing in the secret cloudKey (derived from seed)
		
		let prefix = SHA256.hash(data: cloudKey.rawRepresentation)
		let suffix = paymentId.dbId.data(using: .utf8)!
		
		let hashMe = prefix + suffix
		let digest = SHA256.hash(data: hashMe)
		let hash = digest.map { String(format: "%02hhx", $0) }.joined()
		
		return CKRecord.ID(recordName: hash, zoneID: recordZoneID())
	}
	
	private func metadataForRecord(_ record: CKRecord) -> Data {
		
		// Source: CloudKit Tips and Tricks - WWDC 2015
		
		let archiver = NSKeyedArchiver(requiringSecureCoding: true)
		record.encodeSystemFields(with: archiver)
		
		return archiver.encodedData
	}
	
	private func recordFromMetadata(_ data: Data) -> CKRecord? {
		
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
	
	private func dateToMillis(_ date: Date) -> Int64 {
		
		return Int64(date.timeIntervalSince1970 * 1_000)
	}
	
	private func millisToDate(_ millis: KotlinLong?) -> Date? {
		
		if let millis = millis {
			let seconds: TimeInterval = millis.doubleValue / Double(1_000)
			return Date(timeIntervalSince1970: seconds)
		} else {
			return nil
		}
	}
	
	// ----------------------------------------
	// MARK: Errors
	// ----------------------------------------
	
	/// Standardized error handling routine for various async operations.
	///
	private func handleError(_ error: Error) {
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
			
				case CKError.userDeletedZone.rawValue: fallthrough
				case CKError.zoneNotFound.rawValue:
					isZoneNotFound = true
				
				default: break
			}
			
			// Sometimes a `notAuthenticated` error is hidden in a partial error.
			if let partialErrorsByZone = ckerror.partialErrorsByItemID {
				
				for (_, perZoneError) in partialErrorsByZone {
					if (perZoneError as NSError).code == CKError.notAuthenticated.rawValue {
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
		
		var delay = 0.0
		if useExponentialBackoff {
			self.consecutiveErrorCount += 1
			delay = self.exponentialBackoff()
		
			if let minDelay = minDelay {
				if delay < minDelay {
					delay = minDelay
				}
			}
		}
		
		updateState { state, deferToSimplifiedStateFlow in
			
			if isNotAuthenticated {
				state.waitingForCloudCredentials = true
			}
			if isZoneNotFound {
				state.needsCreateRecordZone = true
			}
			
			switch state.active {
				case .updatingCloud: fallthrough
				case .downloading: fallthrough
				case .uploading:
					
					if useExponentialBackoff {
						state.active = .waiting_exponentialBackoff(self, delay: delay, error: error)
					} else {
						deferToSimplifiedStateFlow = true
					}
					
				default:
					break
			}
		} // </updateState>
		
		if isNotAuthenticated {
			DispatchQueue.main.async {
				self.checkForCloudCredentials()
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
}
