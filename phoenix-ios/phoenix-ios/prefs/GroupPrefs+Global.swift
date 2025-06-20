import Foundation
import Combine

fileprivate let filename = "GroupPrefs+Global"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum Key: CaseIterable {
	case badgeCount
	
	/// We used to declare, `enum Key: String`, but discovered that it's a bit of a footgun.
	/// It's just too easy to type `Key.name.rawValue`, as we've done so many times before.
	/// So we switched to a variable name that puts the value in the proper context.
	///
	var prefix: String {
		switch self {
			case .badgeCount: return "badgeCount"
		}
	}
	
	var deprecatedValue: String {
		return prefix
	}
	
	func value(_ suffix: String) -> String {
		return "\(self.prefix)-\(suffix)"
	}
}

/// Group preferences, stored in the iOS UserDefaults system.
///
/// Note that the values here are SHARED with other extensions bundled in the app,
/// such as the notification-service-extension.
///
/// This set is shared between wallets (not pertaining to any particular wallet).
///
class GroupPrefs_Global {
	
	private static var defaults: UserDefaults {
		return GroupPrefs.defaults
	}
	
	static let shared = GroupPrefs_Global()
	
	private let id: String
	private let defaults: UserDefaults
	
	private init() {
		self.id = PREFS_GLOBAL_ID
		self.defaults = Self.defaults
	}
	
	// --------------------------------------------------
	// MARK: Push Notifications
	// --------------------------------------------------
	
	lazy private(set) var badgeCountPublisher = {
		CurrentValueSubject<Int, Never>(self.badgeCount)
	}()
	
	var badgeCount: Int {
		get {
			return defaults.integer(forKey: Key.badgeCount.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.badgeCount.value(id))
			runOnMainThread {
				self.badgeCountPublisher.send(newValue)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Migration
	// --------------------------------------------------
	
	static func performMigration_toBuild92() {
		log.trace(#function)
		
		let d = self.defaults
		let newId = PREFS_GLOBAL_ID
		
		for key in Key.allCases {
			let oldKey = key.deprecatedValue
			if let value = d.object(forKey: oldKey) {
				
				let newKey = key.value(newId)
				if d.object(forKey: newKey) == nil {
					log.debug("move: \(oldKey) > \(newKey)")
					d.set(value, forKey: newKey)
				} else {
					log.debug("delete: \(oldKey)")
				}
				
				d.removeObject(forKey: oldKey)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Debugging
	// --------------------------------------------------
	
	#if DEBUG
	static func isKnownKey(_ key: String) -> Bool {
		
		for knownKey in Key.allCases {
			if key.hasPrefix(knownKey.prefix) {
				return true
			}
		}
		
		return false
	}
	
	static func valueDescription(_ key: String, _ value: Any) -> String? {
		
		let printInt = {() -> String in
			let desc = (value as? NSNumber)?.intValue.description ?? "unknown"
			return "<Int: \(desc)>"
		}
		
		switch key {
		case Key.badgeCount.prefix:
			return printInt()
			
		default:
			return nil
		}
	}
	#endif
}
