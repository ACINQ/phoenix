import Foundation
import CloudKit
import CryptoKit
import PhoenixShared

fileprivate let filename = "SyncBackupManager+Cards"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate let cards_record_table_name = "cards"
fileprivate let cards_record_column_data = "encryptedData"

fileprivate struct DownloadedCard {
	let record: CKRecord
	let card: BoltCardInfo
}

fileprivate struct UploadCardsOperationInfo {
	let batch: FetchCardsQueueBatchResult
	
	let recordsToSave: [CKRecord]
	let recordIDsToDelete: [CKRecord.ID]
	
	let reverseMap: [CKRecord.ID: Lightning_kmpUUID]
	
	var completedRowids: [Int64] = []
	
	var partialFailures: [Lightning_kmpUUID: CKError?] = [:]
	
	var savedRecords: [CKRecord] = []
	var deletedRecordIds: [CKRecord.ID] = []
}

extension SyncBackupManager {
	
	func startCardsQueueCountMonitor() {
		log.trace(#function)
		
		let queueCountSequnece = cloudKitDb.cards.queueCountSequence()
		Task { @MainActor [weak self] in
			for await count in queueCountSequnece {
				self?.queueCountChanged(count)
			}
		}.store(in: &cancellables)
	}
	
	// ----------------------------------------
	// MARK: Notifications
	// ----------------------------------------
	
	private func queueCountChanged(_ queueCount: Int64) {
		log.trace("cards.queueCountChanged(): count = \(queueCount)")
		
		let count = Int(clamping: queueCount)
		Task {
			if let newState = await self.actor.cardsQueueCountChanged(count, wait: nil) {
				self.handleNewState(newState)
			}
		}
	}
	
	// ----------------------------------------
	// MARK: IO
	// ----------------------------------------
	
	func downloadCards(_ downloadProgress: SyncBackupManager_State_Downloading) {
		log.trace("downloadCards()")
		
		Task {
			
			// Step 1 of 4:
			//
			// We are downloading items from newest to oldest.
			// So first we fetch the oldest item date in the table (if there is one)
			
			let millis: KotlinLong? = try await Task { @MainActor in
				return try await self.cloudKitDb.cards.fetchOldestCreation()
			}.value
			
			let oldestCreationDate = millis?.int64Value.toDate(from: .milliseconds)
			downloadProgress.setCards_oldestCompletedDownload(oldestCreationDate)
			
			/**
			 * NOTE:
			 * If we want to report proper progress (via `SyncBackupManager_State_Downloading`),
			 * then we need to know the total number of records to be downloaded from the cloud.
			 *
			 * However, there's a minor problem here:
			 * CloudKit doesn't support aggregate queries !
			 *
			 * So we cannot simply say: SELECT COUNT(*)
			 *
			 * Our only option (as far as I'm aware of),
			 * is to fetch the metadata for every record in the cloud.
			 * we would have to do this via recursive batch fetching,
			 * and counting the downloaded records as they stream in.
			 *
			 * The big downfall of this approach is that we end up downloading
			 * the CKRecord metadata 2 times for every record :(
			 *
			 * - first just to count the number of records
			 * - and again when we fetch the full record (with encrypted blob)
			 *
			 * Given this bad situation (Bad Apple),
			 * our current choice is to sacrifice the progress details.
			 */
			
			let privateCloudDatabase = CKContainer.default().privateCloudDatabase
			let zoneID = self.recordZoneID()
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = true
			
			do {
				try await privateCloudDatabase.configuredWith(configuration: configuration) { database in
					
					// Step 2 of 4:
					//
					// Execute a CKQuery to download a batch of payments from the cloud.
					// There may be multiple batches available for download.
					
					let predicate: NSPredicate
					if let oldestCreationDate {
						predicate = NSPredicate(format: "creationDate < %@", oldestCreationDate as CVarArg)
					} else {
						predicate = NSPredicate(format: "TRUEPREDICATE")
					}
					
					let query = CKQuery(
						recordType: cards_record_table_name,
						predicate: predicate
					)
					query.sortDescriptors = [
						NSSortDescriptor(key: "creationDate", ascending: false)
					]
					
					var done = false
					var batch = 0
					var cursor: CKQueryOperation.Cursor? = nil
					
					while !done {
						
						// For the first batch, we want to quickly fetch an item from the cloud,
						// and add it to the database. The faster the better, this way the user
						// knows the app is restoring his/her cards.
						//
						// After that, we can slowly increase the batch size,
						// as the user becomes aware of what's happening.
						
						let resultsLimit: Int
						switch batch {
							case 0  : resultsLimit = 1
							case 1  : resultsLimit = 2
							case 2  : resultsLimit = 3
							case 3  : resultsLimit = 4
							default : resultsLimit = 8
						}
						
						log.trace("downloadCards(): batchFetch: requesting \(resultsLimit)")
						
						let results: [(CKRecord.ID, Result<CKRecord, Error>)]
						if let prvCursor = cursor {
							(results, cursor) = try await database.records(
								continuingMatchFrom: prvCursor,
								resultsLimit: resultsLimit
							)
						} else {
							(results, cursor) = try await database.records(
								matching: query,
								inZoneWith: zoneID,
								resultsLimit: resultsLimit
							)
						}
						
						var items: [DownloadedCard] = []
						for (_, result) in results {
							if case .success(let record) = result {
								let card = self.decryptAndDeserializeCard(record)
								if let card {
									items.append(DownloadedCard(
										record: record,
										card: card
									))
								}
							}
						}

						log.trace("downloadCards(): batchFetch: received \(items.count)")
						
						// Step 3 of 4:
						//
						// Save the downloaded results to the database.
						
						try await Task { @MainActor [items] in
							
							var oldest: Date? = nil
							
							var rows: [BoltCardInfo] = []
							var metadataMap: [Lightning_kmpUUID: CloudKitCardsDb.MetadataRow] = [:]
							
							for item in items {
								
								rows.append(item.card)
								
								let cardId = item.card.id
								let creationDate = item.record.creationDate ?? Date()
								let creation = self.dateToMillis(creationDate)
								let metadata = self.metadataForRecord(item.record)
								
								metadataMap[cardId] = CloudKitCardsDb.MetadataRow(
									recordCreation: creation,
									recordBlob: metadata.toKotlinByteArray()
								)
								
								if let prv = oldest {
									if creationDate < prv {
										oldest = creationDate
									}
								} else {
									oldest = creationDate
								}
							}
							
							log.trace("downloadCards(): cloudKitDb.updateRows()...")
							
							try await self.cloudKitDb.cards.updateRows(
								downloadedCards: rows,
								updateMetadata: metadataMap
							)
							
							downloadProgress.cards_finishBatch(completed: items.count, oldest: oldest)
							
						}.value
						// </Task @MainActor>
						
						if (cursor == nil) {
							log.trace("downloadCards(): moreInCloud = false")
							done = true
						} else {
							log.trace("downloadCards(): moreInCloud = true")
							batch += 1
						}
						
					} // </while !done>
				} // </configuredWith>
				
				log.trace("downloadCards(): enqueueMissingItems()...")
				
				// Step 4 of 4:
				//
				// There may be items that we've added to the database since we started the download process.
				// So we enqueue these for upload now.
				
				try await Task { @MainActor in
					try await self.cloudKitDb.cards.enqueueMissingItems()
				}.value
				
				log.trace("downloadCards(): finish: success")

				prefs.backupTransactions.hasDownloadedCards = true
				self.consecutiveErrorCount = 0
				
				if let newState = await self.actor.didDownloadCards() {
					self.handleNewState(newState)
				}
				
			} catch {
				
				log.error("downloadCards(): error: \(error)")
				self.handleError(error)
			}
			
		} // </Task>
	}
	
	/// The upload task performs the following tasks:
	/// - extract rows from the database that need to be uploaded
	/// - serialize & encrypt the data
	/// - upload items to the user's private cloud database
	/// - remove the uploaded items from the queue
	/// - repeat as needed
	///
	func uploadCards(_ uploadProgress: SyncBackupManager_State_Uploading) {
		log.trace("uploadCards()")
		
		let prepareUpload = {(
			batch: FetchCardsQueueBatchResult
		) -> UploadCardsOperationInfo in
			
			log.trace("uploadCards(): prepareUpload()")
			
			var recordsToSave = [CKRecord]()
			var recordIDsToDelete = [CKRecord.ID]()
			
			var reverseMap = [CKRecord.ID: Lightning_kmpUUID]()
			
			// NB: batch.rowidMap may contain the same cardId multiple times.
			// And if we include the same record multiple times in the CKModifyRecordsOperation,
			// then the operation will fail.
			//
			for cardId in batch.uniqueCardIds() {
				
				var existingRecord: CKRecord? = nil
				if let metadata = batch.metadataMap[cardId] {
					
					let data = metadata.toSwiftData()
					existingRecord = self.recordFromMetadata(data)
				}
				
				if let cardInfo = batch.rowMap[cardId] {
					
					if let ciphertext = self.serializeAndEncryptCard(cardInfo) {

						let record = existingRecord ?? CKRecord(
							recordType: cards_record_table_name,
							recordID: self.recordID(cardId: cardId)
						)
						
						record[cards_record_column_data] = ciphertext
						
						recordsToSave.append(record)
						reverseMap[record.recordID] = cardId
					}
					
				} else {
					
					// The card has been deleted from the local database.
					// So we're going to delete it from the cloud database (if it exists there).
					
					let recordID = existingRecord?.recordID ?? self.recordID(cardId: cardId)
					
					recordIDsToDelete.append(recordID)
					reverseMap[recordID] = cardId
				}
			}
			
			var opInfo = UploadCardsOperationInfo(
				batch: batch,
				recordsToSave: recordsToSave,
				recordIDsToDelete: recordIDsToDelete,
				reverseMap: reverseMap
			)
			
			// Edge-case: A rowid wasn't able to be converted to a UUID.
			//
			// So the rowid is not represented in either `rowidMap` or `uniqueCardIds()`.
			// Nor is it reprensented in `recordsToSave` or `recordIDsToDelete`.
			//
			// The end result is that we have an empty operation.
			// And we won't remove the rowid from the database either, creating an infinite loop.
			//
			// So we add a sanity check here.
			
			for rowid in batch.rowids {
				if batch.rowidMap[rowid] == nil {
					log.warning("Malformed UUID in cards_queue")
					opInfo.completedRowids.append(rowid)
				}
			}
			
			return opInfo
			
		} // </prepareUpload()>
		
		let performUpload = {(
			opInfo: UploadCardsOperationInfo
		) async throws -> UploadCardsOperationInfo in
			
			log.trace("uploadCards(): performUpload()")
			log.trace("opInfo.recordsToSave.count = \(opInfo.recordsToSave.count)")
			log.trace("opInfo.recordIDsToDelete.count = \(opInfo.recordIDsToDelete.count)")
			
			if Task.isCancelled {
				throw CKError(.operationCancelled)
			}
			
			let container = CKContainer.default()
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = self.prefs.backupTransactions.useCellular
			
			return try await container.privateCloudDatabase.configuredWith(configuration: configuration) { database in
				
				let (saveResults, deleteResults) = try await database.modifyRecords(
					saving: opInfo.recordsToSave,
					deleting: opInfo.recordIDsToDelete,
					savePolicy: CKModifyRecordsOperation.RecordSavePolicy.ifServerRecordUnchanged,
					atomically: false
				)
				
				// saveResults: [CKRecord.ID : Result<CKRecord, Error>]
				// deleteResults: [CKRecord.ID : Result<Void, Error>]
				
				var nextOpInfo = opInfo
				
				var accountFailure: CKError? = nil
				var recordIDsToFetch: [CKRecord.ID] = []
				
				for (recordID, result) in saveResults {
					
					guard let cardId = opInfo.reverseMap[recordID] else {
						continue
					}
					
					switch result {
					case .success(let record):
						nextOpInfo.savedRecords.append(record)
						
						for rowid in nextOpInfo.batch.rowidsMatching(cardId) {
							nextOpInfo.completedRowids.append(rowid)
						}

					case .failure(let error):
						if let recordError = error as? CKError {
							
							nextOpInfo.partialFailures[cardId] = recordError

							// If this is a standard your-changetag-was-out-of-date message from the server,
							// then we just need to fetch the latest CKRecord metadata from the cloud,
							// and then re-try our upload.
							if recordError.errorCode == CKError.serverRecordChanged.rawValue {
								recordIDsToFetch.append(recordID)
							} else if recordError.errorCode == CKError.accountTemporarilyUnavailable.rawValue {
								accountFailure = recordError
							}
						}
					} // </switch>
				} // </for>
				
				for (recordID, result) in deleteResults {
					
					guard let cardId = opInfo.reverseMap[recordID] else {
						continue
					}
					
					switch result {
					case .success(_):
						nextOpInfo.deletedRecordIds.append(recordID)
						
						for rowid in nextOpInfo.batch.rowidsMatching(cardId) {
							nextOpInfo.completedRowids.append(rowid)
						}
						
					case .failure(let error):
						if let recordError = error as? CKError {
							
							nextOpInfo.partialFailures[cardId] = recordError

							if recordError.errorCode == CKError.accountTemporarilyUnavailable.rawValue {
								accountFailure = recordError
							}
						}
					} // </switch>
				} // </for>
				
				if let accountFailure {
					// We received one or more `accountTemporarilyUnavailable` errors.
					// We have special error handling code for this.
					throw accountFailure
					
				} else if !recordIDsToFetch.isEmpty {
					// One or more records was out-of-date (as compared with the server version).
					// So we need to refetch those records from the server.
					
					if Task.isCancelled {
						throw CKError(.operationCancelled)
					}
					
					let results: [CKRecord.ID : Result<CKRecord, Error>] = try await database.records(
						for: recordIDsToFetch,
						desiredKeys: [] // fetch only basic CKRecord metadata
					)
					
					let fetchedRecords: [CKRecord] = results.values.compactMap { result in
						return try? result.get()
					}
					
					// We successfully fetched the latest CKRecord(s) from the server.
					// We add to nextOpInfo.savedRecords, which will write the CKRecord to the database.
					// So on the next upload attempt, we should have the latest version.
					
					nextOpInfo.savedRecords.append(contentsOf: fetchedRecords)
					
				} else {
					// Every payment in the batch was successful
				}
				
				return nextOpInfo
				
			} // </configuredWith>
			
		} // </performUpload()>
		
		let updateDatabase = {(
			opInfo: UploadCardsOperationInfo
		) async throws -> Void in
			
			log.trace("uploadCards(): updateDatabase()")
			
			var deleteFromQueue = [KotlinLong]()
			var deleteFromMetadata = [Lightning_kmpUUID]()
			var updateMetadata = [Lightning_kmpUUID: CloudKitCardsDb.MetadataRow]()
			
			for (rowid) in opInfo.completedRowids {
				deleteFromQueue.append(KotlinLong(longLong: rowid))
			}
			for recordId in opInfo.deletedRecordIds {
				if let cardId = opInfo.reverseMap[recordId] {
					deleteFromMetadata.append(cardId)
				}
			}
			for record in opInfo.savedRecords {
				if let cardId = opInfo.reverseMap[record.recordID] {
					
					let creation = self.dateToMillis(record.creationDate ?? Date())
					let metadata = self.metadataForRecord(record)
					
					updateMetadata[cardId] = CloudKitCardsDb.MetadataRow(
						recordCreation: creation,
						recordBlob:  metadata.toKotlinByteArray()
					)
				}
			}
			
			// Handle partial failures
			let partialFailures: [String: CKError?] = opInfo.partialFailures.mapKeys { $0.id }
			
			for cardIdStr in self.updateConsecutivePartialFailures(partialFailures) {
				for rowid in opInfo.batch.rowidsMatching(cardIdStr) {
					deleteFromQueue.append(KotlinLong(longLong: rowid))
				}
			}
			
			log.debug("deleteFromQueue.count = \(deleteFromQueue.count)")
			log.debug("deleteFromMetadata.count = \(deleteFromMetadata.count)")
			log.debug("updateMetadata.count = \(updateMetadata.count)")
		
			try await Task { @MainActor [deleteFromQueue, deleteFromMetadata, updateMetadata] in
				try await self.cloudKitDb.cards.updateRows(
					deleteFromQueue: deleteFromQueue,
					deleteFromMetadata: deleteFromMetadata,
					updateMetadata: updateMetadata
				)
			}.value
			
		} // </updateDatabase()>
		
		let finish = {(
			result: Result<Void, Error>
		) async -> Void in
			
			switch result {
			case .success:
				log.trace("uploadCards(): finish(): success")
				
				self.consecutiveErrorCount = 0
				if let newState = await self.actor.didUploadItems() {
					self.handleNewState(newState)
				}
				
			case .failure(let error):
				log.trace("uploadCards(): finish(): failure")
				self.handleError(error)
			}
			
		} // </finish()>
		
		Task {
			log.trace("uploadCards(): starting task...")
			
			do {
				// Step 1 of 4:
				//
				// Check the `cloudkit_cards_queue` table,
				// to see if there's anything we need to upload.
				
				let result: CloudKitCardsDb.FetchQueueBatchResult = try await Task { @MainActor in
					return try await self.cloudKitDb.cards.fetchQueueBatch(limit: 1)
				}.value
				
				let batch = result.convertToSwift()
				
				log.debug("uploadCards(): batch.rowids.count = \(batch.rowids.count)")
				log.debug("uploadCards(): batch.rowidMap.count = \(batch.rowidMap.count)")
				log.debug("uploadCards(): batch.rowMap.count = \(batch.rowMap.count)")
				log.debug("uploadCards(): batch.metadataMap.count = \(batch.metadataMap.count)")
				
				if batch.rowids.isEmpty {
					// There's nothing queued for upload, so we're done.
					
					// Bug Fix / Workaround:
					// The queueCountPublisher isn't firing reliably, and I'm not sure why...
					//
					// This can lead to the following infinite loop:
					// - queueCountPublisher fires and reports a non-zero count
					// - the actor's corresponding queueCount is updated
					// - the uploadTask is evetually triggered
					// - the item(s) are properly uploaded, and the rows are deleted from the queue
					// - queueCountPublisher does NOT properly fire
					// - the uploadTask is triggered again
					// - it finds zero rows to upload, but actor's queueCount remains unchanged
					// - the uploadTask is triggered again
					// - ...
					//
					if let newState = await self.actor.cardsQueueCountChanged(0, wait: nil) {
						self.handleNewState(newState)
					}
					return await finish(.success)
				}
				
				// Step 2 of 4:
				//
				// Serialize and encrypt the card information.
				// Then encapsulate the encrypted blob into a CKRecord.
				// And prepare a full CKModifyRecordsOperation for upload.
				
				var opInfo = prepareUpload(batch)
				
				// Step 3 of 4:
				//
				// Perform the cloud operation.
				
				let inFlightCount = opInfo.recordsToSave.count + opInfo.recordIDsToDelete.count
				if inFlightCount == 0 {
					// Edge case: there are no cloud tasks to perform.
					// We have to skip the upload, because it will fail if given an empty set of tasks.
				} else {
					opInfo = try await performUpload(opInfo)
				}
				
				// Step 4 of 4:
				//
				// Process the upload results.
				
				try await updateDatabase(opInfo)
				
				// Done !
				
				uploadProgress.completeCards_inFlight(inFlightCount)
				return await finish(.success)
				
			} catch {
				return await finish(.failure(error))
			}
		} // </Task>
	}
	
	// ----------------------------------------
	// MARK: Record ID
	// ----------------------------------------
	
	private func recordID(cardId: Lightning_kmpUUID) -> CKRecord.ID {
		
		// The recordID is:
		// - deterministic => by hashing the cardId
		// - secure => by mixing in the nodeIdHash (nodeKey.publicKey.hash160)
		
		let prefix = walletInfo.nodeIdHash.data(using: .utf8)!
		let suffix = cardId.description().data(using: .utf8)!
		
		let hashMe = prefix + suffix
		let digest = SHA256.hash(data: hashMe)
		let hash = digest.toHex(.lowerCase)
		
		return CKRecord.ID(recordName: hash, zoneID: recordZoneID())
	}
	
	// ----------------------------------------
	// MARK: Utilities
	// ----------------------------------------
	
	/// Performs all of the following:
	/// - serializes item (CBOR)
	/// - encrypts the blob using the cloudKey
	///
	private func serializeAndEncryptCard(
		_ card: BoltCardInfo
	) -> Data? {
		
		let wrapper = CloudCard.V0(card: card)
		let cbor = wrapper.cborSerialize().toSwiftData()
		
		#if DEBUG
		let jsonData = wrapper.jsonSerialize().toSwiftData()
		let jsonStr = String(data: jsonData, encoding: .utf8)
		log.debug("Uploading record (JSON representation):\n\(jsonStr ?? "<nil>")")
		#endif
		
		let cleartext: Data = cbor
		var ciphertext: Data? = nil
		do {
			let box = try ChaChaPoly.seal(cleartext, using: self.cloudKey)
			ciphertext = box.combined
			
		} catch {
			log.error("Error encrypting row with ChaChaPoly: \(String(describing: error))")
		}
		
		if let ciphertext {
			return ciphertext
		} else {
			return nil
		}
	}
	
	private func decryptAndDeserializeCard(
		_ record: CKRecord
	) -> BoltCardInfo? {
		
		log.debug("Received record:")
		log.debug(" - recordID: \(record.recordID)")
		log.debug(" - creationDate: \(record.creationDate ?? Date.distantPast)")
		
		guard let ciphertext = record[cards_record_column_data] as? Data else {
			log.error("Missing column.data: skipping \(record.recordID)")
			return nil
		}
		
		let cleartext: Data
		do {
			let box = try ChaChaPoly.SealedBox(combined: ciphertext)
			cleartext = try ChaChaPoly.open(box, using: self.cloudKey)
		} catch {
			log.error("Error decrypting record.data: skipping \(record.recordID)")
			return nil
		}
		
		let card: BoltCardInfo?
		do {
			let cleartext_kotlin = cleartext.toKotlinByteArray()
			card = try CloudCard.companion.cborDeserializeAndUnwrap(blob: cleartext_kotlin)
			
		} catch {
			log.error("Error deserializing record.data: skipping \(record.recordID)")
			return nil
		}
		
		return card
	}
	
	// ----------------------------------------
	// MARK: Debugging
	// ----------------------------------------
	#if DEBUG
	
	func listAllCards() {
		log.trace("listAllCards()")
		
		let query = CKQuery(
			recordType: cards_record_table_name,
			predicate: NSPredicate(format: "TRUEPREDICATE")
		)
		query.sortDescriptors = [
			NSSortDescriptor(key: "creationDate", ascending: false)
		]
		
		let operation = CKQueryOperation(query: query)
		operation.zoneID = recordZoneID()
		
		recursiveListCardsBatch(operation: operation)
	}
	
	private func recursiveListCardsBatch(operation: CKQueryOperation) {
		log.trace("recursiveListCardsBatch()")
		
		operation.recordMatchedBlock = {(recordID: CKRecord.ID, result: Result<CKRecord, Error>) in
			
			if let record = try? result.get() {
				
				log.debug("Received record:")
				log.debug(" - recordID: \(record.recordID)")
				log.debug(" - creationDate: \(record.creationDate ?? Date.distantPast)")
				
				if let data = record[cards_record_column_data] as? Data {
					log.debug(" - data.count: \(data.count)")
				} else {
					log.debug(" - data: ?")
				}
			}
		}
		
		operation.queryResultBlock = {(result: Result<CKQueryOperation.Cursor?, Error>) in
			
			switch result {
			case .success(let cursor):
				if let cursor = cursor {
					log.debug("recursiveListCardsBatch: Continuing with cursor...")
					self.recursiveListCardsBatch(operation: CKQueryOperation(cursor: cursor))
					
				} else {
					log.debug("recursiveListCardsBatch: Complete")
				}
				
			case .failure(let error):
				log.debug("recursiveListCardsBatch: error: \(error)")
			}
		}
		
		let configuration = CKOperation.Configuration()
		configuration.allowsCellularAccess = true
		
		operation.configuration = configuration
		
		CKContainer.default().privateCloudDatabase.add(operation)
	}
	
	#endif
}
