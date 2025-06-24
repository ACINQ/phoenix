import Foundation
import Combine

fileprivate let filename = "GroupPrefs+Global"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = GroupPrefsKey

/// Group preferences, stored in the iOS UserDefaults system.
///
/// This set is shared between wallets (not pertaining to any particular wallet).
///
/// - Note:
/// The values here are SHARED with other extensions bundled in the app,
/// such as the notification-service-extension.
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
	// MARK: Debugging
	// --------------------------------------------------
	
	#if DEBUG
	static func valueDescription(_ prefix: String, _ value: Any) -> String? {
		
		switch prefix {
		case Key.badgeCount.prefix:
			return printInt(value)
			
		default:
			return nil
		}
	}
	#endif
}
