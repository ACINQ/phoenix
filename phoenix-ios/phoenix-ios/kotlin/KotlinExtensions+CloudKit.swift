import Foundation
import PhoenixShared


struct FetchQueueBatchResult {
	let rowids: [Int64]
	let rowidMap: [Int64: WalletPaymentId]
	let rowMap: [WalletPaymentId : WalletPaymentInfo]
	let metadataMap: [WalletPaymentId : KotlinByteArray]
	let incomingStats: CloudKitDb.MetadataStats
	let outgoingStats: CloudKitDb.MetadataStats
	
	func uniquePaymentIds() -> Set<WalletPaymentId> {
		return Set<WalletPaymentId>(rowidMap.values)
	}
	
	func rowidsMatching(_ query: WalletPaymentId) -> [Int64] {
		var results = [Int64]()
		for (rowid, paymentRowId) in rowidMap {
			if paymentRowId == query {
				results.append(rowid)
			}
		}
		return results
	}
	
	static func empty() -> FetchQueueBatchResult {
		
		return FetchQueueBatchResult(
			rowids: [],
			rowidMap: [:],
			rowMap: [:],
			metadataMap: [:],
			incomingStats: CloudKitDb.MetadataStats(),
			outgoingStats: CloudKitDb.MetadataStats()
		)
	}
}

extension CloudKitDb.FetchQueueBatchResult {
	
	func convertToSwift() -> FetchQueueBatchResult {
		
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
		var _rowidMap = [Int64: WalletPaymentId]()
		
		for i in 0 ..< self.rowids.count { // cannot enumerate self.rowidMap
			
			let value_kotlin = rowids[i]
			let value_swift = value_kotlin.int64Value
			
			_rowids.append(value_swift)
			if let paymentRowId = self.rowidMap[value_kotlin] {
				_rowidMap[value_swift] = paymentRowId
			}
		}
		
		return FetchQueueBatchResult(
			rowids: _rowids,
			rowidMap: _rowidMap,
			rowMap: self.rowMap,
			metadataMap: self.metadataMap,
			incomingStats: self.incomingStats,
			outgoingStats: self.outgoingStats
		)
	}
}
