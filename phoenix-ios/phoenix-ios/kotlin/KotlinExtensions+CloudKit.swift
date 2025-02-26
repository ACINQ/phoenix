import Foundation
import PhoenixShared

struct FetchContactsQueueBatchResult {
	let rowids: [Int64]
	let rowidMap: [Int64: Lightning_kmpUUID]
	let rowMap: [Lightning_kmpUUID : ContactInfo]
	let metadataMap: [Lightning_kmpUUID : KotlinByteArray]
	
	func uniqueContactIds() -> Set<Lightning_kmpUUID> {
		return Set<Lightning_kmpUUID>(rowidMap.values)
	}
	
	func rowidsMatching(_ query: Lightning_kmpUUID) -> [Int64] {
		var results = [Int64]()
		for (rowid, contactId) in rowidMap {
			if contactId == query {
				results.append(rowid)
			}
		}
		return results
	}
	
	func rowidsMatching(_ query: String) -> [Int64] {
		var results = [Int64]()
		for (rowid, contactId) in rowidMap {
			if contactId.id == query {
				results.append(rowid)
			}
		}
		return results
	}
	
	static func empty() -> FetchContactsQueueBatchResult {
		
		return FetchContactsQueueBatchResult(
			rowids: [],
			rowidMap: [:],
			rowMap: [:],
			metadataMap: [:]
		)
	}
}

struct FetchPaymentsQueueBatchResult {
	let rowids: [Int64]
	let rowidMap: [Int64: Lightning_kmpUUID]
	let rowMap: [Lightning_kmpUUID : WalletPaymentInfo]
	let metadataMap: [Lightning_kmpUUID : KotlinByteArray]
	
	func uniquePaymentIds() -> Set<Lightning_kmpUUID> {
		return Set<Lightning_kmpUUID>(rowidMap.values)
	}
	
	func rowidsMatching(_ query: Lightning_kmpUUID) -> [Int64] {
		var results = [Int64]()
		for (rowid, paymentRowId) in rowidMap {
			if paymentRowId == query {
				results.append(rowid)
			}
		}
		return results
	}
	
	func rowidsMatching(_ query: String) -> [Int64] {
		var results = [Int64]()
		for (rowid, paymentRowId) in rowidMap {
			if paymentRowId.id == query {
				results.append(rowid)
			}
		}
		return results
	}
	
	static func empty() -> FetchPaymentsQueueBatchResult {
		
		return FetchPaymentsQueueBatchResult(
			rowids: [],
			rowidMap: [:],
			rowMap: [:],
			metadataMap: [:]
		)
	}
}

extension CloudKitContactsDb.FetchQueueBatchResult {
	
	func convertToSwift() -> FetchContactsQueueBatchResult {
		
		// We are experiencing crashes like this:
		//
		// for (rowid, paymentRowId) in batch.rowidMap {
		//      ^^^^^
		// Crash: Could not cast value of type '__NSCFNumber' to 'PhoenixSharedLong'.
		//
		// This appears to be some kind of bug in Kotlin.
		// So we're going to make a clean migration.
		// And we need to do so without swift-style enumeration in order to avoid crashing.
		
		var _rowids = [Int64]()
		var _rowidMap = [Int64: Lightning_kmpUUID]()
		
		for i in 0 ..< self.rowids.count { // cannot enumerate self.rowidMap
			
			let value_kotlin = rowids[i]
			let value_swift = value_kotlin.int64Value
			
			_rowids.append(value_swift)
			if let contactId = self.rowidMap[value_kotlin] {
				_rowidMap[value_swift] = contactId
			}
		}
		
		return FetchContactsQueueBatchResult(
			rowids: _rowids,
			rowidMap: _rowidMap,
			rowMap: self.rowMap,
			metadataMap: self.metadataMap
		)
	}
}

extension CloudKitPaymentsDb.FetchQueueBatchResult {
	
	func convertToSwift() -> FetchPaymentsQueueBatchResult {
		
		// We are experiencing crashes like this:
		//
		// for (rowid, paymentRowId) in batch.rowidMap {
		//      ^^^^^
		// Crash: Could not cast value of type '__NSCFNumber' to 'PhoenixSharedLong'.
		//
		// This appears to be some kind of bug in Kotlin.
		// So we're going to make a clean migration.
		// And we need to do so without swift-style enumeration in order to avoid crashing.
		
		var _rowids = [Int64]()
		var _rowidMap = [Int64: Lightning_kmpUUID]()
		
		for i in 0 ..< self.rowids.count { // cannot enumerate self.rowidMap
			
			let value_kotlin = rowids[i]
			let value_swift = value_kotlin.int64Value
			
			_rowids.append(value_swift)
			if let paymentRowId = self.rowidMap[value_kotlin] {
				_rowidMap[value_swift] = paymentRowId
			}
		}
		
		return FetchPaymentsQueueBatchResult(
			rowids: _rowids,
			rowidMap: _rowidMap,
			rowMap: self.rowMap,
			metadataMap: self.metadataMap
		)
	}
}
