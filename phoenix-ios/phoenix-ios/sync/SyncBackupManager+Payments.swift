import Foundation
import CloudKit
import CryptoKit
import PhoenixShared

fileprivate let filename = "SyncBackupManager+Payments"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate struct DownloadedPayment {
	let record: CKRecord
	let unpaddedSize: Int
	let payment: Lightning_kmpWalletPayment
	let metadata: WalletPaymentMetadataRow?
}

fileprivate struct UploadPaymentsOperationInfo {
	let batch: FetchPaymentsQueueBatchResult
	
	let recordsToSave: [CKRecord]
	let recordIDsToDelete: [CKRecord.ID]
	
	let reverseMap: [CKRecord.ID: WalletPaymentId]
	let unpaddedMap: [WalletPaymentId: Int]
	
	var completedRowids: [Int64] = []
	
	var partialFailures: [WalletPaymentId: CKError?] = [:]
	
	var savedRecords: [CKRecord] = []
	var deletedRecordIds: [CKRecord.ID] = []
}

extension SyncBackupManager {
	
	func downloadPayments(_ downloadProgress: SyncBackupManager_State_Downloading) {
		log.trace("downloadPayments()")
		
		Task {
			
			// Step 1 of 4:
			//
			// We are downloading payments from newest to oldest.
			// So first we fetch the oldest payment date in the table (if there is one)
			
			let millis: KotlinLong? = try await Task { @MainActor in
				return try await self.cloudKitDb.payments.fetchOldestCreation()
			}.value
			
			let oldestCreationDate = millis?.int64Value.toDate(from: .milliseconds)
			downloadProgress.setPayments_oldestCompletedDownload(oldestCreationDate)
			
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
						recordType: payments_record_table_name,
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
						// knows the app is restoring his/her transactions.
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
						
						log.trace("downloadPayments(): batchFetch: requesting \(resultsLimit)")
						
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
						
						var items: [DownloadedPayment] = []
						for (_, result) in results {
							if case .success(let record) = result {
								let (payment, metadata, unpaddedSize) = self.decryptAndDeserializePayment(record)
								if let payment {
									items.append(DownloadedPayment(
										record: record,
										unpaddedSize: unpaddedSize,
										payment: payment,
										metadata: metadata
									))
								}
							}
						}
						
						log.trace("downloadPayments(): batchFetch: received \(items.count)")
						
						// Step 3 of 4:
						//
						// Save the downloaded results to the database.
						
						try await Task { @MainActor [items] in
							
							var oldest: Date? = nil
							
							var paymentRows: [Lightning_kmpWalletPayment] = []
							var paymentMetadataRows: [WalletPaymentId: WalletPaymentMetadataRow] = [:]
							var metadataMap: [WalletPaymentId: CloudKitPaymentsDb.MetadataRow] = [:]
							
							for item in items {
								
								paymentRows.append(item.payment)
								
								let paymentId = item.payment.walletPaymentId()
								paymentMetadataRows[paymentId] = item.metadata
								
								let creationDate = item.record.creationDate ?? Date()
								
								let creation = self.dateToMillis(creationDate)
								let metadata = self.metadataForRecord(item.record)
								
								metadataMap[paymentId] = CloudKitPaymentsDb.MetadataRow(
									unpaddedSize: Int64(item.unpaddedSize),
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
							
							try await self.cloudKitDb.payments.updateRows(
								downloadedPayments: paymentRows,
								downloadedPaymentsMetadata: paymentMetadataRows,
								updateMetadata: metadataMap
							)
							
							downloadProgress.payments_finishBatch(completed: items.count, oldest: oldest)
							
						}.value
						// </Task @MainActor>
						
						if (cursor == nil) {
							log.trace("downloadPayments(): moreInCloud = false")
							done = true
						} else {
							log.trace("downloadPayments(): moreInCloud = true")
							batch += 1
						}
						
					} // </while !done>
				} // </configuredWith>
				
				log.trace("downloadPayments(): enqueueMissingItems()...")
				
				// Step 4 of 4:
				//
				// There may be payments that we've added to the database since we started the download process.
				// So we enqueue these for upload now.
				
				try await Task { @MainActor in
					try await self.cloudKitDb.payments.enqueueMissingItems()
				}.value
				
				log.trace("downloadPayments(): finish: success")
				
				Prefs.shared.backupTransactions.markHasDownloadedPayments(walletId)
				self.consecutiveErrorCount = 0
				
				if let newState = await self.actor.didDownloadPayments() {
					self.handleNewState(newState)
				}
				
			} catch {
				
				log.error("downloadPayments(): error: \(error)")
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
	func uploadPayments(_ uploadProgress: SyncBackupManager_State_Uploading) {
		log.trace("uploadPayments()")
		
		let prepareUpload = {(
			batch: FetchPaymentsQueueBatchResult
		) -> UploadPaymentsOperationInfo in
			
			log.trace("uploadPayments(): prepareUpload()")
			
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
							recordType: payments_record_table_name,
							recordID: self.recordID(paymentId: paymentId)
						)
						
						record[payments_record_column_data] = ciphertext
						
						if let fileUrl = self.serializeAndEncryptMetadata(row.metadata) {
							record[payments_record_column_meta] = CKAsset(fileURL: fileUrl)
						}
						
						recordsToSave.append(record)
						reverseMap[record.recordID] = paymentId
						unpaddedMap[paymentId] = unpaddedSize
					}
					
				} else {
					
					// The payment has been deleted from the local database.
					// So we're going to delete it from the cloud database (if it exists there).
					
					let recordID = existingRecord?.recordID ?? self.recordID(paymentId: paymentId)
					
					recordIDsToDelete.append(recordID)
					reverseMap[recordID] = paymentId
				}
			}
			
