import Foundation

extension Prefs {
	
	#if DEBUG
	static func printString(_ value: Any) -> String {
		let desc = (value as? String) ?? "unknown"
		return "<String: \(desc)>"
	}
	
	static func printBool(_ value: Any) -> String {
		let desc = (value as? NSNumber)?.boolValue.description ?? "unknown"
		return "<Bool: \(desc)>"
	}
	
	static func printInt(_ value: Any) -> String {
		let desc = (value as? NSNumber)?.intValue.description ?? "unknown"
		return "<Int: \(desc)>"
	}
	#endif
}
