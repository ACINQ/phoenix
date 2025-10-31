import Foundation
import Combine

fileprivate let filename = "Prefs+Global"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = PrefsKey

/// Standard app preferences, stored in the iOS UserDefaults system.
///
/// This set is shared between wallets (not pertaining to any particular wallet).
///
/// - Note:
/// The values here are NOT shared with other extensions bundled in the app, such as the
/// notification-service-extension. For preferences shared with extensions, see `GroupPrefs`.
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
			defaults.set(newValue.jsonEncode(), forKey: Key.theme.value(id))
			runOnMainThread {
				self.themePublisher.send(newValue)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Debugging
	// --------------------------------------------------
	
	#if DEBUG
	static func valueDescription(_ prefix: String, _ value: Any) -> String? {
		
		switch prefix {
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