			var opInfo = UploadPaymentsOperationInfo(
				batch: batch,
				recordsToSave: recordsToSave,
				recordIDsToDelete: recordIDsToDelete,
				reverseMap: reverseMap,
				unpaddedMap: unpaddedMap
			)
			
			// Edge-case: A rowid wasn't able to be converted to a WalletPaymentId.
			// This may happen when we add a new type to the database,
			// and we don't have code in place within `cloudKitDb.fetchQueueBatch()` to handle it.
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
					log.warning("UNHANDLED ROWID TYPE")
					opInfo.completedRowids.append(rowid)
				}
			}
			
			return opInfo
		
		} // </prepareUpload()>
		
		let performUpload = {(
			opInfo: UploadPaymentsOperationInfo
		) async throws -> UploadPaymentsOperationInfo in
			
			log.trace("uploadPayments(): performUpload()")
			log.trace("opInfo.recordsToSave.count = \(opInfo.recordsToSave.count)")
			log.trace("opInfo.recordIDsToDelete.count = \(opInfo.recordIDsToDelete.count)")
			
			if Task.isCancelled {
				throw CKError(.operationCancelled)
			}
			
			let container = CKContainer.default()
			
			let configuration = CKOperation.Configuration()
			configuration.allowsCellularAccess = Prefs.shared.backupTransactions.useCellular
			
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
					
					guard let paymentId = opInfo.reverseMap[recordID] else {
						continue
					}
					
					switch result {
					case .success(let record):
						nextOpInfo.savedRecords.append(record)
						
						for rowid in nextOpInfo.batch.rowidsMatching(paymentId) {
							nextOpInfo.completedRowids.append(rowid)
						}

					case .failure(let error):
						if let recordError = error as? CKError {
							
							nextOpInfo.partialFailures[paymentId] = recordError

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
					
					let results: [CKRecord.ID : Result<CKRecord, Error>] = try await database.records(
						for: recordIDsToFetch,
						desiredKeys: [] // fetch only basic CKRecord metadata
					)
					
					// results: [CKRecord.ID : Result<CKRecord, Error>]
					
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
			opInfo: UploadPaymentsOperationInfo
		) async throws -> Void in
			
			log.trace("uploadPayments(): updateDatabase()")
			
			var deleteFromQueue = [KotlinLong]()
			var deleteFromMetadata = [WalletPaymentId]()
			var updateMetadata = [WalletPaymentId: CloudKitPaymentsDb.MetadataRow]()
			
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
					
					updateMetadata[paymentRowId] = CloudKitPaymentsDb.MetadataRow(
						unpaddedSize: Int64(unpaddedSize),
						recordCreation: creation,
						recordBlob:  metadata.toKotlinByteArray()
					)
				}
			}
			
			// Handle partial failures
			let partialFailures: [String: CKError?] = opInfo.partialFailures.mapKeys { $0.id }
			
			for paymentRowIdStr in self.updateConsecutivePartialFailures(partialFailures) {
				for rowid in opInfo.batch.rowidsMatching(paymentRowIdStr) {
					deleteFromQueue.append(KotlinLong(longLong: rowid))
				}
			}
			
			log.debug("deleteFromQueue.count = \(deleteFromQueue.count)")
			log.debug("deleteFromMetadata.count = \(deleteFromMetadata.count)")
			log.debug("updateMetadata.count = \(updateMetadata.count)")
		
			try await Task { @MainActor [deleteFromQueue, deleteFromMetadata, updateMetadata] in
				try await self.cloudKitDb.payments.updateRows(
					deleteFromQueue: deleteFromQueue,
					deleteFromMetadata: deleteFromMetadata,
					updateMetadata: updateMetadata
				)
			}.value
			
		} // </updateDatabase>
		
		let finish = {(
			result: Result<Void, Error>
		) async -> Void in
			
			switch result {
			case .success:
				log.trace("uploadPayments(): finish(): success")
				
				self.consecutiveErrorCount = 0
				if let newState = await self.actor.didUploadItems() {
					self.handleNewState(newState)
				}
				
			case .failure(let error):
				log.trace("uploadPayments(): finish(): failure")
				self.handleError(error)
			}
			
		} // </finish()>
		
		Task {
			log.trace("uploadPayments(): starting task...")
			
			do {
				// Step 1 of 4:
				//
				// Check the `cloudkit_payments_queue` table, to see if there's anything we need to upload.
				// If the queue is non-empty, we will also receive:
				// - the corresponding payment information that needs to be uploaded
				// - the corresponding CKRecord metadata from previous upload for the payment (if present)
				
				let result: CloudKitPaymentsDb.FetchQueueBatchResult = try await Task { @MainActor in
					return try await self.cloudKitDb.payments.fetchQueueBatch(limit: 20)
				}.value
				
				let batch = result.convertToSwift()
				
				log.debug("uploadPayments(): batch.rowids.count = \(batch.rowids.count)")
				log.debug("uploadPayments(): batch.rowidMap.count = \(batch.rowidMap.count)")
				log.debug("uploadPayments(): batch.rowMap.count = \(batch.rowMap.count)")
				log.debug("uploadPayments(): batch.metadataMap.count = \(batch.metadataMap.count)")
				
				if batch.rowids.isEmpty {
					// There's nothing queued for upload, so we're done.
					
					// Bug Fix / Workaround:
					// The fetchQueueCountPublisher isn't firing reliably, and I'm not sure why...
					//
					// This can lead to the following infinite loop:
					// - fetchQueueCountPublisher fires and reports a non-zero count
					// - the actor.queueCount is updated
					// - the uploadTask is evetually triggered
					// - the item(s) are properly uploaded, and the rows are deleted from the queue
					// - fetchQueueCountPublisher does NOT properly fire
					// - the uploadTask is triggered again
					// - it finds zero rows to upload, but actor.queueCount remains unchanged
					// - the uploadTask is triggered again
					// - ...
					//
					if let newState = await self.actor.paymentsQueueCountChanged(0, wait: nil) {
						self.handleNewState(newState)
					}
					return await finish(.success)
				}
				
				// Step 2 of 4:
				//
				// Serialize and encrypt the payment information.
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
				
				uploadProgress.completePayments_inFlight(inFlightCount)
				return await finish(.success)
				
			} catch {
				return await finish(.failure(error))
			}
		} // </Task>
	}
	
	// ----------------------------------------
	// MARK: Record ID
	// ----------------------------------------
	
	private func recordID(paymentId: WalletPaymentId) -> CKRecord.ID {
		
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
		_ batch: FetchPaymentsQueueBatchResult
	) -> (Data, Int)? {
		
		var wrapper: CloudData? = nil
		
		if let incoming = row as? Lightning_kmpIncomingPayment {
			wrapper = CloudData(incoming: incoming)
			
		} else if let outgoing = row as? Lightning_kmpOutgoingPayment {
			wrapper = CloudData(outgoing: outgoing)
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
			// And then we and a random amount of padding to reach our target range.
			
			let makeRange = { (stats: CloudKitPaymentsDb.MetadataStats) -> (Int, Int) in
				
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
		
		guard let row = WalletPaymentMetadataRow.companion.serialize(metadata: metadata) else {
			return nil
		}
		
		let cleartext = CloudAsset(row: row).cborSerialize().toSwiftData()
		
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
	
	private func decryptAndDeserializePayment(
		_ record: CKRecord
	) -> (Lightning_kmpWalletPayment?, WalletPaymentMetadataRow?, Int) {
		
		log.debug("Received record:")
		log.debug(" - recordID: \(record.recordID)")
		log.debug(" - creationDate: \(record.creationDate ?? Date.distantPast)")
		
		var unpaddedSize: Int = 0
		var payment: Lightning_kmpWalletPayment? = nil
		var metadata: WalletPaymentMetadataRow? = nil
		
		if let ciphertext = record[payments_record_column_data] as? Data {
		//	log.debug(" - data.count: \(ciphertext.count)")
			
			var cleartext: Data? = nil
			do {
				let box = try ChaChaPoly.SealedBox(combined: ciphertext)
				cleartext = try ChaChaPoly.open(box, using: self.cloudKey)
				
			//	#if DEBUG
			//	log.debug(" - raw payment: \(cleartext.toHex())")
			//	#endif
			} catch {
				log.error("Error decrypting record.data: skipping \(record.recordID)")
			}
			
			var wrapper: CloudData? = nil
			if let cleartext {
				do {
					let cleartext_kotlin = cleartext.toKotlinByteArray()
					wrapper = try CloudData.companion.cborDeserialize(blob: cleartext_kotlin)
					
				//	#if DEBUG
				//	let jsonData = wrapper.jsonSerialize().toSwiftData()
				//	let jsonStr = String(data: jsonData, encoding: .utf8)
				//	log.debug(" - raw JSON:\n\(jsonStr ?? "<nil>")")
				//	#endif

					let paddedSize = cleartext.count
					let paddingSize = Int(wrapper!.padding?.size ?? 0)
					unpaddedSize = paddedSize - paddingSize
				} catch {
					log.error("Error deserializing record.data: skipping \(record.recordID)")
				}
			}
			
			if let wrapper {
				do {
					payment = try wrapper.unwrap()
				} catch {
					log.error("Error unwrapping record.data: skipping \(record.recordID)")
				}
			}
		}
		
		if let asset = record[payments_record_column_meta] as? CKAsset {
			
			var ciphertext: Data? = nil
			if let fileURL = asset.fileURL {
				do {
					ciphertext = try Data(contentsOf: fileURL)
				} catch {
					log.error("Error reading record.asset: \(String(describing: error))")
				}
			}
			
			var cleartext: Data? = nil
			if let ciphertext {
				do {
					let box = try ChaChaPoly.SealedBox(combined: ciphertext)
					cleartext = try ChaChaPoly.open(box, using: self.cloudKey)
					
				//	#if DEBUG
				//	log.debug(" - raw metadata: \(cleartext.toHex())")
				//	#endif
				} catch {
					log.error("Error decrypting record.asset: \(String(describing: error))")
				}
			}
			
			var cloudAsset: CloudAsset? = nil
			if let cleartext {
				do {
					let cleartext_kotlin = cleartext.toKotlinByteArray()
					cloudAsset = try CloudAsset.companion.cborDeserialize(blob: cleartext_kotlin)
				} catch {
					log.error("Error deserializing record.asset: \(String(describing: error))")
				}
			}
			
			if let cloudAsset {
				do {
					metadata = try cloudAsset.unwrap()
				} catch {
					log.error("Error unwrapping record.asset: \(String(describing: error))")
				}
			}
		}
		
		return (payment, metadata, unpaddedSize)
	}
	
	// ----------------------------------------
	// MARK: Debugging
	// ----------------------------------------
	#if DEBUG
	
	private func listAllPayments() {
		log.trace("listAllPayments()")
		
		let query = CKQuery(
			recordType: payments_record_table_name,
			predicate: NSPredicate(format: "TRUEPREDICATE")
		)
		query.sortDescriptors = [
			NSSortDescriptor(key: "creationDate", ascending: false)
		]
		
		let operation = CKQueryOperation(query: query)
		operation.zoneID = self.recordZoneID()
		
		recursiveListPaymentsBatch(operation: operation)
	}
	
	private func recursiveListPaymentsBatch(operation: CKQueryOperation) -> Void {
		log.trace("recursiveListPaymentsBatch()")
		
		operation.recordMatchedBlock = {(recordID: CKRecord.ID, result: Result<CKRecord, Error>) in
			
			if let record = try? result.get() {
				
				log.debug("Received record:")
				log.debug(" - recordID: \(record.recordID)")
				log.debug(" - creationDate: \(record.creationDate ?? Date.distantPast)")
				
				if let data = record[payments_record_column_data] as? Data {
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
					log.debug("Fetch batch complete. Continuing with cursor...")
					self.recursiveListPaymentsBatch(operation: CKQueryOperation(cursor: cursor))
					
				} else {
					log.debug("Fetch batch complete.")
				}
				
			case .failure(let error):
				log.debug("Error fetching batch: \(String(describing: error))")
			}
		}
		
		let configuration = CKOperation.Configuration()
		configuration.allowsCellularAccess = true
		
		operation.configuration = configuration
		
		CKContainer.default().privateCloudDatabase.add(operation)
	}
	
	#endif
}
