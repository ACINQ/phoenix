import Foundation

fileprivate let filename = "Keychain"
#if DEBUG
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = KeychainKey

extension Keychain {
	
	#if DEBUG
	static func printKeysAndValues(_ walletId: WalletIdentifier) {
		printKeysAndValues(walletId.standardKeyId)
	}
	
	static func printKeysAndValues(_ id: String?) {
		
		var output: String = ""
		output += "# \(id ?? "unknown"):\n"
		
		for key in Key.allCases {
			
			let account: String = if let id { key.account(id) } else { key.deprecatedValue }
			let accessGroup: String = key.accessGroup.value
			
			do {
				let value: Data? = try SystemKeychain.readItem(
					account     : account,
					accessGroup : accessGroup
				)
				if let value {
					let desc = valueDescription(key.prefix, value)
					
					output += " - \(key): \(desc)\n"
				}
			} catch {
				log.debug(
					"""
					keychain.read(acct: \(key.debugName), grp: \(key.accessGroup.debugName)): \
					error: \(error)
					""")
			}
		}
		
		log.debug("\(output)")
	}
	
	static func valueDescription(_ prefix: String, _ value: Data) -> String {
		
		let pinDescription = {() -> String in
			if let str = String(data: value, encoding: .utf8), str.isValidPIN {
				"<String: \(str.count) digits>"
			} else {
				"<String: unknown>"
			}
		}
		
		let invalidPinDescription = {() -> String in
			if let ip = try? JSONDecoder().decode(InvalidPin.self, from: value) {
				"<InvalidPin: \(ip.count), \(ip.timestamp)>"
			} else {
				"<InvalidPin: unknown>"
			}
		}
		
		return switch prefix {
		case Key.lockingKey.prefix:
			"<Key: \(value.count) bytes>"
			
		case Key.softBiometrics.prefix:
			"<Bool: true>" // when false, key doesn't exist in keychain
			
		case Key.passcodeFallback.prefix:
			"<Bool: true>" // when false, key doesn't exist in keychain
			
		case Key.lockPin.prefix:
			pinDescription()
			
		case Key.invalidLockPin.prefix:
			invalidPinDescription()
			
		case Key.spendingPin.prefix:
			pinDescription()
			
		case Key.invalidSpendingPin.prefix:
			invalidPinDescription()
			
		case Key.bip353Address.prefix:
			if let _ = String(data: value, encoding: .utf8) {
				"<String: hidden>"
			} else {
				"<String: unknown>"
			}
			
		default:
			"<?>"
		}
	}
	#endif
}
