import Foundation
import PhoenixShared
import DnaCommunicator

extension BoltCardInfo {
	
	var sanitizedName: String {
		let result = name.trimmingCharacters(in: .whitespacesAndNewlines)
		return result.isEmpty ? BoltCardInfo.defaultName : result
	}
	
	var isActive: Bool {
		return !isFrozen
	}
	
	var createdAtDate: Date {
		return createdAt.toDate()
	}
	
	func withUpdatedLastKnownCounter(_ counter: UInt32) -> BoltCardInfo {
		return doCopy(
			id               : self.id,
			name             : self.name,
			keys             : self.keys,
			uid              : self.uid,
			lastKnownCounter : counter,
			isFrozen         : self.isFrozen,
			isArchived       : self.isArchived,
			isReset          : self.isReset,
			isForeign        : self.isForeign,
			dailyLimit       : self.dailyLimit,
			monthlyLimit     : self.monthlyLimit,
			createdAt        : self.createdAt
		)
	}
	
	func archivedCopy() -> BoltCardInfo {
		return doCopy(
			id               : self.id,
			name             : self.name,
			keys             : self.keys,
			uid              : self.uid,
			lastKnownCounter : self.lastKnownCounter,
			isFrozen         : true, // this should also be set (just to be careful)
			isArchived       : true,
			isReset          : self.isReset,
			isForeign        : self.isForeign,
			dailyLimit       : self.dailyLimit,
			monthlyLimit     : self.monthlyLimit,
			createdAt        : self.createdAt
		)
	}
	
	func resetCopy() -> BoltCardInfo {
		return doCopy(
			id               : self.id,
			name             : self.name,
			keys             : self.keys,
			uid              : self.uid,
			lastKnownCounter : self.lastKnownCounter,
			isFrozen         : true, // this should also be set (just to be careful)
			isArchived       : true, // this must be set
			isReset          : true,
			isForeign        : self.isForeign,
			dailyLimit       : self.dailyLimit,
			monthlyLimit     : self.monthlyLimit,
			createdAt        : self.createdAt
		)
	}
	
	static var defaultName: String {
		return String(
			localized: "My Bolt Card",
			comment: "Default name for a bolt card when creating a new one"
		)
	}
}

extension BoltCardKeySet {
	
	var key0_data: Data {
		return key0.toSwiftData()
	}
	
	var key0_bytes: [UInt8] {
		return key0_data.toByteArray()
	}
	
	var piccDataKey_data: Data {
		return piccDataKey.toSwiftData()
	}
	
	var piccDataKey_bytes: [UInt8] {
		return piccDataKey_data.toByteArray()
	}
	
	var cmacKey_data: Data {
		return cmacKey.toSwiftData()
	}
	
	var cmacKey_bytes: [UInt8] {
		return cmacKey_data.toByteArray()
	}
}
