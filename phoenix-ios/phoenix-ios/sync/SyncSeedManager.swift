import Foundation
import PhoenixShared
import CloudKit
import Combine
import Network
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SyncSeedManager"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate let record_column_mnemonics = "mnemonics"
fileprivate let record_column_language = "language"
fileprivate let record_column_name = "name"

struct SeedBackup {
	let recordID: CKRecord.ID
	let mnemonics: String
	let language: String
	let name: String?
	let created: Date
}

enum FetchSeedsError: Error {
	case cloudKit(underlying: CKError)
	case unknown(underlying: Error)
}

// --------------------------------------------------------------------------------
// MARK: -
// --------------------------------------------------------------------------------

/// Encompasses the logic for syncing seeds with Apple's CloudKit database.
///
class SyncSeedManager: SyncManagerProtcol {
	
	/// Access to parent for shared logic.
	///
	weak var parent: SyncManager? = nil
	
	/// The chain in use by PhoenixBusiness (e.g. Testnet)
	///
	private let chain: Lightning_kmpNodeParams.Chain
	
	/// The 12-word seed phrase for the wallet.
	///
	private let mnemonics: String
	
	/// The encryptedNodeId is created via: Hash(cloudKey + nodeID)
	///
	/// All data from a user's wallet are stored in the user's CKContainer.default().privateCloudDatabase.
	/// And within the privateCloudDatabase, we create a dedicated CKRecordZone for each wallet,
	/// where recordZone.name == encryptedNodeId. All trasactions for the wallet are stored in this recordZone.
	///
	/// For simplicity, the name of the uploaded Seed shared the encryptedNodeId name.
	///
	private let encryptedNodeId: String
	
	/// Informs the user interface regarding the activities of the SyncSeedManager.
	/// This includes various errors & active upload progress.
	///
	/// Changes to this publisher will always occur on the main thread.
	///
	public let statePublisher: CurrentValueSubject<SyncSeedManager_State, Never>
	
	/// Implements the state machine in a thread-safe actor.
	///
	private let actor: SyncSeedManager_Actor
	
	private var consecutiveErrorCount = 0
	
	private var cancellables = Set<AnyCancellable>()
	
	init(chain: Lightning_kmpNodeParams.Chain, mnemonics: [String], encryptedNodeId: String) {
		log.trace("init()")
		
		self.chain = chain
		self.mnemonics = mnemonics.joined(separator: " ")
		self.encryptedNodeId = encryptedNodeId
		
		actor = SyncSeedManager_Actor(
			isEnabled: Prefs.shared.backupSeed.isEnabled,
			hasUploadedSeed: Prefs.shared.backupSeed.hasUploadedSeed(encryptedNodeId: encryptedNodeId)
		)
		statePublisher = CurrentValueSubject<SyncSeedManager_State, Never>(actor.initialState)
		
		startPreferencesMonitor()
		startNameMonitor()
	}
	
	// ----------------------------------------
	// MARK: Fetch Seeds
	// ----------------------------------------
	
