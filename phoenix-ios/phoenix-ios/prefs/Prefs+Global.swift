import Foundation
import Combine

fileprivate let filename = "Prefs+Shared"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum Key: CaseIterable {
	case theme
	
	/// We used to declare, `enum Key: String`, but discovered that it's a bit of a footgun.
	/// It's just too easy to type `Key.name.rawValue`, as we've done so many times before.
	/// So we switched to a variable name that puts the value in the proper context.
	///
	var prefix: String {
		switch self {
			case .theme : return "theme"
		}
	}
	
	var deprecatedValue: String {
		return prefix
	}
	
	func value(_ suffix: String) -> String {
		return "\(self.prefix)-\(suffix)"
	}
}

/// Standard app preferences, stored in the iOS UserDefaults system.
///
/// Note that the values here are NOT shared with other extensions bundled in the app,
/// such as the notification-service-extension. For preferences shared with extensions, see GroupPrefs.
///
/// This set is shared between wallets (not pertaining to any particular wallet).
///
class Prefs_Global {
	
	private static var defaults: UserDefaults {
		return Prefs.defaults
	}
	
	static let shared = Prefs_Global()
	
	private let id: String
	private let defaults: UserDefaults
	
	private init() {
		self.id = PREFS_GLOBAL_ID
		self.defaults = Self.defaults
	}
	
	// --------------------------------------------------
	// MARK: User Options
	// --------------------------------------------------
	
	lazy private(set) var themePublisher = {
		CurrentValueSubject<Theme, Never>(self.theme)
	}()

	var theme: Theme {
		get {
			defaults.data(forKey: Key.theme.value(id))?.jsonDecode() ?? Theme.system
		}
		set {
			defaults.set(newValue, forKey: Key.theme.value(id))
			runOnMainThread {
				self.themePublisher.send(newValue)
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
		
		switch key {
		case Key.theme.prefix:
			let desc = if let data = value as? Data, let theme: Theme = data.jsonDecode() {
				theme.rawValue
			} else { "unknown" }
			
			return "<Theme: \(desc)>"
			
		default:
			return nil
		}
	}
	#endif
}
