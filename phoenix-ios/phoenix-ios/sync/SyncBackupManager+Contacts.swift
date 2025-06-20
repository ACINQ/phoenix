import Foundation
import CloudKit
import CryptoKit
import PhoenixShared

fileprivate let filename = "SyncBackupManager+Contacts"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate let contacts_record_table_name = "contacts"
fileprivate let contacts_record_column_data = "encryptedData"
fileprivate let contacts_record_column_photo = "photo" // CKAsset: automatically encrypted by CloudKit

fileprivate struct DownloadedContact {
	let record: CKRecord
	let contact: ContactInfo
}

fileprivate struct UploadContactsOperationInfo {
	let batch: FetchContactsQueueBatchResult
	
	let recordsToSave: [CKRecord]
	let recordIDsToDelete: [CKRecord.ID]
	
	let reverseMap: [CKRecord.ID: Lightning_kmpUUID]
	
	var completedRowids: [Int64] = []
	
	var partialFailures: [Lightning_kmpUUID: CKError?] = [:]
	
	var savedRecords: [CKRecord] = []
	var deletedRecordIds: [CKRecord.ID] = []
}

extension SyncBackupManager {
	
	func startContactsQueueCountMonitor() {
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
	
	// ----------------------------------------
	// MARK: IO
	// ----------------------------------------
	
	func downloadContacts(_ downloadProgress: SyncBackupManager_State_Downloading) {
		log.trace("downloadContacts()")
		
		Task {
			
			// Step 1 of 4:
			//
			// We are downloading payments from newest to oldest.
			// So first we fetch the oldest payment date in the table (if there is one)
			
			let millis: KotlinLong? = try await Task { @MainActor in
				return try await self.cloudKitDb.contacts.fetchOldestCreation()
			}.value
			
			let oldestCreationDate = millis?.int64Value.toDate(from: .milliseconds)
			downloadProgress.setContacts_oldestCompletedDownload(oldestCreationDate)
			
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
						recordType: contacts_record_table_name,
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
						// knows the app is restoring his/her contacts.
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
						
						log.trace("downloadContacts(): batchFetch: requesting \(resultsLimit)")
						
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
						
						var items: [DownloadedContact] = []
						for (_, result) in results {
							if case .success(let record) = result {
								let contact = self.decryptAndDeserializeContact(record)
								if let contact {
									items.append(DownloadedContact(
										record: record,
										contact: contact
									))
								}
							}
						}

						log.trace("downloadContacts(): batchFetch: received \(items.count)")
						
						// Step 3 of 4:
						//
						// Save the downloaded results to the database.
						
						try await Task { @MainActor [items] in
							
							var oldest: Date? = nil
							
							var rows: [ContactInfo] = []
							var metadataMap: [Lightning_kmpUUID: CloudKitContactsDb.MetadataRow] = [:]
							
							for item in items {
								
								rows.append(item.contact)
								
								let contactId = item.contact.id
								let creationDate = item.record.creationDate ?? Date()
								let creation = self.dateToMillis(creationDate)
								let metadata = self.metadataForRecord(item.record)
								
								metadataMap[contactId] = CloudKitContactsDb.MetadataRow(
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
							
							log.trace("downloadPayments(): cloudKitDb.updateRows()...")
							
							try await self.cloudKitDb.contacts.updateRows(
								downloadedContacts: rows,
								updateMetadata: metadataMap
							)
							
							downloadProgress.contacts_finishBatch(completed: items.count, oldest: oldest)
							
						}.value
						// </Task @MainActor>
						
						if (cursor == nil) {
							log.trace("downloadContacts(): moreInCloud = false")
							done = true
						} else {
							log.trace("downloadContacts(): moreInCloud = true")
							batch += 1
						}
						
					} // </while !done>
				} // </configuredWith>
				
				log.trace("downloadContacts(): enqueueMissingItems()...")
				
				// Step 4 of 4:
				//
				// There may be payments that we've added to the database since we started the download process.
				// So we enqueue these for upload now.
				
				try await Task { @MainActor in
					try await self.cloudKitDb.contacts.enqueueMissingItems()
				}.value
				
				log.trace("downloadContacts(): finish: success")
				
				prefs.backupTransactions.hasDownloadedContacts = true
				self.consecutiveErrorCount = 0
				
				if let newState = await self.actor.didDownloadContacts() {
					self.handleNewState(newState)
				}
				
			} catch {
				
				log.error("downloadContacts(): error: \(error)")
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
	func uploadContacts(_ uploadProgress: SyncBackupManager_State_Uploading) {
		log.trace("uploadContacts()")
		
		let prepareUpload = {(
			batch: FetchContactsQueueBatchResult
		) -> UploadContactsOperationInfo in
			
			log.trace("uploadContacts(): prepareUpload()")
			
			var recordsToSave = [CKRecord]()
			var recordIDsToDelete = [CKRecord.ID]()
			
			var reverseMap = [CKRecord.ID: Lightning_kmpUUID]()
			
			// NB: batch.rowidMap may contain the same contactId multiple times.
			// And if we include the same record multiple times in the CKModifyRecordsOperation,
			// then the operation will fail.
			//
			for contactId in batch.uniqueContactIds() {
				
				var existingRecord: CKRecord? = nil
				if let metadata = batch.metadataMap[contactId] {
					
					let data = metadata.toSwiftData()
					existingRecord = self.recordFromMetadata(data)
				}
				
				if let contactInfo = batch.rowMap[contactId] {
					
					if let ciphertext = self.serializeAndEncryptContact(contactInfo) {

						let record = existingRecord ?? CKRecord(
							recordType: contacts_record_table_name,
							recordID: self.recordID(contactId: contactId)
						)
						
						record[contacts_record_column_data] = ciphertext
						
						if let fileName = contactInfo.photoUri {
							let fileUrl = PhotosManager.urlForPhoto(fileName: fileName)
							record[contacts_record_column_photo] = CKAsset(fileURL: fileUrl)
						}
						
						recordsToSave.append(record)
						reverseMap[record.recordID] = contactId
					}
					
				} else {
					
					// The contact has been deleted from the local database.
					// So we're going to delete it from the cloud database (if it exists there).
					
					let recordID = existingRecord?.recordID ?? self.recordID(contactId: contactId)
					
					recordIDsToDelete.append(recordID)
					reverseMap[recordID] = contactId
				}
			}
			
			var opInfo = UploadContactsOperationInfo(
				batch: batch,
				recordsToSave: recordsToSave,
				recordIDsToDelete: recordIDsToDelete,
				reverseMap: reverseMap
			)
			
			// Edge-case: A rowid wasn't able to be converted to a UUID.
			//
			// So the rowid is not represented in either `rowidMap` or `uniquePaymentIds()`.
			// Nor is it reprensented in `recordsToSave` or `recordIDsToDelete`.
			//
			// The end result is that we have an empty operation.
			// And we won't remove the rowid from the database either, creating an infinite loop.
			//
			// So we add a sanity check here.
			
			for rowid in batch.rowids {
				if batch.rowidMap[rowid] == nil {
					log.warning("Malformed UUID in contacts_queue")
					opInfo.completedRowids.append(rowid)
				}
			}
			
			return opInfo
			
		} // </prepareUpload()>
		
		let performUpload = {(
			opInfo: UploadContactsOperationInfo
		) async throws -> UploadContactsOperationInfo in
			
			log.trace("uploadContacts(): performUpload()")
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
					
					guard let contactId = opInfo.reverseMap[recordID] else {
						continue
					}
					
					switch result {
					case .success(let record):
						nextOpInfo.savedRecords.append(record)
						
						for rowid in nextOpInfo.batch.rowidsMatching(contactId) {
							nextOpInfo.completedRowids.append(rowid)
						}

					case .failure(let error):
						if let recordError = error as? CKError {
							
							nextOpInfo.partialFailures[contactId] = recordError

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
					
					guard let paymentId = opInfo.reverseMap[recordID] else {
						continue
					}
					
					switch result {
					case .success(_):
						nextOpInfo.deletedRecordIds.append(recordID)
						
						for rowid in nextOpInfo.batch.rowidsMatching(paymentId) {
							nextOpInfo.completedRowids.append(rowid)
						}
						
					case .failure(let error):
						if let recordError = error as? CKError {
							
							nextOpInfo.partialFailures[paymentId] = recordError

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
					
					log.trace("Fetching out-of-date CKRecords (\(recordIDsToFetch.count))...")
					
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
			opInfo: UploadContactsOperationInfo
		) async throws -> Void in
			
			log.trace("uploadContacts(): updateDatabase()")
			
			var deleteFromQueue = [KotlinLong]()
			var deleteFromMetadata = [Lightning_kmpUUID]()
			var updateMetadata = [Lightning_kmpUUID: CloudKitContactsDb.MetadataRow]()
			
			for (rowid) in opInfo.completedRowids {
				deleteFromQueue.append(KotlinLong(longLong: rowid))
			}
			for recordId in opInfo.deletedRecordIds {
				if let contactId = opInfo.reverseMap[recordId] {
					deleteFromMetadata.append(contactId)
				}
			}
			for record in opInfo.savedRecords {
				if let contactId = opInfo.reverseMap[record.recordID] {
					
					let creation = self.dateToMillis(record.creationDate ?? Date())
					let metadata = self.metadataForRecord(record)
					
					updateMetadata[contactId] = CloudKitContactsDb.MetadataRow(
						recordCreation: creation,
						recordBlob:  metadata.toKotlinByteArray()
					)
				}
			}
			
			// Handle partial failures
			let partialFailures: [String: CKError?] = opInfo.partialFailures.mapKeys { $0.id }
			
			for contactIdStr in self.updateConsecutivePartialFailures(partialFailures) {
				for rowid in opInfo.batch.rowidsMatching(contactIdStr) {
					deleteFromQueue.append(KotlinLong(longLong: rowid))
				}
			}
			
			log.debug("deleteFromQueue.count = \(deleteFromQueue.count)")
			log.debug("deleteFromMetadata.count = \(deleteFromMetadata.count)")
			log.debug("updateMetadata.count = \(updateMetadata.count)")
		
			try await Task { @MainActor [deleteFromQueue, deleteFromMetadata, updateMetadata] in
				try await self.cloudKitDb.contacts.updateRows(
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
				log.trace("uploadContacts(): finish(): success")
				
				self.consecutiveErrorCount = 0
				if let newState = await self.actor.didUploadItems() {
					self.handleNewState(newState)
				}
				
			case .failure(let error):
				log.trace("uploadContacts(): finish(): failure")
				self.handleError(error)
			}
			
		} // </finish()>
		
		Task {
			log.trace("uploadContacts(): starting task...")
			
			do {
				// Step 1 of 4:
				//
				// Check the `cloudkit_contacts_queue` table,
				// to see if there's anything we need to upload.
				
				let result: CloudKitContactsDb.FetchQueueBatchResult = try await Task { @MainActor in
					return try await self.cloudKitDb.contacts.fetchQueueBatch(limit: 1)
				}.value
				
				let batch = result.convertToSwift()
				
				log.debug("uploadContacts(): batch.rowids.count = \(batch.rowids.count)")
				log.debug("uploadContacts(): batch.rowidMap.count = \(batch.rowidMap.count)")
				log.debug("uploadContacts(): batch.rowMap.count = \(batch.rowMap.count)")
				log.debug("uploadContacts(): batch.metadataMap.count = \(batch.metadataMap.count)")
				
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
					if let newState = await self.actor.contactsQueueCountChanged(0, wait: nil) {
						self.handleNewState(newState)
					}
					return await finish(.success)
				}
				
				// Step 2 of 4:
				//
				// Serialize and encrypt the contact information.
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
				
				uploadProgress.completeContacts_inFlight(inFlightCount)
				return await finish(.success)
				
			} catch {
				return await finish(.failure(error))
			}
		} // </Task>
	}
	
	// ----------------------------------------
	// MARK: Record ID
	// ----------------------------------------
	
	private func recordID(contactId: Lightning_kmpUUID) -> CKRecord.ID {
		
		// The recordID is:
		// - deterministic => by hashing the contactId
		// - secure => by mixing in the nodeIdHash (nodeKey.publicKey.hash160)
		
		let prefix = walletInfo.nodeIdHash.data(using: .utf8)!
		let suffix = contactId.description().data(using: .utf8)!
		
		let hashMe = prefix + suffix
		let digest = SHA256.hash(data: hashMe)
		let hash = digest.toHex(options: .lowerCase)
		
		return CKRecord.ID(recordName: hash, zoneID: recordZoneID())
	}
	
	// ----------------------------------------
	// MARK: Utilities
	// ----------------------------------------
	
	/// Performs all of the following:
	/// - serializes incoming/outgoing payment into JSON
	/// - adds randomized padding to obfuscate payment type
	/// - encrypts the blob using the cloudKey
	///
	private func serializeAndEncryptContact(
		_ contact: ContactInfo
	) -> Data? {
		
		let wrapper = CloudContact.V1(contact: contact)
		let serializedData = wrapper.serialize().toSwiftData()
		
		let cleartext: Data = serializedData
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
	
	private func decryptAndDeserializeContact(
		_ record: CKRecord
	) -> ContactInfo? {
		
		log.debug("Received record:")
		log.debug(" - recordID: \(record.recordID)")
		log.debug(" - creationDate: \(record.creationDate ?? Date.distantPast)")
		
		var photoUri: String? = nil
		if let asset = record[contacts_record_column_photo] as? CKAsset {
			
			if let srcFileURL = asset.fileURL {
				
				let dstFileName = PhotosManager.genFileName()
				let dstFileURL = PhotosManager.urlForPhoto(fileName: dstFileName)
				
				do {
					try FileManager.default.copyItem(at: srcFileURL, to: dstFileURL)
					photoUri = dstFileName
				} catch {
					log.error("Unable to copy photo: \(error)")
				}
			}
		}
		
		var contact: ContactInfo? = nil
		if let ciphertext = record[contacts_record_column_data] as? Data {
			
			var cleartext: Data? = nil
			do {
				let box = try ChaChaPoly.SealedBox(combined: ciphertext)
				cleartext = try ChaChaPoly.open(box, using: self.cloudKey)
			} catch {
				log.error("Error decrypting record.data: skipping \(record.recordID)")
			}
			
			if let cleartext {
				let cleartext_kotlin = cleartext.toKotlinByteArray()
				contact = CloudContact.companion.deserialize(
					blob: cleartext_kotlin,
					photoUri: photoUri
				)
			}
		}
		
		if contact == nil, let photoUri {
			// Edge case cleanup
			Task {
				await PhotosManager.shared.deleteFromDisk(fileName: photoUri)
			}
		}
		
		return contact
	}
	
	// ----------------------------------------
	// MARK: Debugging
	// ----------------------------------------
	#if DEBUG
	
	func listAllContacts() {
		log.trace("listAllContacts()")
		
		let query = CKQuery(
			recordType: contacts_record_table_name,
			predicate: NSPredicate(format: "TRUEPREDICATE")
		)
		query.sortDescriptors = [
			NSSortDescriptor(key: "creationDate", ascending: false)
		]
		
		let operation = CKQueryOperation(query: query)
		operation.zoneID = recordZoneID()
		
		recursiveListContactsBatch(operation: operation)
	}
	
	private func recursiveListContactsBatch(operation: CKQueryOperation) {
		log.trace("recursiveListContactsBatch()")
		
		operation.recordMatchedBlock = {(recordID: CKRecord.ID, result: Result<CKRecord, Error>) in
			
			if let record = try? result.get() {
				
				log.debug("Received record:")
				log.debug(" - recordID: \(record.recordID)")
				log.debug(" - creationDate: \(record.creationDate ?? Date.distantPast)")
				
				if let data = record[contacts_record_column_data] as? Data {
					log.debug(" - data.count: \(data.count)")
				} else {
					log.debug(" - data: ?")
				}
				
				if let blob = record[contacts_record_column_photo] as? CKAsset {
					log.debug(" - photo: \(blob.fileURL?.path ?? "<?>")")
				} else {
					log.debug(" - photo: nil")
				}
			}
		}
		
		operation.queryResultBlock = {(result: Result<CKQueryOperation.Cursor?, Error>) in
			
			switch result {
			case .success(let cursor):
				if let cursor = cursor {
					log.debug("recursiveListContactsBatch: Continuing with cursor...")
					self.recursiveListContactsBatch(operation: CKQueryOperation(cursor: cursor))
					
				} else {
					log.debug("recursiveListContactsBatch: Complete")
				}
				
			case .failure(let error):
				log.debug("recursiveListContactsBatch: error: \(String(describing: error))")
			}
		}
		
		let configuration = CKOperation.Configuration()
		configuration.allowsCellularAccess = true
		
		operation.configuration = configuration
		
		CKContainer.default().privateCloudDatabase.add(operation)
	}
	
	func deleteAllContacts() {
		log.trace("deleteAllContacts()")
		
		let query = CKQuery(
			recordType: contacts_record_table_name,
			predicate: NSPredicate(format: "TRUEPREDICATE")
		)
		query.sortDescriptors = [
			NSSortDescriptor(key: "creationDate", ascending: false)
		]
		
		let operation = CKQueryOperation(query: query)
		operation.zoneID = recordZoneID()
		
		findContactsBatch(operation: operation)
	}
	
	private func findContactsBatch(operation: CKQueryOperation) {
		log.trace("findContactsBatch()")
		
		var results: [CKRecord.ID] = []
		operation.recordMatchedBlock = {(recordID: CKRecord.ID, result: Result<CKRecord, Error>) in
			
			results.append(recordID)
		}
		
		operation.queryResultBlock = {(result: Result<CKQueryOperation.Cursor?, Error>) in
			
			switch result {
			case .success(let cursor):
				log.debug("findContactsBatch: found: \(results.count)")
				if results.isEmpty {
					log.debug("findContactsBatch: complete")
				} else {
					self.deleteContactsBatch(ids: results, cursor: cursor)
				}
				
			case .failure(let error):
				log.debug("findContactsBatch: error: \(String(describing: error))")
			}
		}
		
		let configuration = CKOperation.Configuration()
		configuration.allowsCellularAccess = true
		
		operation.configuration = configuration
		
		CKContainer.default().privateCloudDatabase.add(operation)
	}
	
	private func deleteContactsBatch(ids: [CKRecord.ID], cursor: CKQueryOperation.Cursor?) {
		log.trace("deleteContactsBatch()")
		
		let operation = CKModifyRecordsOperation(recordsToSave: [], recordIDsToDelete: ids)
		
		operation.modifyRecordsResultBlock = {(_ result: Result<Void, any Error>) in
			
			switch result {
			case .success():
				log.debug("deleteContactsBatch: deleted: \(ids.count)")
				if let cursor {
					self.findContactsBatch(operation: CKQueryOperation(cursor: cursor))
				} else {
					log.debug("deleteContactsBatch: complete")
				}
				
			case .failure(let error):
				log.debug("deleteContactsBatch: error: \(String(describing: error))")
			}
		}
		
		let configuration = CKOperation.Configuration()
		configuration.allowsCellularAccess = true
		
		operation.configuration = configuration
		
		CKContainer.default().privateCloudDatabase.add(operation)
	}
	
	#endif
}