	public class func fetchSeeds(
		chain: Lightning_kmpNodeParams.Chain
	) -> PassthroughSubject<SeedBackup, FetchSeedsError> {
		
		let publisher = PassthroughSubject<SeedBackup, FetchSeedsError>()
		
		Task {
			log.trace("fetchSeeds(): starting task")
			
			let container = CKContainer.default()
			let zoneID = CKRecordZone.default().zoneID
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = true
			
			do {
				try await container.privateCloudDatabase.configuredWith(configuration: configuration) { database in
					
					log.trace("fetchSeeds(): configured")
					
					let query = CKQuery(
						recordType: record_table_name(chain: chain),
						predicate: NSPredicate(format: "TRUEPREDICATE")
					)
					query.sortDescriptors = [
						NSSortDescriptor(key: "creationDate", ascending: false)
					]
					
					var done = false
					var cursor: CKQueryOperation.Cursor? = nil
					
					while !done {
						
						log.trace("fetchSeeds(): sending query...")
						
						let results: [(CKRecord.ID, Result<CKRecord, Error>)]
						if let prvCursor = cursor {
							(results, cursor) = try await database.records(continuingMatchFrom: prvCursor)
						} else {
							(results, cursor) = try await database.records(matching: query, inZoneWith: zoneID)
						}
						
						for (recordID, result) in results {
							
							if case .success(let record) = result {
								
								if let mnemonics = record[record_column_mnemonics] as? String,
									let language = record[record_column_language] as? String,
									let name = record[record_column_name] as? String?
								{
									let item = SeedBackup(
										recordID: recordID,
										mnemonics: mnemonics,
										language: language,
										name: name,
										created: record.creationDate ?? Date.distantPast
									)
									
									publisher.send(item)
								}
							}
						}
						
						if cursor == nil {
							log.debug("fetchSeeds(): queryResultBlock(): moreInCloud = false")
							publisher.send(completion: .finished)
							done = true
							
						} else {
							log.debug("fetchSeeds(): queryResultBlock(): moreInCloud = true")
						}
						
					} // </while !done>
					
				} // </configuredWith>
			} catch {
				
				if let ckerror = error as? CKError {
					log.debug("fetchSeeds(): ckerror = \(String(describing: ckerror))")
					publisher.send(completion: .failure(.cloudKit(underlying: ckerror)))
				} else {
					log.debug("fetchSeeds(): error = \(String(describing: error))")
					publisher.send(completion: .failure(.unknown(underlying: error)))
				}
			}
			
		} // </Task>
		
		return publisher
	}
	
	// ----------------------------------------
	// MARK: Monitors
	// ----------------------------------------
	
	private func startPreferencesMonitor() {
		log.trace("startPreferencesMonitor()")
		
		var isFirstFire = true
		Prefs.shared.backupSeed.isEnabled_publisher.sink {[weak self](shouldEnable: Bool) in
			
			if isFirstFire {
				isFirstFire = false
				return
			}
			guard let self = self else {
				return
			}
			
			log.debug("Prefs.shared.backupSeed_isEnabled_publisher = \(shouldEnable ? "true" : "false")")
			Task {
				if let newState = await self.actor.didChangeIsEnabled(shouldEnable) {
					self.handleNewState(newState)
				}
			}
			
		}.store(in: &cancellables)
	}
	
	private func startNameMonitor() {
		log.trace("startNameMonitor()")
		
		Prefs.shared.backupSeed.name_publisher.sink {[weak self] _ in
			
			guard let self = self else {
				return
			}
			
			log.debug("Prefs.shared.backupSeed_name_publisher => fired")
			Task {
				if let newState = await self.actor.didChangeName() {
					self.handleNewState(newState)
				}
			}
			
		}.store(in: &cancellables)
	}
	
	// ----------------------------------------
	// MARK: Publishers
	// ----------------------------------------
	
