import Foundation

fileprivate let filename = "Prefs"
#if DEBUG
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = GroupPrefsKey

extension GroupPrefs {
	
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
		
		return GroupPrefs_Wallet.valueDescription(prefix, value) ??
				 GroupPrefs_Global.valueDescription(prefix, value) ??
				 "<?>"
	}
	#endif
}
