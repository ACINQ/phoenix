import Foundation
import Combine

fileprivate let filename = "Prefs"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = PrefsKey

extension Prefs {
	
	// --------------------------------------------------
	// MARK: Debugging
	// --------------------------------------------------
	
	#if DEBUG
	static func printAllKeyValues() {
		
		let output = self.defaults.dump(
			isKnownKey: self.isKnownKey,
			valueDescription: self.valueDescription
		)
		log.debug("\(output)")
	}
	
	private static func isKnownKey(_ key: String) -> Bool {
		
		for knownKey in Key.allCases {
			if key.hasPrefix(knownKey.prefix) {
				return true
			}
		}
		
		return false
	}
	
	private static func valueDescription(_ prefix: String, _ value: Any) -> String {
		
		return Prefs_Wallet.valueDescription(prefix, value) ??
		       Prefs_BackupSeed.valueDescription(prefix, value) ??
				 Prefs_BackupTransactions.valueDescription(prefix, value) ??
				 Prefs_Global.valueDescription(prefix, value) ??
				 "<?>"
	}
	#endif
}