	private func publishNewState(_ state: SyncSeedManager_State) {
		log.trace("publishNewState()")
		
		let block = {
			log.debug("statePublisher.value = \(state)")
			self.statePublisher.value = state
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
	
	/// Called from `SyncSeedManager_State_Waiting`
	/// 
	func finishWaiting(_ sender: SyncSeedManager_State_Waiting) {
		log.trace("finishWaiting()")
		
		Task {
			if let newState = await self.actor.finishWaiting(sender) {
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
	
	private func handleNewState(_ newState: SyncSeedManager_State) {
		
		log.debug("state = \(newState)")
		switch newState {
			case .uploading:
				uploadSeed()
			case .deleting:
				deleteSeed()
			default:
				break
		}
		
		publishNewState(newState)
	}
	
	private func uploadSeed() {
		log.trace("uploadSeed()")
		
		let uploadedName = Prefs.shared.backupSeed.name(encryptedNodeId: encryptedNodeId) ?? ""
		Task {
			log.trace("uploadSeed(): starting task")
			
			let container = CKContainer.default()
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = true
			
			do {
				try await container.privateCloudDatabase.configuredWith(configuration: configuration) { database in
					
					log.trace("uploadSeed(): configured")
					
					let record = CKRecord(
						recordType: SyncSeedManager.record_table_name(chain: chain),
						recordID: recordID()
					)
					
					record[record_column_mnemonics] = mnemonics
					record[record_column_language] = "en"
					record[record_column_name] = uploadedName
					
					let started = Date.now
					let (saveResults, _) = try await database.modifyRecords(
						saving: [record],
						deleting: [],
						savePolicy: .changedKeys
					)
					
					let result = saveResults[record.recordID]!
					
					if case let .failure(error) = result {
						log.trace("uploadSeed(): perRecordResult: failure")
						throw error
					}
					
					log.trace("uploadSeed(): perRecordResult: success")
					
					// UI optimization:
					// When the user enables seed-backup, they watch as the seed is uploaded.
					// That is, the UI displays the progress to them with a little spinner.
					//
					// Now, when the process takes a few seconds, the user experience is pleasant:
					// - the user sees the message "uploading to the cloud"
					// - there's a little spinner animation
					// - a few seconds later, the process finishes
					// - and the UI says "your seed is stored in the cloud"
					//
					// Then end result is higher confidence in the user.
					//
					// However, if the process is too quick, the user experience is different:
					// - the UI flickers in an unreadable way
					// - then the UI says "your seed is stored in the cloud"
					//
					// The user doesn't know what the flickering UI was for.
					// And they have to trust that their seed is, indeed, stored in the cloud.
					//
					// For this reason we're going to introduce a "readability" delay.
					
					let elapsed = abs(Date.now.timeIntervalSince(started))
					let minElapsed: TimeInterval = 3.0 // seconds
					
					if elapsed < minElapsed { // seconds
						
						let delay: TimeInterval = minElapsed - elapsed
						log.trace("uploadSeed(): readabilityDelay = \(delay) seconds")
						
						try await Task.sleep(seconds: delay)
					}
					
					// Since this is an async process, the user may have changed the seed name again
					// while we were uploading the original name. So we need to check for that.
					
					let currentName = Prefs.shared.backupSeed.name(encryptedNodeId: self.encryptedNodeId) ?? ""
					let needsReUpload = currentName != uploadedName
					
					if needsReUpload {
						log.debug("uploadSeed(): finished: needsReUpload")
					} else {
						log.trace("uploadSeed(): finished: success")
						Prefs.shared.backupSeed.setHasUploadedSeed(true, encryptedNodeId: self.encryptedNodeId)
					}
					self.consecutiveErrorCount = 0
					
					Task {
						if let newState = await self.actor.didUploadSeed(needsReUpload: needsReUpload) {
							self.handleNewState(newState)
						}
					}
					
				} // </configuredWith>
				
			} catch {
				
				log.debug("uploadSeed(): error = \(String(describing: error))")
				self.handleError(error)
			}
		} // </Task>
	}
	
	private func deleteSeed() {
		log.trace("deleteSeed()")
		
		Task {
			log.trace("deleteSeed(): starting task")
			
			let container = CKContainer.default()
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = true
			
			do {
				try await container.privateCloudDatabase.configuredWith(configuration: configuration) { database in
					
					log.trace("deleteSeed(): configured")
					
					let recordID = recordID()
					
					let started = Date.now
					let (_, deleteResults) = try await database.modifyRecords(
						saving: [],
						deleting: [recordID],
						savePolicy: .changedKeys
					)
					
					// deleteResults: [CKRecord.ID : Result<Void, Error>]
					
					let result = deleteResults[recordID]!
					
					if case let .failure(error) = result {
						log.trace("deleteSeed(): perRecordResult: failure")
						throw error
					}
					
					log.trace("deleteSeed(): perRecordResult: success")
					
					// UI optimization:
					// When the user disables seed-backup, they watch as the seed is deleted.
					// That is, the UI displays the progress to them with a little spinner.
					//
					// Now, when the process takes a few seconds, the user experience is pleasant:
					// - the user sees the message "deleting from the cloud"
					// - there's a little spinner animation
					// - a few seconds later, the process finishes
					// - and the UI says "you are responsible for backing up your seed"
					//
					// Then end result is higher confidence in the user.
					// The user knows the seed was deleted from the cloud.
					//
					// However, if the process is too quick, the user experience is different:
					// - the UI flickers in an unreadable way
					// - then the UI says "you are responsible for backing up your seed"
					//
					// The user doesn't know what the flickering UI was for.
					// And they have to trust that their seed was, indeed, deleted from the cloud.
					//
					// For this reason we're going to introduce a "readability" delay.
					
					let elapsed = abs(Date.now.timeIntervalSince(started))
					let minElapsed: TimeInterval = 3.0 // seconds
					
					if elapsed < minElapsed { // seconds
						
						let delay: TimeInterval = minElapsed - elapsed
						log.trace("deleteSeed(): readabilityDelay = \(delay) seconds")
						
						try await Task.sleep(seconds: delay)
					}
					
					log.trace("deleteSeed(): finish: success")
					
					Prefs.shared.backupSeed.setHasUploadedSeed(false, encryptedNodeId: self.encryptedNodeId)
					self.consecutiveErrorCount = 0
					
					Task {
						if let newState = await self.actor.didDeleteSeed() {
							self.handleNewState(newState)
						}
					}
					
				} // </configuredWith>
				
			} catch {
				
				log.debug("deletedSeed(): error = \(String(describing: error))")
				self.handleError(error)
			}
			
		} // </Task>
	}
	
	// ----------------------------------------
	// MARK: Utilities
	// ----------------------------------------
	
	private class func record_table_name(chain: Lightning_kmpNodeParams.Chain) -> String {
		
		// From Apple's docs:
		// > A record type must consist of one or more alphanumeric characters
		// > and must start with a letter. CloudKit permits the use of underscores,
		// > but not spaces.
		//
		var allowed = CharacterSet.alphanumerics
		allowed.insert("_")
		
		let suffix = chain.name.lowercased().components(separatedBy: allowed.inverted).joined(separator: "")
		
		// E.g.:
		// - seeds_bitcoin_testnet
		// - seeds_bitcoin_mainnet
		return "seeds_bitcoin_\(suffix)"
	}
	
	private func recordID() -> CKRecord.ID {
		
		return CKRecord.ID(
			recordName: encryptedNodeId,
			zoneID: CKRecordZone.default().zoneID
		)
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
		var minDelay: Double? = nil
		
		if let ckerror = error as? CKError {
			
			switch ckerror.errorCode {
				case CKError.operationCancelled.rawValue:
					isOperationCancelled = true
				
				case CKError.notAuthenticated.rawValue:
					isNotAuthenticated = true
				
				case CKError.accountTemporarilyUnavailable.rawValue:
					isNotAuthenticated = true
					
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
		if isOperationCancelled || isNotAuthenticated {
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
		
		let wait: SyncSeedManager_State_Waiting?
		if useExponentialBackoff {
			self.consecutiveErrorCount += 1
			var delay = self.exponentialBackoff()
			if let minDelay = minDelay {
				if delay < minDelay {
					delay = minDelay
				}
			}
			wait = SyncSeedManager_State_Waiting(
				kind: .exponentialBackoff(error),
				parent: self,
				delay: delay
			)
		} else {
			wait = nil
		}
		
		Task { [isNotAuthenticated] in
			if let newState = await self.actor.handleError(
				isNotAuthenticated: isNotAuthenticated,
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
		
		// Keep in mind:
		// When the user enables/disables seed-backup,
		// they will be looking at a UI that shows the sync status.
		// So we're NOT using small millisecond values for the first failures,
		// as it makes the UI update too fast for readability.
		switch consecutiveErrorCount {
			case  1 : return 5.seconds()
			case  2 : return 10.seconds()
			case  3 : return 30.seconds()
			case  4 : return 60.seconds()
			case  5 : return 120.seconds()
			case  6 : return 300.seconds()
			default : return 600.seconds()
		}
	}
}
