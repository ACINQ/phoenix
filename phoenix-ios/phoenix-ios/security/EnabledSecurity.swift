import Foundation

/// Represents the security options enabled by the user.
///
struct EnabledSecurity: OptionSet, CustomStringConvertible {
	
	let rawValue: Int

	static let biometrics       = EnabledSecurity(rawValue: 1 << 0)
	static let advancedSecurity = EnabledSecurity(rawValue: 1 << 1)

	static let none: EnabledSecurity = []
	
	var description: String {
		var str = "["
		if contains(.biometrics) {
			str.append("biometrics")
		}
		if contains(.advancedSecurity) {
			if str.count > 1 {
				str.append(", ")
			}
			str.append("advancedSecurity")
		}
		
		return str.appending("]")
	}
}
