import Foundation

/// Represents the security options enabled by the user.
///
struct EnabledSecurity: OptionSet, CustomStringConvertible {
	
	let rawValue: Int

	static let biometrics       = EnabledSecurity(rawValue: 1 << 0)
	static let passcodeFallback = EnabledSecurity(rawValue: 1 << 1)
	static let advancedSecurity = EnabledSecurity(rawValue: 1 << 2)
	static let lockPin          = EnabledSecurity(rawValue: 1 << 3)
	static let spendingPin      = EnabledSecurity(rawValue: 1 << 4)

	static let none: EnabledSecurity = []
	
	func hasAppLock() -> Bool {
		return contains(.biometrics) || contains(.advancedSecurity) || contains(.lockPin)
	}
	
	func hasSpendingPin() -> Bool {
		return contains(.spendingPin)
	}
	
	var description: String {
		var items = [String]()
		items.reserveCapacity(3)
		if contains(.biometrics) {
			items.append("biometrics")
		}
		if contains(.passcodeFallback) {
			items.append("passcodeFallback")
		}
		if contains(.advancedSecurity) {
			items.append("advancedSecurity")
		}
		if contains(.lockPin) {
			items.append("lockPin")
		}
		if contains(.spendingPin) {
			items.append("spendingPin")
		}
		return "[\(items.joined(separator: ","))]"
	}
}
